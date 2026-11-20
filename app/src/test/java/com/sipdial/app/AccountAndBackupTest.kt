package com.sipdial.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.data.AccountRepository
import com.sipdial.app.data.AppDatabase
import com.sipdial.app.data.BackupManager
import com.sipdial.app.data.models.CallRecord
import com.sipdial.app.data.models.FavoriteContact
import com.sipdial.app.data.models.SavedContact
import com.sipdial.app.data.models.SipAccountData
import com.sipdial.app.sip.SipRegistrationState
import com.sipdial.app.ui.screens.BackupRestoreScreen
import com.sipdial.app.ui.screens.SettingsScreen
import com.sipdial.app.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountAndBackupTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var repo: AccountRepository
    private lateinit var db: AppDatabase

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        repo = AccountRepository(context)
        repo.clearAccount()
        db = AppDatabase.getInstance(context)
        db.callHistoryDao().deleteAllCallRecords()
        db.favoritesDao().deleteAll()
        db.savedContactDao().getAllOnce().forEach { db.savedContactDao().delete(it) }
    }

    @Test
    fun accountRepositorySavesSelectsEnablesAndDeletesAccounts() = runBlocking {
        val acc1 = repo.saveAccount(
            SipAccountData(
                name = "Work Line",
                username = "1001",
                authUsername = "auth_1001",
                password = "s3cr3tPassword!",
                domain = "pbx.example.com",
                port = 5061,
                transport = "TLS",
                registrationExpiry = 120,
                enabled = true
            )
        )
        val acc2 = repo.saveAccount(
            SipAccountData(
                name = "Home Line",
                username = "2002",
                password = "homePassword",
                domain = "sip.home.org",
                enabled = true
            )
        )

        val list = repo.getAccounts().first()
        assertEquals(2, list.size)
        assertEquals("auth_1001", list.first { it.id == acc1.id }.authUsername)
        assertEquals(120, list.first { it.id == acc1.id }.registrationExpiry)

        repo.selectAccount(acc1.id)
        assertEquals(acc1.id, repo.getAccount().first()?.id)

        repo.setEnabled(acc2.id, false)
        assertEquals(false, repo.getAccounts().first().first { it.id == acc2.id }.enabled)

        repo.deleteAccount(acc1.id)
        val remaining = repo.getAccounts().first()
        assertEquals(1, remaining.size)
        assertEquals(acc2.id, remaining.first().id)
    }

    @Test
    fun backupExportAndRestoreRoundTripPreservesAllData() = runBlocking {
        repo.saveAccount(
            SipAccountData(
                name = "Office",
                username = "3003",
                authUsername = "office_auth",
                password = "strongBackupSecret",
                domain = "voip.company.net",
                port = 5060,
                transport = "TCP",
                registrationExpiry = 90
            )
        )
        db.callHistoryDao().insertCallRecord(
            CallRecord(
                remoteAddress = "+15551234567",
                displayName = "Alice",
                isIncoming = false,
                isAnswered = true,
                duration = 45,
                timestamp = 1_700_000_000_000L,
                accountUser = "3003"
            )
        )
        db.savedContactDao().insert(
            SavedContact(
                category = SavedContact.CATEGORY_CLIENT,
                name = "Alice Client",
                number = "+15551234567",
                company = "Acme Corp"
            )
        )
        db.favoritesDao().insert(
            FavoriteContact(
                name = "Alice",
                number = "+15551234567",
                normalizedNumber = FavoriteContact.normalize("+15551234567"),
                preferredRoute = FavoriteContact.ROUTE_SIP
            )
        )

        val out = ByteArrayOutputStream()
        val exported = BackupManager.export(context, "correct-pass-123".toCharArray(), out)
        assertEquals(1, exported.accounts)
        assertEquals(1, exported.calls)
        assertEquals(1, exported.contacts)
        assertEquals(1, exported.favorites)

        val bytes = out.toByteArray()
        assertTrue(bytes.size > 40)

        // Wipe local stores completely
        repo.clearAccount()
        db.callHistoryDao().deleteAllCallRecords()
        db.favoritesDao().deleteAll()
        db.savedContactDao().getAllOnce().forEach { db.savedContactDao().delete(it) }

        val restored = BackupManager.restore(
            context,
            "correct-pass-123".toCharArray(),
            ByteArrayInputStream(bytes)
        )
        assertEquals(1, restored.accounts)
        assertEquals(1, restored.calls)
        assertEquals(1, restored.contacts)
        assertEquals(1, restored.favorites)

        val restoredAcc = repo.getAccount().first()
        assertNotNull(restoredAcc)
        assertEquals("office_auth", restoredAcc!!.authUsername)
        assertEquals("strongBackupSecret", restoredAcc.password)
        assertEquals(90, restoredAcc.registrationExpiry)
        assertEquals(1, db.favoritesDao().getAllOnce().size)
        assertEquals(1, db.savedContactDao().getAllOnce().size)
        assertTrue(restored.settingsIncluded)
    }

    @Test
    fun backupExportAndRestoreSelectiveCallHistoryAndSettingsOnly() = runBlocking {
        repo.saveAccount(
            SipAccountData(
                name = "Ignored Line",
                username = "9009",
                password = "pw",
                domain = "sip.ignored.net"
            )
        )
        db.callHistoryDao().insertCallRecord(
            CallRecord(
                remoteAddress = "+15559876543",
                displayName = "Bob",
                isIncoming = true,
                isAnswered = false,
                duration = 0,
                timestamp = 1_710_000_000_000L,
                accountUser = "9009",
                accountId = "acc-9009",
                accountName = "Sales Line"
            )
        )
        com.sipdial.app.util.BlockList.init(context)
        com.sipdial.app.util.BlockList.add(context, "+19990001111")
        com.sipdial.app.util.CallingPrefs.setAutoAnswerEnabled(context, true)
        com.sipdial.app.util.GeneralPrefs.setDefaultOpeningTab(context, com.sipdial.app.util.GeneralPrefs.TAB_CONTACTS)
        com.sipdial.app.util.GeneralPrefs.setHapticFeedbackEnabled(context, false)
        com.sipdial.app.util.GeneralPrefs.setNumberFormattingEnabled(context, false)

        val options = BackupManager.BackupOptions(
            includeCalls = true,
            includeAccounts = false,
            includeContactsAndFavorites = false,
            includeSettings = true
        )
        val (bytes, exported) = BackupManager.exportToBytes(
            context = context,
            passphrase = "local-pass-99".toCharArray(),
            options = options
        )
        assertEquals(0, exported.accounts)
        assertEquals(1, exported.calls)
        assertEquals(0, exported.contacts)
        assertEquals(0, exported.favorites)
        assertTrue(exported.settingsIncluded)

        // Clear calls, blocklist, and prefs
        db.callHistoryDao().deleteAllCallRecords()
        com.sipdial.app.util.BlockList.remove(context, "+19990001111")
        com.sipdial.app.util.CallingPrefs.setAutoAnswerEnabled(context, false)
        com.sipdial.app.util.GeneralPrefs.setDefaultOpeningTab(context, com.sipdial.app.util.GeneralPrefs.TAB_RECENTS)
        com.sipdial.app.util.GeneralPrefs.setHapticFeedbackEnabled(context, true)
        com.sipdial.app.util.GeneralPrefs.setNumberFormattingEnabled(context, true)

        val restored = BackupManager.restoreFromBytes(
            context = context,
            passphrase = "local-pass-99".toCharArray(),
            bytes = bytes
        )
        assertEquals(0, restored.accounts)
        assertEquals(1, restored.calls)
        assertTrue(restored.settingsIncluded)
        assertTrue(com.sipdial.app.util.CallingPrefs.isAutoAnswerEnabled(context))
        assertTrue(com.sipdial.app.util.BlockList.isBlocked(context, "+19990001111"))
        val restoredCall = db.callHistoryDao().getAllOnce().first()
        assertEquals("acc-9009", restoredCall.accountId)
        assertEquals("Sales Line", restoredCall.accountName)
        assertEquals(com.sipdial.app.util.GeneralPrefs.TAB_CONTACTS, com.sipdial.app.util.GeneralPrefs.getDefaultOpeningTab(context))
        assertEquals(false, com.sipdial.app.util.GeneralPrefs.isHapticFeedbackEnabled(context))
        assertEquals(false, com.sipdial.app.util.GeneralPrefs.isNumberFormattingEnabled(context))
    }

    @Test(expected = BackupManager.WrongPassphraseException::class)
    fun backupRestoreWithWrongPassphraseThrows() = runBlocking {
        val out = ByteArrayOutputStream()
        BackupManager.export(context, "secret123".toCharArray(), out)
        BackupManager.restore(context, "wrong-pass".toCharArray(), ByteArrayInputStream(out.toByteArray()))
        Unit
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRestoreWithInvalidFileThrows() = runBlocking {
        BackupManager.restore(
            context,
            "pass".toCharArray(),
            ByteArrayInputStream("not-a-valid-backup".toByteArray())
        )
        Unit
    }

    @Test
    fun settingsAndBackupScreensShowOnlyLocalSipbakBackupWithoutGoogleDrive() {
        composeRule.setContent {
            AppTheme {
                SettingsScreen(
                    currentAccount = null,
                    accountsCount = 0,
                    registrationState = SipRegistrationState.UNREGISTERED,
                    recordingsCount = 0,
                    onBack = {},
                    onOpenAppearance = {},
                    onOpenSipAccounts = {},
                    onOpenCalling = {},
                    onOpenAudioCodecs = {},
                    onOpenRecordings = {},
                    onOpenNotifications = {},
                    onOpenPowerNetwork = {},
                    onOpenPrivacySecurity = {},
                    onOpenBackupRestore = {},
                    onOpenAbout = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_row_backup"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Encrypted local .sipbak files").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Google Drive", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun backupRestoreScreenExposesLocalFileActionsAndNoGoogleDrive() {
        composeRule.setContent {
            AppTheme {
                BackupRestoreScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Local Encrypted File (.sipbak)").assertIsDisplayed()
        composeRule.onNodeWithText("Export Local Backup File").assertIsDisplayed()
        composeRule.onNodeWithText("Restore From Local File").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Google Drive", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().size
        )
    }
}
