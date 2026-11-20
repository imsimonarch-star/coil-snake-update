package com.coilsnake.game.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coilsnake.game.game.SKIN_LIST
import com.coilsnake.game.ui.theme.BungeeFontFamily
import com.coilsnake.game.ui.theme.CoilColors
import com.coilsnake.game.ui.theme.RubikFontFamily

fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return CoilColors.Teal
    return Color((0xFF000000L or value).toInt())
}

@Composable
fun ToyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CoilColors.Cream,
    fontSize: TextUnit = 24.sp,
    textAlign: TextAlign = TextAlign.Start,
    strokeWidth: Float = 8f,
    letterSpacing: TextUnit = 0.sp,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val baseStyle = TextStyle(
        fontFamily = BungeeFontFamily,
        fontSize = fontSize,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        textAlign = textAlign
    )
    Box(modifier = modifier) {
        Text(
            text = text,
            style = baseStyle.copy(
                color = CoilColors.Ink,
                drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round),
                shadow = Shadow(
                    color = Color(0x70000000),
                    offset = Offset(0f, strokeWidth * 0.7f),
                    blurRadius = 6f
                )
            )
        )
        Text(
            text = text,
            style = baseStyle.copy(color = color)
        )
    }
}

@Composable
fun LogoTitle(
    compact: Boolean,
    modifier: Modifier = Modifier,
    ultraCompact: Boolean = false
) {
    val mainSize = when {
        ultraCompact -> 34.sp
        compact -> 44.sp
        else -> 64.sp
    }
    val subSize = when {
        ultraCompact -> 16.sp
        compact -> 20.sp
        else -> 29.sp
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Box {
            val style = TextStyle(
                fontFamily = BungeeFontFamily,
                fontSize = mainSize,
                lineHeight = mainSize * 0.9f,
                letterSpacing = 2.sp
            )
            Text(
                text = "COIL",
                style = style.copy(
                    color = CoilColors.Ink,
                    drawStyle = Stroke(width = 14f, join = StrokeJoin.Round),
                    shadow = Shadow(
                        color = Color(0xFF0B625A),
                        offset = Offset(0f, 10f),
                        blurRadius = 0f
                    )
                )
            )
            Text(
                text = "COIL",
                style = style.copy(
                    color = CoilColors.Cream,
                    shadow = Shadow(
                        color = Color(0xFF149283),
                        offset = Offset(0f, 5f),
                        blurRadius = 0f
                    )
                )
            )
        }
        Box(modifier = Modifier.offset(y = (-4).dp)) {
            val style = TextStyle(
                fontFamily = BungeeFontFamily,
                fontSize = subSize,
                letterSpacing = 6.sp
            )
            Text(
                text = "SNAKE",
                style = style.copy(
                    color = CoilColors.Ink,
                    drawStyle = Stroke(width = 10f, join = StrokeJoin.Round),
                    shadow = Shadow(
                        color = Color(0x88000000),
                        offset = Offset(0f, 8f),
                        blurRadius = 8f
                    )
                )
            )
            Text(
                text = "SNAKE",
                style = style.copy(
                    color = CoilColors.Gold,
                    shadow = Shadow(
                        color = Color(0xFFA56C08),
                        offset = Offset(0f, 4f),
                        blurRadius = 0f
                    )
                )
            )
        }
    }
}

@Composable
fun ToyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = CoilColors.Gold,
    bevelColor: Color = CoilColors.GoldDark,
    contentColor: Color = CoilColors.Ink,
    enabled: Boolean = true,
    tag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val yOffset by animateDpAsState(targetValue = if (pressed && enabled) 3.dp else 0.dp, label = "btnPress")

    Box(
        modifier = modifier
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = 48.dp)
            .drawBehind {
                val r = 16.dp.toPx()
                drawRoundRect(
                    color = bevelColor,
                    topLeft = Offset(0f, 5.dp.toPx()),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            .offset(y = yOffset)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = BungeeFontFamily,
            fontSize = 17.sp,
            color = contentColor
        )
    }
}

@Composable
fun ModeCard(
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    val bgColor = if (selected) CoilColors.Cream.copy(alpha = 0.14f) else CoilColors.Cream.copy(alpha = 0.07f)
    val borderW = if (selected) 2.5.dp else 1.5.dp
    val borderCol = if (selected) accentColor else accentColor.copy(alpha = 0.65f)

    Surface(
        modifier = modifier
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
            .defaultMinSize(minHeight = 48.dp)
            .alpha(if (selected) 1f else 0.78f)
            .then(
                if (selected) Modifier.shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = accentColor, spotColor = accentColor)
                else Modifier
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(borderW, borderCol)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkinSwatchRow(
    whoLabel: String,
    playerColor: Color,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val selectedSkin = SKIN_LIST.getOrElse(selectedIdx) { SKIN_LIST[0] }
    val swatchSize = if (compact) 24.dp else 28.dp
    val swatchGap = if (compact) 6.dp else 8.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = whoLabel,
                fontFamily = BungeeFontFamily,
                fontSize = if (compact) 12.sp else 13.sp,
                color = playerColor
            )
            Text(
                text = selectedSkin.name,
                fontFamily = RubikFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 11.sp else 12.sp,
                color = CoilColors.Cream.copy(alpha = 0.8f)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(swatchGap),
            verticalArrangement = Arrangement.spacedBy(swatchGap)
        ) {
            SKIN_LIST.forEachIndexed { idx, skin ->
                val isSelected = idx == selectedIdx
                val body = parseHexColor(skin.bodyHex)
                val band = parseHexColor(skin.bandHex)
                val head = parseHexColor(skin.headHex)
                Box(
                    modifier = Modifier
                        .size(swatchSize)
                        .testTag("skin_swatch_${whoLabel}_$idx")
                        .semantics { contentDescription = "$whoLabel skin ${skin.name}" }
                        .then(
                            if (isSelected) {
                                Modifier
                                    .shadow(10.dp, CircleShape, ambientColor = body, spotColor = body)
                                    .border(2.5.dp, CoilColors.Cream, CircleShape)
                            } else {
                                Modifier.border(1.5.dp, CoilColors.Cream.copy(alpha = 0.25f), CircleShape)
                            }
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(head, body, band),
                                center = Offset(18f, 16f),
                                radius = 48f
                            )
                        )
                        .clickable { onSelect(idx) }
                )
            }
        }
    }
}

@Composable
fun DockActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    bevelColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    tag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val yOffset by animateDpAsState(targetValue = if (pressed) 3.dp else 0.dp, label = "dockPress")

    Box(
        modifier = modifier
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
            .defaultMinSize(minHeight = 48.dp)
            .drawBehind {
                val r = (if (compact) 14.dp else 18.dp).toPx()
                drawRoundRect(
                    color = bevelColor,
                    topLeft = Offset(0f, 4.dp.toPx()),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            .offset(y = yOffset)
            .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = if (compact) 6.dp else 9.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    fontFamily = BungeeFontFamily,
                    fontSize = 11.sp,
                    color = contentColor,
                    maxLines = 1
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    fontFamily = BungeeFontFamily,
                    fontSize = 12.sp,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}
