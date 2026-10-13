package com.maxrave.spotify.auth

actual fun generateTotp(secret: String, timestamp: Long): String {
    val key = base32Decode(secret)
    val counter = (timestamp / 1000L) / 30L
    val counterBytes = ByteArray(8)
    for (i in 7 downTo 0) {
        counterBytes[i] = ((counter ushr ((7 - i) * 8)) and 0xffL).toByte()
    }
    val hash = hmacSha1(key, counterBytes)
    val offset = hash[hash.size - 1].toInt() and 0x0f
    val binary =
        ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
    val otp = binary % 1_000_000
    return otp.toString().padStart(6, '0')
}

private fun base32Decode(base32: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val clean = base32.trim().trimEnd('=').uppercase()
    val out = ArrayList<Byte>(clean.length * 5 / 8)
    var buffer = 0
    var bitsLeft = 0
    for (c in clean) {
        val val5 = alphabet.indexOf(c)
        if (val5 < 0) continue
        buffer = (buffer shl 5) or val5
        bitsLeft += 5
        if (bitsLeft >= 8) {
            bitsLeft -= 8
            out.add(((buffer ushr bitsLeft) and 0xff).toByte())
        }
    }
    return out.toByteArray()
}

private fun hmacSha1(key: ByteArray, message: ByteArray): ByteArray {
    val blockSize = 64
    val actualKey = if (key.size > blockSize) sha1(key) else key
    val ipad = ByteArray(blockSize) { i -> ((if (i < actualKey.size) actualKey[i].toInt() else 0) xor 0x36).toByte() }
    val opad = ByteArray(blockSize) { i -> ((if (i < actualKey.size) actualKey[i].toInt() else 0) xor 0x5c).toByte() }
    val inner = sha1(ipad + message)
    return sha1(opad + inner)
}

private fun Int.rotl(n: Int): Int = (this shl n) or (this ushr (32 - n))

private fun sha1(input: ByteArray): ByteArray {
    var h0 = 0x67452301
    var h1 = -0x10325477
    var h2 = -0x67452302
    var h3 = 0x10325476
    var h4 = -0x3c2d1e10

    val bitLen = input.size.toLong() * 8L
    val padLen = ((56 - (input.size + 1) % 64) + 64) % 64
    val padded = ByteArray(input.size + 1 + padLen + 8)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[padded.size - 1 - i] = ((bitLen ushr (i * 8)) and 0xffL).toByte()
    }

    val w = IntArray(80)
    var offset = 0
    while (offset < padded.size) {
        for (i in 0 until 16) {
            val j = offset + i * 4
            w[i] =
                ((padded[j].toInt() and 0xff) shl 24) or
                    ((padded[j + 1].toInt() and 0xff) shl 16) or
                    ((padded[j + 2].toInt() and 0xff) shl 8) or
                    (padded[j + 3].toInt() and 0xff)
        }
        for (i in 16 until 80) {
            w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotl(1)
        }
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        for (i in 0 until 80) {
            val f: Int
            val k: Int
            when (i) {
                in 0..19 -> {
                    f = (b and c) or (b.inv() and d)
                    k = 0x5a827999
                }
                in 20..39 -> {
                    f = b xor c xor d
                    k = 0x6ed9eba1
                }
                in 40..59 -> {
                    f = (b and c) or (b and d) or (c and d)
                    k = -0x70e44324
                }
                else -> {
                    f = b xor c xor d
                    k = -0x359d3e2a
                }
            }
            val temp = a.rotl(5) + f + e + k + w[i]
            e = d
            d = c
            c = b.rotl(30)
            b = a
            a = temp
        }
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        offset += 64
    }

    val out = ByteArray(20)
    val hs = intArrayOf(h0, h1, h2, h3, h4)
    for (i in 0 until 5) {
        out[i * 4] = (hs[i] ushr 24).toByte()
        out[i * 4 + 1] = (hs[i] ushr 16).toByte()
        out[i * 4 + 2] = (hs[i] ushr 8).toByte()
        out[i * 4 + 3] = hs[i].toByte()
    }
    return out
}