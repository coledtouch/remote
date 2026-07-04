package com.curbscript.tvremote.iptv

/** Parses an M3U/M3U8 playlist into channels, and extracts the EPG (url-tvg) if present. */
object M3uParser {
    data class Result(val channels: List<IptvChannel>, val epgUrl: String?)

    fun parse(text: String): Result {
        val channels = ArrayList<IptvChannel>()
        var epgUrl: String? = null
        var pending: IptvChannel? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTM3U") -> epgUrl = attr(line, "url-tvg") ?: attr(line, "x-tvg-url")
                line.startsWith("#EXTINF") -> {
                    val name = line.substringAfterLast(",").trim()
                    val tvgId = attr(line, "tvg-id")
                    pending = IptvChannel(
                        id = tvgId ?: name,
                        name = name,
                        logo = attr(line, "tvg-logo"),
                        streamUrl = "",
                        epgId = tvgId,
                        group = attr(line, "group-title")
                    )
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    pending?.let { channels.add(it.copy(streamUrl = line)) }
                    pending = null
                }
            }
        }
        return Result(channels, epgUrl)
    }

    private fun attr(line: String, key: String): String? =
        Regex("$key=\"([^\"]*)\"").find(line)?.groupValues?.get(1)?.ifBlank { null }
}
