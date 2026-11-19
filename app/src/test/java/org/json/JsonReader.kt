package org.json

/**
 * Small JSON reader used only by local unit tests: the mockable android.jar
 * stubs out org.json, but production code deliberately uses the platform API.
 * Keeps tests dependency-free while exercising the real release parser.
 */
internal class JsonReader(private val source: String) {
    private var pos = 0

    fun readObject(): Map<String, Any?> {
        expect('{')
        val result = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (consume('}')) return result
        while (true) {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            result[key] = readValue()
            skipWhitespace()
            if (consume('}')) return result
            expect(',')
        }
    }

    fun readArray(): List<Any?> {
        expect('[')
        val result = mutableListOf<Any?>()
        skipWhitespace()
        if (consume(']')) return result
        while (true) {
            result += readValue()
            skipWhitespace()
            if (consume(']')) return result
            expect(',')
        }
    }

    private fun readValue(): Any? {
        skipWhitespace()
        return when (peek()) {
            '{' -> JSONObject(readObject())
            '[' -> JSONArray(readArray())
            '"' -> readString()
            't' -> {
                readLiteral("true")
                true
            }
            'f' -> {
                readLiteral("false")
                false
            }
            'n' -> {
                readLiteral("null")
                null
            }
            else -> readNumber()
        }
    }

    private fun readString(): String {
        expect('"')
        val out = StringBuilder()
        while (pos < source.length) {
            val c = source[pos++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> {
                    if (pos >= source.length) fail("Unterminated escape")
                    when (val escaped = source[pos++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (pos + 4 > source.length) fail("Bad unicode escape")
                            out.append(source.substring(pos, pos + 4).toInt(16).toChar())
                            pos += 4
                        }
                        else -> fail("Bad escape: $escaped")
                    }
                }
                else -> out.append(c)
            }
        }
        fail("Unterminated string")
    }

    private fun readNumber(): Any? {
        val start = pos
        while (pos < source.length && source[pos] !in ",]} \n\r\t") pos++
        val token = source.substring(start, pos)
        if (token.isEmpty()) fail("Expected value")
        return token.toLongOrNull() ?: token.toDoubleOrNull() ?: fail("Bad number: $token")
    }

    private fun readLiteral(literal: String) {
        if (!source.startsWith(literal, pos)) fail("Bad literal at $pos")
        pos += literal.length
    }

    private fun skipWhitespace() {
        while (pos < source.length && source[pos] in " \n\r\t") pos++
    }

    private fun peek(): Char = source.getOrNull(pos) ?: fail("Unexpected end of JSON")

    private fun consume(c: Char): Boolean {
        skipWhitespace()
        return if (source.getOrNull(pos) == c) {
            pos++
            true
        } else {
            false
        }
    }

    private fun expect(c: Char) {
        skipWhitespace()
        if (source.getOrNull(pos) != c) fail("Expected '$c' at $pos")
        pos++
    }

    private fun fail(message: String): Nothing = throw JSONException("$message in $source")
}
