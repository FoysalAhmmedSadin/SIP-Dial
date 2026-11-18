package com.sipdial.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sipdial.app.data.BackupManager
import com.sipdial.app.data.drive.DriveBackupPrefs
import com.sipdial.app.data.drive.GoogleDriveBackupService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GoogleDriveBackupServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DriveBackupPrefs.clearAccountState(context)
        DriveBackupPrefs.setUseAppDataFolder(context, false)
        DriveBackupPrefs.setBackupOptions(context, BackupManager.BackupOptions())
    }

    @Test
    fun buildUploadMetadataJsonIncludesExpectedFieldsAndParent() {
        val raw = GoogleDriveBackupService.buildUploadMetadataJson(
            filename = "SipDial-drive-20261114.sipbak",
            parentId = "appDataFolder",
            description = "Calls, SIP Accounts"
        )
        val obj = JSONObject(raw)
        assertEquals("SipDial-drive-20261114.sipbak", obj.getString("name"))
        assertEquals("application/octet-stream", obj.getString("mimeType"))
        assertEquals("Calls, SIP Accounts", obj.getString("description"))
        assertEquals("appDataFolder", obj.getJSONArray("parents").getString(0))
    }

    @Test
    fun parseFileListJsonParsesDriveAndAppDataSpaces() {
        val payload = """
            {
              "files": [
                {
                  "id": "file_1",
                  "name": "SipDial-drive-1.sipbak",
                  "size": "4096",
                  "modifiedTime": "2026-11-14T08:15:00.000Z",
                  "description": "Calls, SIP Accounts",
                  "spaces": ["drive"]
                },
                {
                  "id": "file_2",
                  "name": "SipDial-drive-2.sipbak",
                  "size": "8192",
                  "modifiedTime": "2026-11-14T09:30:00.000Z",
                  "description": "Full backup",
                  "spaces": ["appDataFolder"]
                }
              ]
            }
        """.trimIndent()

        val files = GoogleDriveBackupService.parseFileListJson(payload, defaultAppData = false)
        assertEquals(2, files.size)
        assertEquals("file_1", files[0].id)
        assertEquals(4096L, files[0].sizeBytes)
        assertFalse(files[0].isAppData)

        assertEquals("file_2", files[1].id)
        assertEquals(8192L, files[1].sizeBytes)
        assertTrue(files[1].isAppData)
    }

    @Test
    fun parseSingleFileJsonRespectsDefaultAppDataFlag() {
        val payload = """
            {
              "id": "single_99",
              "name": "SipDial-drive-hidden.sipbak",
              "size": "1024",
              "modifiedTime": "2026-11-14T10:00:00.000Z",
              "description": "Calls only"
            }
        """.trimIndent()
        val file = GoogleDriveBackupService.parseSingleFileJson(payload, defaultAppData = true)
        assertEquals("single_99", file.id)
        assertEquals("SipDial-drive-hidden.sipbak", file.name)
        assertTrue(file.isAppData)
    }

    @Test
    fun driveBackupPrefsPersistAndClearStateCleanly() {
        DriveBackupPrefs.setAccountEmail(context, "user@example.com")
        assertEquals("user@example.com", DriveBackupPrefs.getAccountEmail(context))

        DriveBackupPrefs.setUseAppDataFolder(context, true)
        assertTrue(DriveBackupPrefs.isUseAppDataFolder(context))

        val customOptions = BackupManager.BackupOptions(
            includeCalls = true,
            includeAccounts = false,
            includeContactsAndFavorites = true,
            includeSettings = false
        )
        DriveBackupPrefs.setBackupOptions(context, customOptions)
        assertEquals(customOptions, DriveBackupPrefs.getBackupOptions(context))
        assertTrue(customOptions.hasAnySelection())
        assertEquals("Calls, Contacts & Favorites", customOptions.summaryLabel())

        DriveBackupPrefs.recordBackup(context, 1_731_570_000_000L, "Calls: 12")
        assertEquals(1_731_570_000_000L, DriveBackupPrefs.getLastBackupTimestamp(context))
        assertEquals("Calls: 12", DriveBackupPrefs.getLastBackupSummary(context))

        DriveBackupPrefs.clearAccountState(context)
        assertNull(DriveBackupPrefs.getAccountEmail(context))
    }

    @Test
    fun describeStatusCodeExplainsDeveloperErrorAndIncludesPackageHint() {
        val msg = com.sipdial.app.data.drive.GoogleDriveAuthManager.describeStatusCode(
            packageName = "com.sipdial.app",
            sha1 = "AA:BB:CC",
            statusCode = 10,
            detail = "10:"
        )
        assertTrue(msg.contains("DEVELOPER_ERROR 10"))
        assertTrue(msg.contains("com.sipdial.app"))
        assertTrue(msg.contains("AA:BB:CC"))
    }

    @Test
    fun extractTokenFromCanceledEmptyIntentFlagsAccountFallbackEligible() {
        val outcome = com.sipdial.app.data.drive.GoogleDriveAuthManager.extractTokenFromResult(
            context = context,
            resultCode = android.app.Activity.RESULT_CANCELED,
            data = null
        )
        assertTrue(outcome is com.sipdial.app.data.drive.GoogleDriveAuthManager.AuthOutcome.Failed)
        val failed = outcome as com.sipdial.app.data.drive.GoogleDriveAuthManager.AuthOutcome.Failed
        assertTrue(failed.isCancellation)
        assertTrue(failed.canTryAccountFallback)
    }

    @Test
    fun buildOAuthDiagnosticTextIncludesPackageAndFingerprintsWhenPresent() {
        val diag = com.sipdial.app.data.drive.GoogleDriveAuthManager.buildOAuthDiagnosticText(context)
        assertTrue(diag.contains("Package: com.sipdial.app"))
    }
}
