package com.curbscript.tvremote.iptv

data class IptvChannel(
    val id: String,
    val name: String,
    val logo: String?,
    val streamUrl: String,
    val epgId: String?,
    val group: String?
)

data class IptvProgram(
    val title: String,
    val start: Long,
    val stop: Long,
    val desc: String?
)

data class IptvSettings(
    val type: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = ""
) {
    val configured: Boolean
        get() = (type == "xtream" && server.isNotBlank()) || (type == "m3u" && m3uUrl.isNotBlank())
}
