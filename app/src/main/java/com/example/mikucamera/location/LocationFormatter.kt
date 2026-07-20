package com.example.mikucamera.location

import android.location.Address

/**
 * Formats reverse-geocoded addresses for watermark text.
 *
 * Default: 市 + 区/县
 *   → 沈阳市沈北新区
 *
 * With 门牌: 市 + 区/县 + 机构/POI 名（**绝不含道路名**）
 *   → 沈阳市沈北新区沈阳航空航天大学
 * Full line may be: 沈阳市沈北新区与民路沈阳航空航天大学
 *   → 与民路 must be dropped when 门牌 is on.
 */
object LocationFormatter {
    fun format(address: Address, includePoi: Boolean): String {
        val city = firstNonBlank(
            address.locality,
            address.subAdminArea?.takeIf { looksLikeCity(it) },
            address.adminArea
        )
        val district = firstNonBlank(
            address.subLocality,
            address.subAdminArea?.takeIf { !looksLikeCity(it) }
        )?.takeIf { it != city }

        val parts = mutableListOf<String>()
        if (city != null) parts += city
        if (district != null) parts += district

        if (includePoi) {
            val poi = pickPoiName(address, city, district)
            if (!poi.isNullOrBlank()) parts += poi
        }

        if (parts.isNotEmpty()) return parts.joinToString("")

        // Last resort: strip roads from the full line so we never show 与民路 alone.
        val line = address.getAddressLine(0)?.trim().orEmpty()
        return if (includePoi) stripRoads(line) else stripToCityDistrict(line, city, district)
    }

    private fun pickPoiName(address: Address, city: String?, district: String?): String? {
        val thoroughfare = address.thoroughfare?.trim()?.takeIf { it.isNotBlank() }
        val subThoroughfare = address.subThoroughfare?.trim()?.takeIf { it.isNotBlank() }

        // Never use road fields as POI (this is why 与民路 was showing).
        val rawCandidates = buildList {
            address.featureName?.let { add(it) }
            address.premises?.let { add(it) }
            // Scan address lines for a trailing institution name.
            for (i in 0 until address.maxAddressLineIndex.coerceAtLeast(0) + 1) {
                address.getAddressLine(i)?.let { add(it) }
            }
        }

        for (raw in rawCandidates) {
            var name = raw.trim()
            // Drop admin + road pieces that Geocoder often glues into featureName.
            listOfNotNull(city, district, thoroughfare, subThoroughfare).forEach { chunk ->
                if (name.contains(chunk)) name = name.replace(chunk, "")
            }
            name = stripRoads(name)
            name = name.trim().trimStart(',', '，', ' ', '、', '-', '—')
            if (isGoodPoi(name, city, district, thoroughfare)) return name
        }
        return null
    }

    /** Remove any “…路/街/道/巷…” road segments from a string. */
    private fun stripRoads(text: String): String {
        if (text.isBlank()) return text
        var s = text
        // e.g. 与民路、道义南大街、XX路19号
        s = s.replace(
            Regex("""[\u4e00-\u9fa5A-Za-z0-9]*?(?:路|街|道|巷|胡同|大街|大道)(?:\d+号?)?"""),
            ""
        )
        return s.trim()
    }

    private fun stripToCityDistrict(line: String, city: String?, district: String?): String {
        if (city != null && district != null) return city + district
        if (city != null) return city
        return stripRoads(line)
    }

    private fun isGoodPoi(
        name: String,
        city: String?,
        district: String?,
        thoroughfare: String?
    ): Boolean {
        if (name.isBlank()) return false
        if (name == city || name == district) return false
        if (thoroughfare != null && (name == thoroughfare || name.contains(thoroughfare))) return false
        if (isStreetLike(name) || isPureNumber(name)) return false
        // Still contains a road pattern → reject
        if (name.contains(Regex("""(?:路|街|道|巷|大街|大道)"""))) return false
        // Prefer names that look like places (university, plaza, center, …) or are long enough
        if (name.length < 2) return false
        return true
    }

    private fun isStreetLike(value: String): Boolean {
        if (value.matches(Regex("""^[\u4e00-\u9fa5A-Za-z0-9]*?(?:路|街|道|巷|胡同|大街|大道)(?:\d+号?)?$"""))) {
            return true
        }
        if (value.matches(Regex("""^\d+号?$"""))) return true
        return false
    }

    private fun isPureNumber(value: String): Boolean =
        value.matches(Regex("""^[\d\-号栋单元室]+$"""))

    private fun looksLikeCity(value: String): Boolean =
        value.endsWith("市") || value.endsWith("州") || value.endsWith("盟")

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
