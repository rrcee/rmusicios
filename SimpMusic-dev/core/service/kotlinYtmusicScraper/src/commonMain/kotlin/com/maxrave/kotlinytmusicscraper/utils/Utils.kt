package com.maxrave.kotlinytmusicscraper.utils

import com.maxrave.kotlinytmusicscraper.models.response.AudioData
import com.maxrave.logger.Logger
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte ->
    (eachByte.toInt() and 0xFF).toString(16).padStart(2, '0')
}

fun sha1(input: String): String {
    val bytes = input.encodeToByteArray()
    var h0 = 0x67452301
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476
    var h4 = 0xC3D2E1F0.toInt()

    val messageLenBits = bytes.size.toLong() * 8
    val padLen = ((56 - ((bytes.size + 1) % 64)) + 64) % 64
    val totalLen = bytes.size + 1 + padLen + 8
    val padded = ByteArray(totalLen)
    bytes.copyInto(padded, 0, 0, bytes.size)
    padded[bytes.size] = 0x80.toByte()

    for (i in 0 until 8) {
        padded[totalLen - 8 + i] = ((messageLenBits ushr ((7 - i) * 8)) and 0xFF).toByte()
    }

    val w = IntArray(80)

    for (chunkStart in 0 until totalLen step 64) {
        for (i in 0 until 16) {
            val offset = chunkStart + i * 4
            w[i] = ((padded[offset].toInt() and 0xFF) shl 24) or
                   ((padded[offset + 1].toInt() and 0xFF) shl 16) or
                   ((padded[offset + 2].toInt() and 0xFF) shl 8) or
                   (padded[offset + 3].toInt() and 0xFF)
        }
        for (i in 16 until 80) {
            val num = w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]
            w[i] = (num shl 1) or (num ushr 31)
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4

        for (i in 0 until 80) {
            val f: Int
            val k: Int
            when {
                i in 0..19 -> {
                    f = (b and c) or ((b.inv()) and d)
                    k = 0x5A827999
                }
                i in 20..39 -> {
                    f = b xor c xor d
                    k = 0x6ED9EBA1
                }
                i in 40..59 -> {
                    f = (b and c) or (b and d) or (c and d)
                    k = 0x8F1BBCDC.toInt()
                }
                else -> {
                    f = b xor c xor d
                    k = 0xCA62C1D6.toInt()
                }
            }
            val temp = ((a shl 5) or (a ushr 27)) + f + e + k + w[i]
            e = d
            d = c
            c = (b shl 30) or (b ushr 2)
            b = a
            a = temp
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    return intArrayOf(h0, h1, h2, h3, h4).joinToString("") {
        (it.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0')
    }
}


fun parseCookieString(cookie: String): Map<String, String> =
    cookie
        .split("; ")
        .filter { it.isNotEmpty() }
        .associate {
            val (key, value) = it.split("=")
            key to value
        }

fun String.parseTime(): Int? {
    try {
        val parts =
            if (this.contains(":")) split(":").map { it.toInt() } else split(".").map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

fun generateNetscapeCookies(
    cookies: Map<String, String>,
    domain: String = ".example.com",
    path: String = "/",
    secure: Boolean = false,
    httpOnly: Boolean = false,
    expirationTimeSeconds: Long = 2147483647L,
): String {
    val header =
        "# Netscape HTTP Cookie File\n" +
            "# This is a generated file! Do not edit.\n\n"

    val cookieLines =
        cookies
            .map { (name, value) ->
                // Netscape format: domain, domainFlag, path, secure, expiration, name, value
                buildString {
                    append(domain)
                    append("\t")
                    append("TRUE") // domain flag - TRUE means domain includes subdomains
                    append("\t")
                    append(path)
                    append("\t")
                    append(if (secure) "TRUE" else "FALSE")
                    append("\t")
                    append(expirationTimeSeconds)
                    append("\t")
                    append(name)
                    append("\t")
                    append(value)
                }
            }.joinToString("\n")

    return header + cookieLines
}

fun String.decodeTidalManifest(): AudioData? {
    val decodedBytes = Base64.decode(this)
    val jsonString = decodedBytes.decodeToString()
    val json =
        Json {
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    return try {
        json.decodeFromString<AudioData?>(jsonString)
    } catch (e: Exception) {
        Logger.e("Utils", "Failed to decode Tidal manifest: ${e.message}")
        e.printStackTrace()
        null
    }
}

fun String.decodeBase64(): String {
    val decodedBytes = Base64.decode(this)
    return decodedBytes.decodeToString()
}