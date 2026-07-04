package com.curbscript.tvremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import com.curbscript.tvremote.iptv.IptvChannel
import com.curbscript.tvremote.ui.components.IconKey

@Composable
fun GuideScreen(vm: RemoteViewModel, onBack: () -> Unit, onPlay: (IptvChannel) -> Unit) {
    val channels by vm.channels.collectAsState()
    LaunchedEffect(Unit) { if (channels.isEmpty()) vm.loadIptv() }
    val query = vm.guideQuery
    val filtered = if (query.isBlank()) channels else channels.filter { it.name.contains(query, ignoreCase = true) }

    Box(Modifier.fillMaxSize().background(RemoteColors.bg)) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(26.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconKey(Icons.AutoMirrored.Rounded.ArrowBack, onBack, size = 44.dp,
                    background = RemoteColors.surface, tint = RemoteColors.onSurface, iconSize = 20.dp)
                Text("TV Guide", color = RemoteColors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconKey(Icons.Rounded.Refresh, { vm.loadIptv() }, size = 44.dp,
                    background = RemoteColors.surface, tint = RemoteColors.muted, iconSize = 20.dp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = { vm.updateGuideQuery(it) },
                placeholder = { Text("Search channels") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RemoteColors.coral, unfocusedBorderColor = RemoteColors.border,
                    focusedTextColor = RemoteColors.onSurface, unfocusedTextColor = RemoteColors.onSurface,
                    cursorColor = RemoteColors.coral
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(10.dp))
            when {
                vm.iptvLoading && channels.isEmpty() ->
                    Text("Loading channels…", color = RemoteColors.muted, fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp))
                channels.isEmpty() ->
                    Text("No channels yet. Add your IPTV provider (Xtream or M3U) in Setup, then refresh.",
                        color = RemoteColors.muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(filtered) { ch -> ChannelRow(ch, vm.nowNext(ch), { onPlay(ch) }) }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    ch: IptvChannel,
    nowNext: Pair<com.curbscript.tvremote.iptv.IptvProgram?, com.curbscript.tvremote.iptv.IptvProgram?>,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp))
            .background(RemoteColors.surface).border(1.dp, RemoteColors.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(RemoteColors.surfaceHi),
            contentAlignment = Alignment.Center
        ) {
            if (ch.logo != null) {
                AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Text(ch.name.take(1).uppercase(), color = RemoteColors.muted, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.name, color = RemoteColors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val now = nowNext.first
            val next = nowNext.second
            Text(
                now?.title ?: "No program info",
                color = if (now != null) RemoteColors.coral else RemoteColors.muted, fontSize = 13.sp, maxLines = 1
            )
            if (next != null) {
                Text("Next: ${next.title}", color = RemoteColors.muted, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
