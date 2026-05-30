package app.hisaab.capture

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import android.test.mock.MockContentResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the inbox-query → RawCapture mapping in isolation using a MatrixCursor-backed fake
 * provider wired through an in-process MockContentResolver. The mapping (ADDRESS/BODY/DATE columns,
 * DATE > cursor filter, DATE ASC ordering, blank-body skip) is the production-critical logic and is
 * identical to CaptureService.backfillSince.
 *
 * The provider is registered programmatically (MockContentResolver.addProvider) rather than via the
 * androidTest manifest: a manifest-declared provider is hosted in the test APK's own process, which
 * has no Kotlin stdlib linked and crashes with NoClassDefFoundError when queried cross-process.
 * MockContentResolver runs the provider in the instrumentation process, so no separate process,
 * cross-UID export, or manifest entry is required.
 */
@RunWith(AndroidJUnit4::class)
class CaptureServiceBackfillInstrumentedTest {

    /** A minimal in-memory provider returning fixed inbox rows for any query. */
    class FakeSmsProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor {
            lastSelection = selection
            lastSelectionArgs = selectionArgs?.toList()
            lastSortOrder = sortOrder
            val cols = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
            return MatrixCursor(cols).apply {
                addRow(arrayOf<Any?>("bKash", "Tk 100 received", 100L))
                addRow(arrayOf<Any?>("NAGAD", "", 150L))            // blank body -> skipped by caller
                addRow(arrayOf<Any?>("Rocket", "Tk 300 sent", 300L))
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
        companion object {
            var lastSelection: String? = null
            var lastSelectionArgs: List<String>? = null
            var lastSortOrder: String? = null
        }
    }

    /** Replicates CaptureService.backfillSince's cursor-mapping against an arbitrary resolver. */
    private fun mapInboxCursor(resolver: ContentResolver, uri: Uri, cursorMs: Long): List<RawCapture> = buildList {
        resolver.query(
            uri,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(cursorMs.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val a = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val b = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val d = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val addr = c.getString(a) ?: continue
                val body = c.getString(b) ?: continue
                if (body.isBlank()) continue
                add(RawCapture(sender = addr, body = body, receivedAt = c.getLong(d), channel = CaptureChannel.SMS))
            }
        }
    }

    @Test
    fun maps_inbox_rows_skipping_blank_bodies_and_passing_cursor_filter() {
        // In-process fake provider via MockContentResolver — see class KDoc for why this avoids a
        // manifest-declared provider (separate test-APK process, NoClassDefFoundError on query).
        FakeSmsProvider.lastSelection = null
        FakeSmsProvider.lastSelectionArgs = null
        FakeSmsProvider.lastSortOrder = null
        val authority = "fakesms"
        // Attach a real Context + ProviderInfo so the ContentProvider.Transport URI validation
        // (validateIncomingUri → context.getUserId()) doesn't NPE when MockContentResolver routes
        // the query through the provider's IContentProvider transport.
        val provider = FakeSmsProvider().apply {
            attachInfo(
                InstrumentationRegistry.getInstrumentation().context,
                ProviderInfo().apply { this.authority = authority },
            )
        }
        val resolver = MockContentResolver().apply { addProvider(authority, provider) }
        val uri = Uri.parse("content://$authority/inbox")

        val rows = mapInboxCursor(resolver, uri, cursorMs = 50L)

        // Verify the query shape the production code uses.
        assertEquals("${Telephony.Sms.DATE} > ?", FakeSmsProvider.lastSelection)
        assertEquals(listOf("50"), FakeSmsProvider.lastSelectionArgs)
        assertEquals("${Telephony.Sms.DATE} ASC", FakeSmsProvider.lastSortOrder)
        // Verify mapping + blank-body skip (NAGAD row dropped).
        assertEquals(2, rows.size)
        assertEquals("bKash", rows[0].sender)
        assertEquals("Tk 100 received", rows[0].body)
        assertTrue(rows.none { it.sender == "NAGAD" })
        // Channel is always SMS for inbox-mapped rows; assert on the actual mapped row.
        assertEquals(CaptureChannel.SMS, rows[0].channel)
    }
}
