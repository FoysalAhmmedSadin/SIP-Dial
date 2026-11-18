package com.sipdial.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.data.AppDatabase
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.data.models.FavoriteContact
import com.sipdial.app.data.models.SavedContact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DatabaseMigrationAndDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun callHistoryRecordingPathAndClearWork() = runBlocking {
        val dao = db.callHistoryDao()
        dao.insertCallRecord(
            CallRecord(
                remoteAddress = "1001@pbx.test",
                displayName = "Support",
                isIncoming = true,
                isAnswered = true,
                duration = 92,
                timestamp = 1_700_000_000_000L,
                recordingPath = "/tmp/call_recording.wav",
                accountUser = "1000"
            )
        )
        dao.insertCallRecord(
            CallRecord(
                remoteAddress = "1002@pbx.test",
                displayName = "Sales",
                isIncoming = false,
                isAnswered = false,
                duration = 0,
                timestamp = 1_700_000_010_000L,
                recordingPath = "",
                accountUser = "1000"
            )
        )

        val recorded = dao.getRecordedCalls().first()
        assertEquals(1, recorded.size)
        assertEquals("Support", recorded.first().displayName)

        dao.clearRecordingPath(recorded.first().id)
        assertEquals(0, dao.getRecordedCalls().first().size)
        assertEquals(2, dao.getAllOnce().size)
    }

    @Test
    fun favoritesDaoCrudAndRouteUpdate() = runBlocking {
        val dao = db.favoritesDao()
        val norm = FavoriteContact.normalize("+1 (555) 234-5678")
        val id = dao.insert(
            FavoriteContact(
                name = "Bob",
                number = "+1 (555) 234-5678",
                normalizedNumber = norm,
                preferredRoute = FavoriteContact.ROUTE_ASK
            )
        )
        assertEquals(1, dao.countByNormalized(norm))

        dao.updatePreferredRoute(id, FavoriteContact.ROUTE_SIP)
        assertEquals(FavoriteContact.ROUTE_SIP, dao.getAllOnce().first().preferredRoute)

        dao.deleteByNormalized(norm)
        assertEquals(0, dao.countByNormalized(norm))
    }

    @Test
    fun savedContactDaoDeduplicatesByCategoryAndNumber() = runBlocking {
        val dao = db.savedContactDao()
        dao.insert(
            SavedContact(
                category = SavedContact.CATEGORY_OFFICE,
                name = "Reception",
                number = "100"
            )
        )
        assertEquals(1, dao.countSame(SavedContact.CATEGORY_OFFICE, "100"))
        assertEquals(0, dao.countSame(SavedContact.CATEGORY_CLIENT, "100"))
    }

    @Test
    fun migration11To12PreservesExistingRowsAndAddsAccountColumns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_11_12_test.db"
        context.deleteDatabase(dbName)

        // Create version 11 SQLite schema directly before opening Room v12
        val sqliteHelper = object : android.database.sqlite.SQLiteOpenHelper(context, dbName, null, 11) {
            override fun onCreate(sqldb: android.database.sqlite.SQLiteDatabase) {
                sqldb.execSQL(
                    "CREATE TABLE IF NOT EXISTS `call_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`remoteAddress` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                        "`isIncoming` INTEGER NOT NULL, `isAnswered` INTEGER NOT NULL, " +
                        "`duration` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
                        "`recordingPath` TEXT NOT NULL, `accountUser` TEXT NOT NULL)"
                )
                sqldb.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_contacts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`category` TEXT NOT NULL, `name` TEXT NOT NULL, `number` TEXT NOT NULL, " +
                        "`company` TEXT NOT NULL, `email` TEXT NOT NULL, `address` TEXT NOT NULL, " +
                        "`birthday` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                        "`hasWhatsapp` INTEGER NOT NULL, `hasTelegram` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                sqldb.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_contacts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `number` TEXT NOT NULL, " +
                        "`normalizedNumber` TEXT NOT NULL, `photoUri` TEXT, " +
                        "`preferredRoute` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL)"
                )
                sqldb.execSQL(
                    "INSERT INTO `call_history` " +
                        "(`remoteAddress`, `displayName`, `isIncoming`, `isAnswered`, `duration`, `timestamp`, `recordingPath`, `accountUser`) " +
                        "VALUES ('+15551112222', 'Legacy Caller', 1, 1, 65, 1700000000000, '', '1001')"
                )
            }

            override fun onUpgrade(sqldb: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        sqliteHelper.writableDatabase.close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_11_12)
            .allowMainThreadQueries()
            .build()
        try {
            val rows = migrated.callHistoryDao().getAllOnce()
            assertEquals(1, rows.size)
            assertEquals("Legacy Caller", rows.first().displayName)
            assertEquals("1001", rows.first().accountUser)
            assertEquals("", rows.first().accountId)
            assertEquals("", rows.first().accountName)
            assertEquals("1001", rows.first().resolvedAccountName())

            migrated.callHistoryDao().insertCallRecord(
                CallRecord(
                    remoteAddress = "+15559998888",
                    displayName = "Office Contact",
                    isIncoming = false,
                    isAnswered = true,
                    duration = 30,
                    timestamp = 1700000050000L,
                    accountUser = "2002",
                    accountId = "acc-office",
                    accountName = "Office PBX"
                )
            )
            val updated = migrated.callHistoryDao().getAllOnce()
            assertEquals(2, updated.size)
            assertEquals("Office PBX", updated.first { it.displayName == "Office Contact" }.resolvedAccountName())
        } finally {
            migrated.close()
            context.deleteDatabase(dbName)
        }
    }
}
