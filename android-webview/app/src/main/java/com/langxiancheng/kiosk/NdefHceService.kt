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
 *   3. READ BINARY         → returns CC file content
 *   4. SELECT NDEF FILE    (File ID: E104)
 *   5. READ BINARY         → returns NDEF message (NLEN + URI record)
 *
 * Reference: NFCForum-TS-Type-4-Tag_2.0.pdf
 */
class NdefHceService : HostApduService() {

    companion object {
        private const val TAG = "LXCNdefHce"

        // NFC Forum NDEF Type 4 Tag Application AID bytes (without CLA/INS/P1/P2/Lc/Le)
        private val NDEF_TAG_AID = byteArrayOf(
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x85.toByte(), 0x01.toByte(), 0x01.toByte()
        )

        // Capability Container (CC) file — describes the NDEF Type 4 Tag capabilities
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F,                     // CCLEN = 15 bytes
            0x20,                           // Mapping Version 2.0
            0x00, 0x3B,                     // Max R-APDU data size = 59
            0x00, 0x34,                     // Max C-APDU data size = 52
            0x04, 0x06,                     // NDEF File Control TLV: Tag=04, Len=06
            0xE1.toByte(), 0x04,            // NDEF File Identifier = E104
            0x00, 0xFF.toByte(),            // Max NDEF size = 255 bytes
            0x00,                           // Read access: no restriction
            0xFF.toByte()                   // Write access: denied
        )

        // Status words
        private val SUCCESS_SW = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val FAILURE_SW = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        /** Current NDEF URL to be served. Set by NfcBridge.prepareWrite(). */
        @Volatile
        var pendingUrl: String? = null
    }

    // State machine
    private var appSelected = false
    private var ccSelected = false
    private var ndefSelected = false

    // Pre-built NDEF record file (NLEN + NDEF message bytes)
    private var ndefRecordFile: ByteArray = buildNdefFile("https://kiosk-h5.pages.dev/result/D1")

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NdefHceService created")
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "APDU: ${commandApdu.toHexString()}")

        // Step 1: SELECT APPLICATION (NDEF Tag Application)
        // Match: CLA=00, INS=A4, P1=04, P2=00, and AID data contains D2760000850101
        // Note: some readers include Le byte (12 bytes), some don't (11 bytes)
        if (commandApdu.size >= 11 &&
            commandApdu[0] == 0x00.toByte() &&
            commandApdu[1] == 0xA4.toByte() &&
            commandApdu[2] == 0x04.toByte() &&
            commandApdu[3] == 0x00.toByte() &&
            commandApdu.containsSequence(NDEF_TAG_AID)
        ) {
            appSelected = true
            ccSelected = false
            ndefSelected = false

            // Refresh NDEF content from pending URL
            pendingUrl?.let { url ->
                ndefRecordFile = buildNdefFile(url)
                Log.i(TAG, "NDEF URL set: $url")
            }

            Log.d(TAG, "Application selected")
            return SUCCESS_SW
        }

        // Step 2: SELECT CC FILE (E103)
        // Match: CLA=00, INS=A4, and file ID E103 present
        if (appSelected && commandApdu.size >= 7 &&
            commandApdu[0] == 0x00.toByte() && commandApdu[1] == 0xA4.toByte() &&
            commandApdu.containsSequence(byteArrayOf(0xE1.toByte(), 0x03.toByte()))
        ) {
            ccSelected = true
            ndefSelected = false
            Log.d(TAG, "CC file selected")
            return SUCCESS_SW
        }

        // Step 3: SELECT NDEF FILE (E104)
        // Match: CLA=00, INS=A4, and file ID E104 present
        if (appSelected && commandApdu.size >= 7 &&
            commandApdu[0] == 0x00.toByte() && commandApdu[1] == 0xA4.toByte() &&
            commandApdu.containsSequence(byteArrayOf(0xE1.toByte(), 0x04.toByte()))
        ) {
            ccSelected = false
            ndefSelected = true
            Log.d(TAG, "NDEF file selected")
            return SUCCESS_SW
        }

        // Step 4: READ BINARY
        if (commandApdu.size >= 5 &&
            commandApdu[0] == 0x00.toByte() && commandApdu[1] == 0xB0.toByte()
        ) {
            val offset = ((0x00FF.toInt() and commandApdu[2].toInt()) * 256 +
                         (0x00FF.toInt() and commandApdu[3].toInt()))
            val le = 0x00FF.toInt() and commandApdu[4].toInt()

            // Read CC file
            if (ccSelected) {
                return readData(CC_FILE, offset, le)
            }

            // Read NDEF file
            if (ndefSelected) {
                return readData(ndefRecordFile, offset, le)
            }
        }

        Log.w(TAG, "Unknown APDU command: ${commandApdu.toHexString()}")
        return FAILURE_SW
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "HCE deactivated, reason=$reason")
        appSelected = false
        ccSelected = false
        ndefSelected = false
    }

    /**
     * Read [le] bytes from [data] starting at [offset], appending SUCCESS_SW.
     */
    private fun readData(data: ByteArray, offset: Int, le: Int): ByteArray {
        if (offset >= data.size) {
            return FAILURE_SW
        }
        val readLen = minOf(le, data.size - offset)
        val response = ByteArray(readLen + SUCCESS_SW.size)
        System.arraycopy(data, offset, response, 0, readLen)
        System.arraycopy(SUCCESS_SW, 0, response, readLen, SUCCESS_SW.size)
        return response
    }

    /**
     * Build an NDEF file: [NLEN (2 bytes)] + [NDEF message bytes]
     * The NDEF message contains a single URI record pointing to [url].
     */
    private fun buildNdefFile(url: String): ByteArray {
        val uriRecord = NdefRecord.createUri(url)
        val ndefMessage = NdefMessage(uriRecord)
        val ndefBytes = ndefMessage.toByteArray()
        val nlen = ndefBytes.size

        val file = ByteArray(nlen + 2)
        file[0] = (nlen shr 8).toByte()
        file[1] = (nlen and 0xFF).toByte()
        System.arraycopy(ndefBytes, 0, file, 2, nlen)

        Log.d(TAG, "Built NDEF file: ${nlen + 2} bytes for URL: $url")
        return file
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
