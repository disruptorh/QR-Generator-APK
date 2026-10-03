package com.disruptorh.qrgenerator.core

/** The content types the core supports, in the order the selector shows them. */
enum class ContentType(val wire: String, val label: String) {
    text("text", "Texto"),
    url("url", "URL"),
    wifi("wifi", "Wi-Fi"),
    vcard("vcard", "Contacto"),
    email("email", "Correo"),
    sms("sms", "SMS"),
    phone("phone", "Teléfono"),
    geo("geo", "Ubicación"),
    event("event", "Evento"),
}

/** Wi-Fi authentication, as written into the WIFI: payload. */
enum class WifiAuth(val wire: String, val label: String) {
    wpa("wpa", "WPA / WPA2"),
    sae("sae", "WPA3"),
    wep("wep", "WEP"),
    nopass("nopass", "Abierta"),
}

/** Error correction level. */
enum class Ecc(val wire: String, val label: String) {
    low("low", "Bajo (7%)"),
    medium("medium", "Medio (15%)"),
    quartile("quartile", "Cuartil (25%)"),
    high("high", "Alto (30%)"),
}

/** File format the export buttons produce. */
enum class OutputFormat(val wire: String, val label: String, val mime: String, val extension: String) {
    png("png", "PNG", "image/png", "png"),
    svg("svg", "SVG", "image/svg+xml", "svg"),
}

/**
 * Everything the user can type or choose, mirroring the core's `app::AppInputs`
 * field for field.
 *
 * Coordinates stay as text on purpose: the core owns the range check and reports
 * a coordinate that is not a number, so the editor never has to invent a value
 * the user did not type.
 */
data class Inputs(
    val type: ContentType = ContentType.text,
    val text: String = "",
    val url: String = "",
    val ssid: String = "",
    val wifiPassword: String = "",
    val wifiAuth: WifiAuth = WifiAuth.wpa,
    val wifiHidden: Boolean = false,
    val fullName: String = "",
    val organization: String = "",
    val title: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val contactUrl: String = "",
    val address: String = "",
    val emailAddress: String = "",
    val emailSubject: String = "",
    val emailBody: String = "",
    val smsNumber: String = "",
    val smsMessage: String = "",
    val phoneNumber: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val altitude: String = "",
    val eventSummary: String = "",
    val eventStart: String = "",
    val eventEnd: String = "",
    val eventLocation: String = "",
    val eventDescription: String = "",
    val ecc: Ecc = Ecc.medium,
    val boostEcc: Boolean = true,
    val mask: Int = -1,
    val minVersion: Int = 1,
    val maxVersion: Int = 40,
    val modulePx: Int = 8,
    val quietZone: Int = 4,
    /** 0xRRGGBB, no alpha: the wire form and the core both use plain channels. */
    val foreground: Int = 0x000000,
    val background: Int = 0xFFFFFF,
    val format: OutputFormat = OutputFormat.png,
) {
    /** Encodes the wire form the native codec reads. */
    fun toWire(): ByteArray {
        val wire = StringBuilder(512)
        wire.field("type", type.wire)
        wire.field("t.body", text)
        wire.field("url", url)
        wire.field("wifi.ssid", ssid)
        wire.field("wifi.pass", wifiPassword)
        wire.field("wifi.auth", wifiAuth.wire)
        wire.field("wifi.hidden", wifiHidden)
        wire.field("vc.name", fullName)
        wire.field("vc.org", organization)
        wire.field("vc.title", title)
        for (phone in phones) wire.field("vc.phone", phone)
        for (email in emails) wire.field("vc.email", email)
        wire.field("vc.url", contactUrl)
        wire.field("vc.addr", address)
        wire.field("em.addr", emailAddress)
        wire.field("em.subject", emailSubject)
        wire.field("em.body", emailBody)
        wire.field("sms.number", smsNumber)
        wire.field("sms.message", smsMessage)
        wire.field("tel.number", phoneNumber)
        wire.field("geo.lat", latitude)
        wire.field("geo.lon", longitude)
        wire.field("geo.alt", altitude)
        wire.field("ev.summary", eventSummary)
        wire.field("ev.start", eventStart)
        wire.field("ev.end", eventEnd)
        wire.field("ev.location", eventLocation)
        wire.field("ev.desc", eventDescription)
        wire.field("enc.ecc", ecc.wire)
        wire.field("enc.boost", boostEcc)
        wire.field("enc.mask", mask.toString())
        wire.field("enc.minver", minVersion.toString())
        wire.field("enc.maxver", maxVersion.toString())
        wire.field("rnd.module_px", modulePx.toString())
        wire.field("rnd.quiet", quietZone.toString())
        wire.field("rnd.fg", foreground.toHex6())
        wire.field("rnd.bg", background.toHex6())
        wire.field("out.format", format.wire)
        return wire.toString().toByteArray(Charsets.UTF_8)
    }
}

private fun StringBuilder.field(key: String, value: String) {
    append(key).append('=').append(Wire.escape(value)).append('\n')
}

private fun StringBuilder.field(key: String, value: Boolean) {
    field(key, if (value) "1" else "0")
}

private fun Int.toHex6(): String {
    val digits = "0123456789ABCDEF"
    val out = StringBuilder(6)
    for (shift in intArrayOf(20, 16, 12, 8, 4, 0)) {
        out.append(digits[(this shr shift) and 0xF])
    }
    return out.toString()
}

/**
 * Percent-escaping shared with the native codec: every byte outside
 * [A-Za-z0-9._~-] becomes %XX, which keeps values with newlines or '=' from
 * breaking the line framing.
 */
internal object Wire {
    private const val HEX = "0123456789ABCDEF"

    fun escape(value: String): String {
        val out = StringBuilder(value.length + 8)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            val plain = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' ||
                char == '.' || char == '_' || char == '~' || char == '-'
            if (plain) {
                out.append(char)
            } else {
                out.append('%').append(HEX[code shr 4]).append(HEX[code and 0xF])
            }
        }
        return out.toString()
    }

    fun unescape(value: String): String {
        if ('%' !in value) return value
        val bytes = ByteArray(value.length)
        var count = 0
        var index = 0
        while (index < value.length) {
            val char = value[index]
            val code = if (char == '%' && index + 2 < value.length) {
                hexDigit(value[index + 1]) * 16 + hexDigit(value[index + 2])
            } else {
                -1
            }
            if (code >= 0) {
                bytes[count++] = code.toByte()
                index += 3
            } else {
                // A '%' that is not two hex digits stays literal, matching the
                // native side instead of silently dropping it.
                for (byte in char.toString().toByteArray(Charsets.UTF_8)) bytes[count++] = byte
                index++
            }
        }
        return String(bytes, 0, count, Charsets.UTF_8)
    }

    private fun hexDigit(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }
}
