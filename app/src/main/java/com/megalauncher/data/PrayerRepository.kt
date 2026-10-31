package com.megalauncher.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class PrayerDay(
    val date: String, // yyyy-MM-dd
    val hijri: String = "",
    val imsak: String = "",
    val fajr: String,
    val syuruk: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
)

@Serializable
internal data class PrayerCache(val zone: String, val fetchedAt: Long, val days: List<PrayerDay>)

data class PrayerResult(val day: PrayerDay?, val error: String?)

enum class PrayerSlot(val label: String) {
    SUBUH("Subuh"), SYURUK("Syuruk"), ZOHOR("Zohor"), ASAR("Asar"), MAGHRIB("Maghrib"), ISYAK("Isyak")
}

fun PrayerDay.slots(): List<Pair<PrayerSlot, String>> = listOf(
    PrayerSlot.SUBUH to fajr,
    PrayerSlot.SYURUK to syuruk,
    PrayerSlot.ZOHOR to dhuhr,
    PrayerSlot.ASAR to asr,
    PrayerSlot.MAGHRIB to maghrib,
    PrayerSlot.ISYAK to isha,
)

/** The prayer whose time window contains [now]; null between Syuruk and Zohor. */
fun PrayerDay.currentSlot(now: LocalTime): PrayerSlot? = runCatching {
    fun t(s: String) = LocalTime.parse(s)
    when {
        now < t(fajr) -> PrayerSlot.ISYAK
        now < t(syuruk) -> PrayerSlot.SUBUH
        now < t(dhuhr) -> null
        now < t(asr) -> PrayerSlot.ZOHOR
        now < t(maghrib) -> PrayerSlot.ASAR
        now < t(isha) -> PrayerSlot.MAGHRIB
        else -> PrayerSlot.ISYAK
    }
}.getOrNull()

/** JAKIM e-Solat prayer times, cached on disk for ~1 month per zone (one file per zone, so profiles don't evict each other). */
class PrayerRepository(context: Context) {
    private val dir = context.filesDir

    private fun fileFor(zone: String) = File(dir, "prayer_cache_${zone.filter { it.isLetterOrDigit() }}.json")

    init {
        // Pre-0.1.0 single-zone cache file.
        File(dir, "prayer_cache.json").delete()
    }

    suspend fun today(zone: String, online: Boolean, force: Boolean): PrayerResult {
        val file = fileFor(zone)
        val today = LocalDate.now()
        val cache = FileCache.read<PrayerCache>(file)?.takeIf { it.zone == zone }
        val cachedToday = cache?.days?.find { it.date == today.toString() }

        if (!force && !needsRefresh(cache, today)) return PrayerResult(cachedToday, null)
        if (!online) return PrayerResult(cachedToday, if (cachedToday == null) "No internet connection" else null)

        return try {
            val fresh = fetch(zone, today, today.plusDays(CACHE_DAYS))
            val keepFrom = today.minusDays(1).toString()
            val merged = (cache?.days.orEmpty().filter { it.date >= keepFrom }.associateBy { it.date } +
                fresh.associateBy { it.date }).values.sortedBy { it.date }
            FileCache.write(file, PrayerCache(zone, System.currentTimeMillis(), merged))
            PrayerResult(merged.find { it.date == today.toString() }, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PrayerResult(cachedToday, if (cachedToday == null) "JAKIM e-Solat unavailable" else null)
        }
    }

    private fun needsRefresh(cache: PrayerCache?, today: LocalDate): Boolean {
        if (cache == null) return true
        if (cache.days.none { it.date == today.toString() }) return true
        val last = cache.days.maxOfOrNull { it.date } ?: return true
        val runningOut = LocalDate.parse(last) < today.plusDays(REFRESH_WHEN_DAYS_LEFT)
        val tooOld = System.currentTimeMillis() - cache.fetchedAt > MAX_AGE_MS
        return runningOut || tooOld
    }

    private suspend fun fetch(zone: String, from: LocalDate, to: LocalDate): List<PrayerDay> {
        val base = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&zone=${Net.encode(zone)}"
        var days = try {
            parse(Net.postForm("$base&period=duration", mapOf("datestart" to from.toString(), "dateend" to to.toString())))
        } catch (e: IOException) {
            emptyList()
        }
        if (days.isEmpty()) days = parse(Net.get("$base&period=month"))
        if (days.isEmpty()) throw IOException("No prayer data for $zone")
        return days
    }

    private fun parse(body: String): List<PrayerDay> {
        val arr = runCatching { JSONObject(body).optJSONArray("prayerTime") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val date = parseDate(o.optString("date")) ?: return@mapNotNull null
            PrayerDay(
                date = date,
                hijri = o.optString("hijri"),
                imsak = hm(o.optString("imsak")),
                fajr = hm(o.optString("fajr")),
                syuruk = hm(o.optString("syuruk")),
                dhuhr = hm(o.optString("dhuhr")),
                asr = hm(o.optString("asr")),
                maghrib = hm(o.optString("maghrib")),
                isha = hm(o.optString("isha")),
            )
        }
    }

    private fun hm(value: String) = value.take(5)

    /** e-Solat dates look like "31-Oct-2026" (English or Malay month abbreviations). */
    private fun parseDate(value: String): String? = runCatching {
        val p = value.trim().split("-")
        if (p.size != 3) return null
        if (p[0].length == 4) return LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt()).toString()
        val month = MONTHS[p[1].take(3).lowercase()] ?: p[1].toInt()
        LocalDate.of(p[2].toInt(), month, p[0].toInt()).toString()
    }.getOrNull()

    private companion object {
        const val CACHE_DAYS = 31L
        const val REFRESH_WHEN_DAYS_LEFT = 7L
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        val MONTHS = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "mac" to 3, "apr" to 4, "may" to 5, "mei" to 5,
            "jun" to 6, "jul" to 7, "aug" to 8, "ogo" to 8, "sep" to 9, "oct" to 10, "okt" to 10,
            "nov" to 11, "dec" to 12, "dis" to 12,
        )
    }
}
