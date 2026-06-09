package com.langxiancheng.kiosk

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * HCE service that emulates an NFC Forum Type 4 Tag containing an NDEF URI record.
 *
 * When a phone taps the SUNMI FLEX 3's under-screen NFC area, this service responds
 * to the standard Type 4 Tag APDU command sequence and returns the NDEF message
 * containing the result page URL. The reading phone's OS will automatically open
 * the URL in a browser.
 *
 * Communication flow (NFC Forum Type 4 Tag v2.0):
 *   1. SELECT APPLICATION  (AID: D2760000850101)
 *   2. SELECT CC FILE      (File ID: E103)
 *   3. READ BINARY         -> returns CC file content
 *   4. SELECT NDEF FILE    (File ID: E104)
 *   5. READ BINARY         -> returns NDEF message (NLEN + URI record)
 *
 * v3.8.3 Optimizations:
 *   - Pre-build NDEF data on prepareWrite() for zero-delay APDU response
 *   - Increased CC file R-APDU/C-APDU to 256 for single-read compatibility
 *   - Support SELECT by DF name (P1=00) variant for broader phone compatibility
 *   - Support READ BINARY with Le=0 (meaning 256) as per ISO 7816-4
 *   - Auto-reset state on any unexpected APDU for faster retry
 *
 * Reference: NFCForum-TS-Type-4-Tag_2.0.pdf
 */
class NdefHceService : HostApduService() {

    companion object {
        private const val TAG = "LXCNdefHce"

        // NFC Forum NDEF Type 4 Tag Application AID bytes
        private val NDEF_TAG_AID = byteArrayOf(
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x85.toByte(), 0x01.toByte(), 0x01.toByte()
        )

        // Capability Container (CC) file — describes the NDEF Type 4 Tag capabilities
        // v3.8.3: Increased R-APDU/C-APDU to 256 bytes for single-read completion
        //         (previously 59/52, causing some phones to need multiple READ BINARY rounds)
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F,                     // CCLEN = 15 bytes
            0x20,                           // Mapping Version 2.0
            0x01, 0x00,                     // Max R-APDU data size = 256 (was 59)
            0x01, 0x00,                     // Max C-APDU data size = 256 (was 52)
            0x04, 0x06,                     // NDEF File Control TLV: Tag=04, Len=06
            0xE1.toByte(), 0x04,            // NDEF File Identifier = E104
            0x01, 0x00,                     // Max NDEF size = 256 bytes (was 255)
            0x00,                           // Read access: no restriction
            0xFF.toByte()                   // Write access: denied
        )

        // Status words
        private val SUCCESS_SW = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val FILE_NOT_FOUND_SW = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val WRONG_LENGTH_SW = byteArrayOf(0x6C.toByte(), 0x00.toByte())

        /** Current NDEF URL to be served. Set by NfcBridge.prepareWrite(). */
        @Volatile
        var pendingUrl: String? = null

        /** Pre-built NDEF record file, updated in prepareWrite() for zero-delay APDU response */
        @Volatile
        var prebuiltNdefFile: ByteArray = buildNdefFileStatic("https://kiosk-h5.pages.dev/result/D1")

        /** Build NDEF file without needing an instance */
        private fun buildNdefFileStatic(url: String): ByteArray {
            val uriRecord = NdefRecord.createUri(url)
            val ndefMessage = NdefMessage(uriRecord)
            val ndefBytes = ndefMessage.toByteArray()
            val nlen = ndefBytes.size

            val file = ByteArray(nlen + 2)
            file[0] = (nlen shr 8).toByte()
            file[1] = (nlen and 0xFF).toByte()
            System.arraycopy(ndefBytes, 0, file, 2, nlen)
            return file
        }

        /** Called by NfcBridge.prepareWrite() to pre-build NDEF data on the UI thread */
        fun prepareNdefData(url: String) {
            prebuiltNdefFile = buildNdefFileStatic(url)
            pendingUrl = url
            Log.d(TAG, "Pre-built NDEF file: ${prebuiltNdefFile.size} bytes for URL: $url")
        }

