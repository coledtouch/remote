package com.curbscript.tvremote.iptv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/** Streams an XMLTV EPG. Keeps only programmes ending in (roughly) the future. */
object XmltvParser {
    fun parse(input: InputStream, now: Long): Map<String, MutableList<IptvProgram>> {
        val map = HashMap<String, MutableList<IptvProgram>>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        var channel: String? = null
        var start = 0L
        var stop = 0L
        var title: String? = null
        var desc: String? = null
        var inProg = false
        var text = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "programme") {
                        inProg = true
                        channel = parser.getAttributeValue(null, "channel")
                        start = parseTime(parser.getAttributeValue(null, "start"))
                        stop = parseTime(parser.getAttributeValue(null, "stop"))
                        title = null
                        desc = null
                    }
                    text = ""
                }
                XmlPullParser.TEXT -> text = parser.text ?: ""
                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> if (inProg && title == null) title = text.trim()
                    "desc" -> if (inProg && desc == null) desc = text.trim()
                    "programme" -> {
                        val ch = channel
                        if (inProg && ch != null && title != null && stop >= now - 3_600_000L) {
                            map.getOrPut(ch) { ArrayList() }.add(IptvProgram(title!!, start, stop, desc))
                        }
                        inProg = false
                    }
                }
            }
            event = parser.next()
        }
        map.values.forEach { list -> list.sortBy { it.start } }
        return map
    }

    private val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        return try {
            val t = s.trim()
            val norm = if (t.length >= 15 && t[14] == ' ') t
            else if (t.length >= 14) t.substring(0, 14) + " +0000" else t
            fmt.parse(norm)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }
}
