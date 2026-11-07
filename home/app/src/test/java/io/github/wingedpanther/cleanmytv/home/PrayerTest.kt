package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PrayerTest {
    private val day = LocalDate.of(2026, 11, 8)
    private fun at(h: Int, m: Int) = day.atTime(h, m).toInstant(Prayer.MY).toEpochMilli()
    private val dayMs = 24 * 60 * 60 * 1000L

    private val jakim = """
        {"prayerTime":[{"hijri":"1448-05-26","date":"08-Nov-2026","day":"Sunday","imsak":"04:42:00","fajr":"04:52:00",
        "syuruk":"06:03:00","dhuhr":"12:00:00","asr":"15:17:00","maghrib":"18:00:00","isha":"19:11:00"}],
        "status":"OK!","periodType":"today","lang":"ms_my","zone":"SWK08"}
    """.trimIndent()

    private fun parsed() = Prayer.parse(jakim, day)!!

    @Test fun `JAKIM's answer for today gives six times in order`() {
        val p = parsed()
        assertEquals(listOf("subuh", "syuruk", "zohor", "asar", "maghrib", "isyak"), p.times.map { it.id })
        assertEquals(at(4, 52), p.times[0].at)
        assertEquals(at(12, 0), p.times[2].at)
        assertEquals(at(19, 11), p.times[5].at)
    }

    @Test fun `the Malay field names and Unix times work too`() {
        val malay = """{"prayerTime":[{"subuh":"04:52","syuruk":"06:03","zohor":"12:00","asar":"15:17","maghrib":"18:00","isyak":"19:11"}]}"""
        assertEquals(parsed().times, Prayer.parse(malay, day)!!.times)
        val unix = JSONObject().put("prayerTime", org.json.JSONArray().put(JSONObject()
            .put("fajr", at(4, 52) / 1000).put("syuruk", at(6, 3) / 1000).put("dhuhr", at(12, 0) / 1000)
            .put("asr", at(15, 17) / 1000).put("maghrib", at(18, 0) / 1000).put("isha", at(19, 11) / 1000)))
        assertEquals(parsed().times, Prayer.parse(unix.toString(), day)!!.times)
    }

    @Test fun `anything that is not a full, increasing table is refused`() {
        assertNull(Prayer.parse("not json", day))
        assertNull(Prayer.parse("""{"prayerTime":[]}""", day))
        assertNull(Prayer.parse("""{"status":"Not found"}""", day))
        assertNull(Prayer.parse(jakim.replace("\"asr\":\"15:17:00\",", ""), day))
        assertNull(Prayer.parse(jakim.replace("\"dhuhr\":\"12:00:00\"", "\"dhuhr\":\"03:00:00\""), day))
        assertNull(Prayer.parse(jakim.replace("\"isha\":\"19:11:00\"", "\"isha\":\"soon\""), day))
    }

    @Test fun `the next prayer is the first one after now`() {
        val p = parsed()
        assertEquals("subuh", p.next(at(3, 0))!!.id)
        assertEquals("zohor", p.next(at(10, 0))!!.id)
        assertEquals("asar", p.next(at(12, 0))!!.id) // at the minute itself, that prayer has come
        assertEquals("isyak", p.next(at(18, 30))!!.id)
    }

    @Test fun `after Isyak the next prayer is tomorrow's Subuh`() {
        val next = parsed().next(at(20, 0))!!
        assertEquals("subuh", next.id)
        assertEquals(at(4, 52) + dayMs, next.at)
    }

    @Test fun `the countdown rounds up and never says zero`() {
        assertEquals("1h 23m", Prayer.countdown(at(12, 0), at(10, 37)))
        assertEquals("12 min", Prayer.countdown(at(12, 0), at(11, 48)))
        assertEquals("1 min", Prayer.countdown(at(12, 0), at(12, 0) - 30_000))
        assertEquals("1h 0m", Prayer.countdown(at(12, 0), at(11, 0)))
    }

    @Test fun `the next fetch waits two minutes with no table, else until after midnight at most six hours`() {
        assertEquals(2 * 60_000L, Prayer.refreshIn(false, at(10, 0)))
        assertEquals(6 * 60 * 60_000L, Prayer.refreshIn(true, at(10, 0)))
        assertEquals(70 * 60_000L, Prayer.refreshIn(true, at(22, 55))) // 22:55 to 00:05 is 70 minutes
    }

    @Test fun `the zone is a JAKIM code, upper-cased, or blank to turn the line off`() {
        assertEquals("SWK08", HomeConfig.fromJson(JSONObject().put("prayer_zone", " swk08 ")).prayerZone)
        val on = HomeConfig(prayerZone = "SWK08")
        assertEquals("SWK08", HomeConfig.fromJson(JSONObject().put("prayer_zone", "Kuching"), on).prayerZone)
        assertEquals("SWK08", HomeConfig.fromJson(JSONObject().put("accent", "#FFFFFF"), on).prayerZone)
        assertEquals("", HomeConfig.fromJson(JSONObject().put("prayer_zone", ""), on).prayerZone)
        assertNotNull(HomeConfig().toJson().get("prayer_zone"))
    }
}
