package io.github.wingedpanther.cleanmytv.home

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** One prayer time today. `id` is subuh, syuruk, zohor, asar, maghrib or isyak; `at` is epoch milliseconds. */
data class PrayerTime(val id: String, val at: Long)

/** Today's six times in order, for the day `date` in Malaysia (UTC+8). */
data class PrayerDay(val date: LocalDate, val times: List<PrayerTime>) {
    /**
     * The first time after `now`. After Isyak it is tomorrow's Subuh, taken as today's plus a day: it is
     * off by a minute at most, and the fetch after midnight puts the real one in.
     */
    fun next(now: Long): PrayerTime? {
        times.firstOrNull { it.at > now }?.let { return it }
        val subuh = times.firstOrNull() ?: return null
        return subuh.copy(at = subuh.at + DAY_MS)
    }
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * JAKIM's e-Solat: the official Malaysian prayer times, by zone (SWK08 is Kuching, Bau, Lundu and Sematan).
 * No key, no account. One small request when Home opens, then after midnight and every six hours.
 */
object Prayer {
    /** Malaysia has one time zone, so the times mean the same wherever the box is set. */
    val MY: ZoneOffset = ZoneOffset.ofHours(8)

    /** The name Home shows for each id. */
    val NAMES = mapOf("subuh" to "Subuh", "syuruk" to "Syuruk", "zohor" to "Zohor", "asar" to "Asar", "maghrib" to "Maghrib", "isyak" to "Isyak")

    // JAKIM names its fields in Arabic transliteration; some mirrors use the Malay names. Either works.
    private val FIELDS = listOf(
        "subuh" to listOf("fajr", "subuh"),
        "syuruk" to listOf("syuruk"),
        "zohor" to listOf("dhuhr", "zohor"),
        "asar" to listOf("asr", "asar"),
        "maghrib" to listOf("maghrib"),
        "isyak" to listOf("isha", "isyak"),
    )

    /** `today` is the Malaysian date the times are for. Null when anything is missing or out of order. */
    fun parse(json: String, today: LocalDate): PrayerDay? = runCatching {
        val root = JSONObject(json)
        val day = root.optJSONArray("prayerTime")?.optJSONObject(0) ?: return@runCatching null
        val times = FIELDS.map { (id, keys) ->
            val raw = keys.firstNotNullOfOrNull { k -> day.opt(k)?.takeIf { it != JSONObject.NULL } } ?: return@runCatching null
            PrayerTime(id, instant(raw, today) ?: return@runCatching null)
        }
        // Six increasing times, or the answer is something other than a prayer table.
        if (times.zipWithNext().any { (a, b) -> b.at <= a.at }) null else PrayerDay(today, times)
    }.getOrNull()

    /** "05:08:00" or "05:08" as that time on `today` in Malaysia, or a Unix time in seconds. */
    private fun instant(raw: Any, today: LocalDate): Long? = when (raw) {
        is Number -> raw.toLong().takeIf { it > 1_000_000_000L }?.times(1000)
        is String -> runCatching { today.atTime(LocalTime.parse(raw.trim())).toInstant(MY).toEpochMilli() }.getOrNull()
        else -> null
    }

    /** "1h 23m", or "12 min" under an hour; rounded up, so it never says 0. */
    fun countdown(at: Long, now: Long): String {
        val m = ((at - now + 59_999L) / 60_000L).coerceAtLeast(1)
        return if (m < 60) "$m min" else "${m / 60}h ${m % 60}m"
    }

    /** Wait before the next fetch: two minutes while there is none yet, else until just after Malaysian midnight, at most six hours. */
    fun refreshIn(have: Boolean, now: Long = System.currentTimeMillis()): Long {
        if (!have) return 2 * 60_000L
        val tomorrow = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), MY).toLocalDate().plusDays(1)
        val midnight = tomorrow.atTime(0, 5).toInstant(MY).toEpochMilli()
        return minOf(6 * 60 * 60_000L, (midnight - now).coerceAtLeast(60_000L))
    }

    fun today(now: Long = System.currentTimeMillis()): LocalDate = java.time.Instant.ofEpochMilli(now).atZone(MY).toLocalDate()

    suspend fun fetch(zone: String): PrayerDay? = withContext(Dispatchers.IO) {
        val day = today()
        runCatching {
            val conn = URL("https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=today&zone=$zone").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            // A bare Java user agent is the kind of thing public services turn away.
            conn.setRequestProperty("User-Agent", "CleanMyTV-Home")
            try {
                if (conn.responseCode != 200) { Log.i(TAG, "prayer: HTTP ${conn.responseCode}"); return@runCatching null }
                val body = conn.inputStream.bufferedReader().use { it.readText().take(64_000) }
                parse(body, day).also { if (it == null) Log.i(TAG, "prayer: unreadable answer for $zone") }
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.i(TAG, "prayer: $it") }.getOrNull()
    }
}
