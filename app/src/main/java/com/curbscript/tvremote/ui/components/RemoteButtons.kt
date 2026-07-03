package com.curbscript.tvremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curbscript.tvremote.data.AppShortcut
import com.curbscript.tvremote.ui.RemoteColors
import androidx.compose.foundation.clickable

/** A tactile key: press-scale animation + light haptic tick. */
@Composable
fun KeyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    shape: Shape = CircleShape,
    background: Color = RemoteColors.surfaceHi,
    borderColor: Color = RemoteColors.border,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, label = "press")
    val bg by animateColorAsState(
        if (pressed) background.copy(alpha = 0.75f) else background, label = "bg"
    )
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun IconKey(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    shape: Shape = CircleShape,
    background: Color = RemoteColors.surfaceHi,
    borderColor: Color = RemoteColors.border,
    tint: Color = RemoteColors.onSurface,
    iconSize: Dp = 26.dp
) {
    KeyButton(onClick, modifier, size, shape, background, borderColor) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** An icon key with a small caption underneath. */
@Composable
fun LabeledIconKey(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    background: Color = RemoteColors.surfaceHi,
    tint: Color = RemoteColors.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        IconKey(icon, onClick, size = size, background = background, tint = tint)
        Text(
            label,
            color = RemoteColors.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = RemoteColors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = modifier
    )
}

/** Circular directional pad with a raised OK center. */
@Composable
fun DPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 250.dp
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(RemoteColors.surface)
            .border(1.dp, RemoteColors.border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val edge = 4.dp
        IconKey(
            Icons.Rounded.KeyboardArrowUp, onUp,
            Modifier.align(Alignment.TopCenter).padding(top = edge),
            size = 62.dp, background = Color.Transparent, borderColor = Color.Transparent,
            tint = RemoteColors.onSurface, iconSize = 34.dp
        )
        IconKey(
            Icons.Rounded.KeyboardArrowDown, onDown,
            Modifier.align(Alignment.BottomCenter).padding(bottom = edge),
            size = 62.dp, background = Color.Transparent, borderColor = Color.Transparent,
            tint = RemoteColors.onSurface, iconSize = 34.dp
        )
        IconKey(
            Icons.Rounded.KeyboardArrowLeft, onLeft,
            Modifier.align(Alignment.CenterStart).padding(start = edge),
            size = 62.dp, background = Color.Transparent, borderColor = Color.Transparent,
            tint = RemoteColors.onSurface, iconSize = 34.dp
        )
        IconKey(
            Icons.Rounded.KeyboardArrowRight, onRight,
            Modifier.align(Alignment.CenterEnd).padding(end = edge),
            size = 62.dp, background = Color.Transparent, borderColor = Color.Transparent,
            tint = RemoteColors.onSurface, iconSize = 34.dp
        )
        KeyButton(
            onOk,
            size = 96.dp,
            shape = CircleShape,
            background = RemoteColors.surfaceHi,
            borderColor = RemoteColors.accent
        ) {
            Text("OK", color = RemoteColors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
fun AppTile(shortcut: AppShortcut, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        KeyButton(
            onClick,
            size = 62.dp,
            shape = RoundedCornerShape(18.dp),
            background = shortcut.brand,
            borderColor = Color.Transparent
        ) {
            Text(shortcut.glyph, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        Text(
            shortcut.label,
            color = RemoteColors.muted,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun StatusChip(label: String, connected: Boolean, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(RemoteColors.surface)
            .border(1.dp, RemoteColors.border, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (connected) RemoteColors.good else RemoteColors.muted)
        )
        Text(label, color = RemoteColors.onSurface, fontSize = 12.sp)
        Text(
            if (connected) "Ready" else "Off",
            color = if (connected) RemoteColors.good else RemoteColors.muted,
            fontSize = 12.sp
        )
    }
}
