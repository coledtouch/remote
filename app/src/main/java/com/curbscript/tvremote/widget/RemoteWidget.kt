package com.curbscript.tvremote.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.curbscript.tvremote.R
import com.curbscript.tvremote.control.Controller
import com.curbscript.tvremote.proto.RemoteKeyCode

private val CMD = ActionParameters.Key<String>("cmd")
private val TILE_BG = Color(0xCC15171C)
private val OK_BG = Color(0xE6FF7A4D)

data class AppEntry(val cmd: String, val icon: ImageProvider)

/**
 * Full-feature living-room widget: transparent background, dark translucent
 * control tiles, real installed app icons (brand-tile fallback), resizable to ~5x6.
 */
class RemoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val apps = loadAppIcons(context)
        provideContent { Content(apps) }
    }

    /** Real launcher icons where the phone has the app; brand-tile fallback otherwise. */
    private fun loadAppIcons(context: Context): List<AppEntry> {
        val pm = context.packageManager
        fun icon(pkg: String, fallback: Int): ImageProvider = try {
            ImageProvider(pm.getApplicationIcon(pkg).toBitmap(96, 96))
        } catch (e: Exception) {
            ImageProvider(fallback)
        }
        return listOf(
            AppEntry("app_tivimate", icon("ar.tvplayer.tv", R.drawable.ic_app_tivimate)),
            AppEntry("app_youtube", icon("com.google.android.youtube", R.drawable.ic_app_youtube)),
            AppEntry("app_netflix", icon("com.netflix.mediaclient", R.drawable.ic_app_netflix)),
            AppEntry("app_spotify", icon("com.spotify.music", R.drawable.ic_app_spotify))
        )
    }

    @Composable
    private fun Content(apps: List<AppEntry>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Ctl(R.drawable.ic_power, "power", GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_volume_down, "vol_down", GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_volume_up, "vol_up", GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_volume_mute, "mute", GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_input, "input", GlanceModifier.defaultWeight())
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Ctl(R.drawable.ic_back, "back", GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_dpad_up, "up", GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_home, "home", GlanceModifier.defaultWeight())
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Ctl(R.drawable.ic_dpad_left, "left", GlanceModifier.defaultWeight())
                Ok(GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_dpad_right, "right", GlanceModifier.defaultWeight())
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Ctl(R.drawable.ic_rewind, "rewind", GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_dpad_down, "down", GlanceModifier.defaultWeight())
                Ctl(R.drawable.ic_forward, "forward", GlanceModifier.defaultWeight())
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Ctl(R.drawable.ic_play_pause, "playpause", GlanceModifier.defaultWeight())
            }
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                apps.forEach { app -> AppButton(app.icon, app.cmd, GlanceModifier.defaultWeight()) }
            }
        }
    }

    @Composable
    private fun Ctl(iconRes: Int, cmd: String, modifier: GlanceModifier) {
        Box(
            modifier = modifier.fillMaxHeight().padding(3.dp).background(TILE_BG).cornerRadius(14.dp)
                .clickable(actionRunCallback<RemoteAction>(actionParametersOf(CMD to cmd))),
            contentAlignment = Alignment.Center
        ) {
            Image(provider = ImageProvider(iconRes), contentDescription = null,
                modifier = GlanceModifier.size(24.dp))
        }
    }

    @Composable
    private fun Ok(modifier: GlanceModifier) {
        Box(
            modifier = modifier.fillMaxHeight().padding(3.dp).background(OK_BG).cornerRadius(16.dp)
                .clickable(actionRunCallback<RemoteAction>(actionParametersOf(CMD to "ok"))),
            contentAlignment = Alignment.Center
        ) {
            Text("OK", style = TextStyle(color = ColorProvider(Color.White),
                fontWeight = FontWeight.Bold, fontSize = 15.sp))
        }
    }

    @Composable
    private fun AppButton(icon: ImageProvider, cmd: String, modifier: GlanceModifier) {
        Box(
            modifier = modifier.fillMaxHeight().padding(3.dp).cornerRadius(16.dp)
                .clickable(actionRunCallback<RemoteAction>(actionParametersOf(CMD to cmd))),
            contentAlignment = Alignment.Center
        ) {
            Image(provider = icon, contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

class RemoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemoteWidget()
}

class RemoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = Controller.get(context)
        when (parameters[CMD]) {
            "power" -> c.tvPowerToggle()
            "vol_up" -> c.tvVolumeUp()
            "vol_down" -> c.tvVolumeDown()
            "mute" -> c.tvMuteToggle()
            "input" -> c.tvCycleInput()
            "up" -> c.onnKey(RemoteKeyCode.KEYCODE_DPAD_UP)
            "down" -> c.onnKey(RemoteKeyCode.KEYCODE_DPAD_DOWN)
            "left" -> c.onnKey(RemoteKeyCode.KEYCODE_DPAD_LEFT)
            "right" -> c.onnKey(RemoteKeyCode.KEYCODE_DPAD_RIGHT)
            "ok" -> c.onnKey(RemoteKeyCode.KEYCODE_DPAD_CENTER)
            "back" -> c.onnKey(RemoteKeyCode.KEYCODE_BACK)
            "home" -> c.onnKey(RemoteKeyCode.KEYCODE_HOME)
            "playpause" -> c.onnKey(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE)
            "rewind" -> c.onnKey(RemoteKeyCode.KEYCODE_MEDIA_REWIND)
            "forward" -> c.onnKey(RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD)
            "app_tivimate" -> c.onnLaunch("market://launch?id=ar.tvplayer.tv")
            "app_youtube" -> c.onnLaunch("market://launch?id=com.google.android.youtube.tv")
            "app_netflix" -> c.onnLaunch("market://launch?id=com.netflix.ninja")
            "app_spotify" -> c.onnLaunch("market://launch?id=com.spotify.tv.android")
        }
    }
}
