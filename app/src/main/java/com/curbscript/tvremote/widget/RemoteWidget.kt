package com.curbscript.tvremote.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.curbscript.tvremote.control.Controller

/**
 * Compact home-screen widget: TV power / volume / mute plus two quick app launches.
 * Each tap dispatches directly through the shared [Controller].
 */
class RemoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }

    @Composable
    private fun WidgetContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF16181D))
                .cornerRadius(22.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WidgetButton("Power", Color(0xFFFF5A5F), Color.White,
                    actionRunCallback<PowerAction>(), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                WidgetButton("Vol −", Color(0xFF1F232B), Color.White,
                    actionRunCallback<VolDownAction>(), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                WidgetButton("Vol +", Color(0xFF1F232B), Color.White,
                    actionRunCallback<VolUpAction>(), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                WidgetButton("Mute", Color(0xFF1F232B), Color.White,
                    actionRunCallback<MuteAction>(), GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WidgetButton("YouTube", Color(0xFFFF0033), Color.White,
                    actionRunCallback<YouTubeAction>(), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                WidgetButton("Netflix", Color(0xFFE50914), Color.White,
                    actionRunCallback<NetflixAction>(), GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun WidgetButton(
    label: String,
    bg: Color,
    fg: Color,
    action: androidx.glance.action.Action,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .background(bg)
            .cornerRadius(14.dp)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(fg),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

class RemoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemoteWidget()
}

// ---- Action callbacks ----

class PowerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Controller.get(context).tvPowerToggle()
    }
}

class VolUpAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Controller.get(context).tvVolumeUp()
    }
}

class VolDownAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Controller.get(context).tvVolumeDown()
    }
}

class MuteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Controller.get(context).tvMuteToggle()
    }
}

class YouTubeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Controller.get(context).onnLaunch("https://www.youtube.com")
    }
}

class NetflixAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Controller.get(context).onnLaunch("https://www.netflix.com/title")
    }
}