        /** Called by NfcBridge.cancelWrite() */
        fun clearNdefData() {
            pendingUrl = null
            Log.d(TAG, "NDEF data cleared")
        }
    }

    // State machine
    private var appSelected = false
    private var ccSelected = false
    private var ndefSelected = false

    // Cache the NDEF file for this session (copied from pre-built at SELECT APP)
    private var ndefRecordFile: ByteArray = prebuiltNdefFile

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NdefHceService created")
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "APDU: ${commandApdu.toHexString()}")

        // ---- Step 1: SELECT APPLICATION (NDEF Tag Application) ----
        // Standard: CLA=00, INS=A4, P1=04 (select by DF name), P2=00
        // Variant:  CLA=00, INS=A4, P1=00 (select by file ID), P2=0C/00
        // Match if INS=A4 and AID bytes are present anywhere in the command
        if (commandApdu.size >= 2 && commandApdu[1] == 0xA4.toByte() &&
            commandApdu.containsSequence(NDEF_TAG_AID)
        ) {
            appSelected = true
            ccSelected = false
            ndefSelected = false

            // Use pre-built NDEF data (built in prepareWrite, zero latency here)
            ndefRecordFile = prebuiltNdefFile
            Log.d(TAG, "Application selected, NDEF ready (${ndefRecordFile.size} bytes)")

            return SUCCESS_SW
        }

        // After app selected, handle file selections
        if (appSelected) {
            // ---- Step 2: SELECT CC FILE (E103) ----
            // Match: INS=A4 and file ID E103 present
            if (commandApdu.size >= 2 && commandApdu[1] == 0xA4.toByte() &&
                commandApdu.containsSequence(byteArrayOf(0xE1.toByte(), 0x03.toByte()))
            ) {
                ccSelected = true
                ndefSelected = false
                Log.d(TAG, "CC file selected")
                return SUCCESS_SW
            }

            // ---- Step 3: SELECT NDEF FILE (E104) ----
            // Match: INS=A4 and file ID E104 present
            if (commandApdu.size >= 2 && commandApdu[1] == 0xA4.toByte() &&
                commandApdu.containsSequence(byteArrayOf(0xE1.toByte(), 0x04.toByte()))
            ) {
                ccSelected = false
                ndefSelected = true
                Log.d(TAG, "NDEF file selected")
                return SUCCESS_SW
            }

            // ---- Step 4: READ BINARY ----
            if (commandApdu.size >= 5 && commandApdu[1] == 0xB0.toByte()) {
                val offset = ((0x00FF.toInt() and commandApdu[2].toInt()) * 256 +
                             (0x00FF.toInt() and commandApdu[3].toInt()))
                // Le=0 means 256 bytes per ISO 7816-4
                val le = (0x00FF.toInt() and commandApdu[4].toInt()).let { if (it == 0) 256 else it }

                // Read CC file
                if (ccSelected) {
                    return readData(CC_FILE, offset, le)
                }

                // Read NDEF file
                if (ndefSelected) {
                    return readData(ndefRecordFile, offset, le)
                }
            }
        }

        // Auto-reset: if we get an unexpected command, reset state to allow
        // the phone to retry immediately without waiting for deactivation
        if (commandApdu.size >= 2 && commandApdu[1] != 0xB0.toByte() && commandApdu[1] != 0xA4.toByte()) {
            Log.w(TAG, "Unexpected APDU, resetting state: ${commandApdu.toHexString()}")
            resetState()
        }

        Log.w(TAG, "Unhandled APDU: ${commandApdu.toHexString()}")
        return FILE_NOT_FOUND_SW
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "HCE deactivated, reason=$reason")
        resetState()
    }

    private fun resetState() {
        appSelected = false
        ccSelected = false
        ndefSelected = false
    }

    /**
     * Read [le] bytes from [data] starting at [offset], appending SUCCESS_SW.
     */
    private fun readData(data: ByteArray, offset: Int, le: Int): ByteArray {
        if (offset >= data.size) {
            return FILE_NOT_FOUND_SW
        }
        val readLen = minOf(le, data.size - offset)
        val response = ByteArray(readLen + SUCCESS_SW.size)
        System.arraycopy(data, offset, response, 0, readLen)
        System.arraycopy(SUCCESS_SW, 0, response, readLen, SUCCESS_SW.size)
        return response
    }

    /** Helper: check if byte array contains a subsequence */
    private fun ByteArray.containsSequence(sub: ByteArray): Boolean {
        if (sub.size > this.size) return false
        for (i in 0..(this.size - sub.size)) {
            var match = true
            for (j in sub.indices) {
                if (this[i + j] != sub[j]) { match = false; break }
            }
            if (match) return true
        }
        return false
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it) }
}
