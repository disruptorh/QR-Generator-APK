package com.disruptorh.qrgenerator.core

import android.graphics.Bitmap

/**
 * The Kotlin side of the shared core.
 *
 * Nothing here reimplements QR logic: every call goes through the same C++
 * encoder, decoder and rasterizer the desktop app uses, so the two front ends
 * cannot drift apart.
 */
object QrCore {

    init {
        System.loadLibrary("qr_bridge")
    }

    /** Outcome of one generation, with the symbol already verified by the core. */
    data class Generated(
        val payload: String,
        val describe: String,
        val filename: String,
        val version: Int,
        val modules: Int,
        val mask: Int,
        val usedBytes: Int,
        val capacityBytes: Int,
        val eccCodewords: Int,
        val verifiedBlocks: Int,
        val pixelWidth: Int,
        val pixelHeight: Int,
        val previewWidth: Int,
        val previewHeight: Int,
    ) {
        val usagePercent: Int
            get() = if (capacityBytes > 0) usedBytes * 100 / capacityBytes else 0

        val pixelSize: String
            get() = "$pixelWidth x $pixelHeight px"
    }

    /**
     * Either a verified symbol or the reason there is none. [errorField] is the
     * core's stable field identifier, so the screen can mark the exact input.
     */
    data class Result(
        val generated: Generated?,
        val errorField: String,
        val errorMessage: String,
    ) {
        val ok: Boolean get() = generated != null
    }

    fun generate(inputs: Inputs): Result {
        val record = parseRecord(nativeGenerate(inputs.toWire()))
        if (record["ok"] != "1") {
            return Result(null, record["err.field"].orEmpty(), record["err.msg"].orEmpty())
        }
        return Result(
            generated = Generated(
                payload = record.value("payload"),
                describe = record.value("describe"),
                filename = record.value("filename"),
                version = record.int("version"),
                modules = record.int("modules"),
                mask = record.int("mask"),
                usedBytes = record.int("used_bytes"),
                capacityBytes = record.int("capacity_bytes"),
                eccCodewords = record.int("ecc_codewords"),
                verifiedBlocks = record.int("verify_blocks"),
                pixelWidth = record.int("px_w"),
                pixelHeight = record.int("px_h"),
                previewWidth = record.int("preview_w"),
                previewHeight = record.int("preview_h"),
            ),
            errorField = "",
            errorMessage = "",
        )
    }

    /**
     * One pixel per module; the screen scales it, so this stays cheap. Same core
     * rasterizer as the export, only the pixel size differs.
     *
     * [width] and [height] come from [generate] on the same inputs, because the
     * core owns that arithmetic; here we only wrap its pixels in a [Bitmap].
     */
    fun preview(inputs: Inputs, width: Int, height: Int): Bitmap? {
        val pixels = nativePreview(inputs.toWire())
        if (width <= 0 || height <= 0 || pixels.size != width * height) return null
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun exportPng(inputs: Inputs): ByteArray = nativeExportPng(inputs.toWire())

    fun exportSvg(inputs: Inputs): ByteArray = nativeExportSvg(inputs.toWire())

    private external fun nativeGenerate(inputs: ByteArray): ByteArray
    private external fun nativePreview(inputs: ByteArray): IntArray
    private external fun nativeExportPng(inputs: ByteArray): ByteArray
    private external fun nativeExportSvg(inputs: ByteArray): ByteArray

    private fun parseRecord(bytes: ByteArray): Map<String, String> {
        val record = HashMap<String, String>()
        for (line in String(bytes, Charsets.UTF_8).split('\n')) {
            if (line.isEmpty()) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            record[line.substring(0, separator)] = Wire.unescape(line.substring(separator + 1))
        }
        return record
    }

    private fun Map<String, String>.value(key: String): String = this[key] ?: ""

    private fun Map<String, String>.int(key: String): Int = this[key]?.toIntOrNull() ?: 0
}
