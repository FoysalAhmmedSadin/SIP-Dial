package com.sipdial.app.util

import android.net.Uri
import org.linphone.core.Call

object CallerIdFormatter {
    private val GENERIC_CALLER_TOKENS = setOf(
        "anonymous",
        "unknown",
        "restricted",
        "private",
        "unavailable",
        "withheld"
    )

    fun extractNumber(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        if (s.contains('%')) {
            s = try {
                Uri.decode(s).trim()
            } catch (_: Exception) {
                s
            }
        }
        val lt = s.indexOf('<')
        val gt = s.indexOf('>', startIndex = maxOf(0, lt + 1))
        if (lt >= 0 && gt > lt + 1) {
            s = s.substring(lt + 1, gt).trim()
        }
        s = when {
            s.startsWith("sips:", ignoreCase = true) -> s.substring(5)
            s.startsWith("sip:", ignoreCase = true) -> s.substring(4)
            s.startsWith("tel:", ignoreCase = true) -> s.substring(4)
            else -> s
        }.trim()

        s = s.substringBefore("@")
            .substringBefore(";")
            .substringBefore("?")
            .substringBefore(",")
            .trim()
            .trim('"')
            .trim()

        val colonIdx = s.lastIndexOf(':')
        if (colonIdx > 0) {
            val suffix = s.substring(colonIdx + 1)
            if (suffix.isNotEmpty() && suffix.all { it.isDigit() }) {
                s = s.substring(0, colonIdx)
            }
        }
        return s.ifBlank { raw.trim() }
    }

    fun normalizeMatchDigits(raw: String): String {
        val cleaned = extractNumber(raw)
        val hasPlus = cleaned.trim().startsWith("+")
        var digits = cleaned.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        if (!hasPlus && digits.startsWith("00") && digits.length >= 9) {
            digits = digits.drop(2)
        }
        if (digits.startsWith("8800") && digits.length == 14) {
            digits = "880" + digits.substring(4)
        }
        return digits
    }

    fun numbersMatch(aRaw: String, bRaw: String): Boolean {
        val aClean = extractNumber(aRaw)
        val bClean = extractNumber(bRaw)
        if (aClean.isBlank() || bClean.isBlank()) return false
        if (aClean.equals(bClean, ignoreCase = true)) return true

        val aDigits = normalizeMatchDigits(aClean)
        val bDigits = normalizeMatchDigits(bClean)
        if (aDigits.isEmpty() || bDigits.isEmpty()) return false
        if (aDigits == bDigits) return true

        if (aDigits.length < 7 || bDigits.length < 7) {
            return false
        }
        if (aDigits.length >= 10 && bDigits.length >= 10) {
            return aDigits.takeLast(10) == bDigits.takeLast(10)
        }
        val shorter = if (aDigits.length <= bDigits.length) aDigits else bDigits
        val longer = if (aDigits.length <= bDigits.length) bDigits else aDigits
        return shorter.length >= 7 && longer.endsWith(shorter)
    }

    fun resolveCallAddress(call: Call): String {
        val remoteAddr = try { call.remoteAddress } catch (_: Exception) { null }
        val rawUri = try { remoteAddr?.asStringUriOnly().orEmpty() } catch (_: Exception) { "" }
        val userPart = try { remoteAddr?.username.orEmpty().trim() } catch (_: Exception) { "" }
        val domainPart = try { remoteAddr?.domain.orEmpty().trim() } catch (_: Exception) { "" }

        val decodedUser = extractNumber(userPart)
        if (decodedUser.isNotBlank() && decodedUser.lowercase() !in GENERIC_CALLER_TOKENS) {
            return if (domainPart.isNotBlank() && !decodedUser.contains("@")) {
                "sip:$decodedUser@$domainPart"
            } else {
                decodedUser
            }
        }

        val paiHeader = try { call.remoteParams?.getCustomHeader("P-Asserted-Identity").orEmpty() } catch (_: Exception) { "" }
        val ppiHeader = try { call.remoteParams?.getCustomHeader("P-Preferred-Identity").orEmpty() } catch (_: Exception) { "" }
        val rpidHeader = try { call.remoteParams?.getCustomHeader("Remote-Party-ID").orEmpty() } catch (_: Exception) { "" }
        val displayFallback = try { remoteAddr?.displayName.orEmpty().trim() } catch (_: Exception) { "" }

        val candidate = listOf(paiHeader, ppiHeader, rpidHeader, displayFallback)
            .map { extractNumber(it) }
            .firstOrNull {
                it.isNotBlank() &&
                    it.lowercase() !in GENERIC_CALLER_TOKENS &&
                    it.any { c -> c.isDigit() || c.isLetter() }
            }

        if (!candidate.isNullOrBlank()) {
            return if (domainPart.isNotBlank() && !candidate.contains("@")) {
                "sip:$candidate@$domainPart"
            } else {
                candidate
            }
        }
        return rawUri.ifBlank { decodedUser }
    }
}
