package com.megalauncher.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** JAKIM e-Solat zones. Coordinates are approximate zone centres used for auto-detection; null = special zone. */
data class PrayerZone(val code: String, val state: String, val areas: String, val lat: Double?, val lon: Double?)

object PrayerZones {
    val all = listOf(
        PrayerZone("JHR01", "Johor", "Pulau Aur dan Pulau Pemanggil", 2.45, 104.52),
        PrayerZone("JHR02", "Johor", "Johor Bahru, Kota Tinggi, Mersing, Kulai", 1.55, 103.75),
        PrayerZone("JHR03", "Johor", "Kluang, Pontian", 1.85, 103.35),
        PrayerZone("JHR04", "Johor", "Batu Pahat, Muar, Segamat, Gemas Johor, Tangkak", 2.05, 102.75),
        PrayerZone("KDH01", "Kedah", "Kota Setar, Kubang Pasu, Pokok Sena", 6.15, 100.40),
        PrayerZone("KDH02", "Kedah", "Kuala Muda, Yan, Pendang", 5.70, 100.45),
        PrayerZone("KDH03", "Kedah", "Padang Terap, Sik", 6.00, 100.70),
        PrayerZone("KDH04", "Kedah", "Baling", 5.68, 100.92),
        PrayerZone("KDH05", "Kedah", "Bandar Baharu, Kulim", 5.30, 100.55),
        PrayerZone("KDH06", "Kedah", "Langkawi", 6.35, 99.80),
        PrayerZone("KDH07", "Kedah", "Puncak Gunung Jerai", null, null),
        PrayerZone("KTN01", "Kelantan", "Bachok, Kota Bharu, Machang, Pasir Mas, Pasir Puteh, Tanah Merah, Tumpat, Kuala Krai, Mukim Chiku", 6.00, 102.20),
        PrayerZone("KTN02", "Kelantan", "Gua Musang (Daerah Galas dan Bertam), Jeli, Jajahan Kecil Lojing", 5.00, 101.80),
        PrayerZone("MLK01", "Melaka", "Seluruh Negeri Melaka", 2.25, 102.25),
        PrayerZone("NGS01", "Negeri Sembilan", "Tampin, Jempol", 2.65, 102.35),
        PrayerZone("NGS02", "Negeri Sembilan", "Jelebu, Kuala Pilah, Rembau", 2.80, 102.15),
        PrayerZone("NGS03", "Negeri Sembilan", "Port Dickson, Seremban", 2.65, 101.85),
        PrayerZone("PHG01", "Pahang", "Pulau Tioman", 2.79, 104.17),
        PrayerZone("PHG02", "Pahang", "Kuantan, Pekan, Muadzam Shah", 3.60, 103.20),
        PrayerZone("PHG03", "Pahang", "Jerantut, Temerloh, Maran, Bera, Chenor, Jengka", 3.55, 102.45),
        PrayerZone("PHG04", "Pahang", "Bentong, Lipis, Raub", 3.85, 101.85),
        PrayerZone("PHG05", "Pahang", "Genting Sempah, Janda Baik, Bukit Tinggi", null, null),
        PrayerZone("PHG06", "Pahang", "Cameron Highlands, Genting Highlands, Bukit Fraser", null, null),
        PrayerZone("PHG07", "Pahang", "Zon Khas Daerah Rompin (Mukim Rompin, Endau, Pontian)", 2.80, 103.48),
        PrayerZone("PLS01", "Perlis", "Kangar, Padang Besar, Arau", 6.44, 100.20),
        PrayerZone("PNG01", "Pulau Pinang", "Seluruh Negeri Pulau Pinang", 5.40, 100.35),
        PrayerZone("PRK01", "Perak", "Tapah, Slim River, Tanjung Malim", 3.95, 101.40),
        PrayerZone("PRK02", "Perak", "Kuala Kangsar, Sg. Siput, Ipoh, Batu Gajah, Kampar", 4.60, 101.08),
        PrayerZone("PRK03", "Perak", "Lenggong, Pengkalan Hulu, Grik", 5.35, 101.10),
        PrayerZone("PRK04", "Perak", "Temengor, Belum", null, null),
        PrayerZone("PRK05", "Perak", "Kg Gajah, Teluk Intan, Bagan Datuk, Seri Iskandar, Beruas, Parit, Lumut, Sitiawan, Pulau Pangkor", 4.15, 100.85),
        PrayerZone("PRK06", "Perak", "Selama, Taiping, Bagan Serai, Parit Buntar", 4.95, 100.65),
        PrayerZone("PRK07", "Perak", "Bukit Larut", null, null),
        PrayerZone("SBH01", "Sabah", "Bahagian Sandakan (Timur), Bukit Garam, Semawang, Temanggong, Tambisan, Bandar Sandakan, Sukau", 5.84, 118.12),
        PrayerZone("SBH02", "Sabah", "Beluran, Telupid, Pinangah, Terusan, Kuamut, Bahagian Sandakan (Barat)", 5.60, 117.30),
        PrayerZone("SBH03", "Sabah", "Lahad Datu, Silabukan, Kunak, Sahabat, Semporna, Tungku, Bahagian Tawau (Timur)", 4.90, 118.40),
        PrayerZone("SBH04", "Sabah", "Bandar Tawau, Balong, Merotai, Kalabakan, Bahagian Tawau (Barat)", 4.35, 117.70),
        PrayerZone("SBH05", "Sabah", "Kudat, Kota Marudu, Pitas, Pulau Banggi, Bahagian Kudat", 6.70, 116.90),
        PrayerZone("SBH06", "Sabah", "Gunung Kinabalu", null, null),
        PrayerZone("SBH07", "Sabah", "Kota Kinabalu, Ranau, Kota Belud, Tuaran, Penampang, Papar, Putatan, Bahagian Pantai Barat", 5.95, 116.15),
        PrayerZone("SBH08", "Sabah", "Pensiangan, Keningau, Tambunan, Nabawan, Bahagian Pendalaman (Atas)", 5.10, 116.30),
        PrayerZone("SBH09", "Sabah", "Beaufort, Kuala Penyu, Sipitang, Tenom, Long Pasia, Membakut, Weston, Bahagian Pendalaman (Bawah)", 5.15, 115.70),
        PrayerZone("SGR01", "Selangor", "Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, Shah Alam", 3.05, 101.60),
        PrayerZone("SGR02", "Selangor", "Kuala Selangor, Sabak Bernam", 3.50, 101.15),
        PrayerZone("SGR03", "Selangor", "Klang, Kuala Langat", 2.95, 101.42),
        PrayerZone("SWK01", "Sarawak", "Limbang, Lawas, Sundar, Trusan", 4.60, 115.20),
        PrayerZone("SWK02", "Sarawak", "Miri, Niah, Bekenu, Sibuti, Marudi", 4.20, 114.10),
        PrayerZone("SWK03", "Sarawak", "Pandan, Belaga, Suai, Tatau, Sebauh, Bintulu", 3.00, 113.30),
        PrayerZone("SWK04", "Sarawak", "Sibu, Mukah, Dalat, Song, Igan, Oya, Balingian, Kanowit, Kapit", 2.30, 112.20),
        PrayerZone("SWK05", "Sarawak", "Sarikei, Matu, Julau, Rajang, Daro, Bintangor, Belawai", 2.10, 111.50),
        PrayerZone("SWK06", "Sarawak", "Lubok Antu, Sri Aman, Roban, Debak, Kabong, Lingga, Engkelili, Betong, Spaoh, Pusa, Saratok", 1.30, 111.50),
        PrayerZone("SWK07", "Sarawak", "Serian, Simunjan, Samarahan, Sebuyau, Meludam", 1.20, 110.70),
        PrayerZone("SWK08", "Sarawak", "Kuching, Bau, Lundu, Sematan", 1.55, 110.25),
        PrayerZone("SWK09", "Sarawak", "Zon Khas (Kampung Patarikan)", null, null),
        PrayerZone("TRG01", "Terengganu", "Kuala Terengganu, Marang, Kuala Nerus", 5.30, 103.10),
        PrayerZone("TRG02", "Terengganu", "Besut, Setiu", 5.70, 102.55),
        PrayerZone("TRG03", "Terengganu", "Hulu Terengganu", 5.00, 102.80),
        PrayerZone("TRG04", "Terengganu", "Dungun, Kemaman", 4.50, 103.35),
        PrayerZone("WLY01", "Wilayah Persekutuan", "Kuala Lumpur, Putrajaya", 3.14, 101.69),
        PrayerZone("WLY02", "Wilayah Persekutuan", "Labuan", 5.28, 115.24),
    )

    private val default = all.first { it.code == "WLY01" }

    fun byCode(code: String): PrayerZone = all.firstOrNull { it.code == code } ?: default

    /** Nearest zone within [MAX_KM], or null when the location is outside Malaysia. */
    fun nearest(lat: Double, lon: Double): PrayerZone? = all
        .filter { it.lat != null && it.lon != null }
        .map { it to distanceKm(lat, lon, it.lat!!, it.lon!!) }
        .minByOrNull { it.second }
        ?.takeIf { it.second <= MAX_KM }
        ?.first

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }

    private const val MAX_KM = 200.0
}
