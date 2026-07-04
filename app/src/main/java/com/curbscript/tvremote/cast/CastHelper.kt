package com.curbscript.tvremote.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.images.WebImage

/** Thin wrapper over the Cast SDK; degrades gracefully when Cast/Play Services is unavailable. */
object CastHelper {
    fun context(ctx: Context): CastContext? =
        try { CastContext.getSharedInstance(ctx.applicationContext) } catch (_: Exception) { null }

    fun isConnected(ctx: Context): Boolean =
        context(ctx)?.sessionManager?.currentCastSession?.isConnected == true

    fun cast(ctx: Context, url: String, title: String, logo: String?): Boolean {
        val client = context(ctx)?.sessionManager?.currentCastSession?.remoteMediaClient ?: return false
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            if (!logo.isNullOrBlank()) addImage(WebImage(Uri.parse(logo)))
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(if (url.endsWith(".m3u8")) "application/x-mpegURL" else "video/mp2t")
            .setMetadata(meta)
            .build()
        return try {
            client.load(MediaLoadRequestData.Builder().setMediaInfo(info).build())
            true
        } catch (_: Exception) { false }
    }
}
