package com.vidma.downloader.ui.components.core

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.vidma.downloader.ui.theme.LocalVidmaPalette
import com.vidma.downloader.ui.theme.VidmaBase
import com.vidma.downloader.ui.theme.VidmaPalette

/**
 * GlassTextField — borderless frosted input with animated focus glow.
 * Used for URL entry, browser address bar, search etc.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    corner: Dp = 18.dp,
    fillWidth: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = VidmaBase.TextHigh),
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val shape = RoundedCornerShape(corner)
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) palette.primary.copy(alpha = 0.75f) else VidmaBase.GlassStroke,
        label = "focus",
    )
    val fillAlpha by animateColorAsState(
        targetValue = if (focused) 0.085f else 0.05f,
        label = "fill",
    )

    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .background(Color.White.copy(alpha = fillAlpha), shape)
            .border(
                width = 1.4.dp,
                brush = Brush.linearGradient(
                    listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.5f),
                    )
                ),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (focused) palette.secondary else VidmaBase.TextLow,
                    modifier = Modifier.requiredSize(21.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = singleLine,
                textStyle = textStyle,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                cursorBrush = SolidColor(palette.secondary),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle.copy(color = VidmaBase.TextLow),
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/**
 * VidmaChoiceChip — selectable gradient pill used for quality/format picking.
 */
@Composable
fun VidmaChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(
        targetValue = if (selected) palette.primary.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.05f),
        label = "chip",
    )
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .background(bg, shape)
            .border(
                width = if (selected) 1.4.dp else 1.dp,
                brush = if (selected) {
                    Brush.linearGradient(listOf(palette.secondary.copy(alpha = 0.9f), palette.primary, palette.tertiary.copy(alpha = 0.8f)))
                } else {
                    Brush.linearGradient(listOf(VidmaBase.GlassStroke, VidmaBase.GlassStrokeSoft))
                },
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (selected) Color.White else VidmaBase.TextHigh,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (selected) palette.secondary else VidmaBase.TextLow,
                ),
            )
        }
    }
}

/**
 * ModeToggle — two-option (Video / Audio) segmented switcher inside glass.
 */
@Composable
fun VidmaModeToggle(
    options: List<Pair<String, Boolean>>, // label to selected
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    palette: VidmaPalette = LocalVidmaPalette.current,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.05f), shape)
            .border(1.dp, VidmaBase.GlassStrokeSoft, shape)
            .padding(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (label, selected) ->
                val bg by animateColorAsState(
                    targetValue = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                    label = "seg",
                )
                val shapeSeg = RoundedCornerShape(14.dp)
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(bg, shapeSeg)
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            brush = Brush.linearGradient(
                                listOf(palette.primary.copy(alpha = 0.65f), palette.secondary.copy(alpha = 0.4f)),
                            ),
                            shape = shapeSeg,
                        )
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (selected) palette.secondary else VidmaBase.TextMid,
                        ),
                    )
                }
            }
        }
    }
}
