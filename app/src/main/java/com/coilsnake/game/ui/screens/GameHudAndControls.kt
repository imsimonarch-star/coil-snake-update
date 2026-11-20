package com.coilsnake.game.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coilsnake.game.game.GameConst
import com.coilsnake.game.game.GameUiState
import com.coilsnake.game.game.NetRole
import com.coilsnake.game.game.PlayMode
import com.coilsnake.game.game.PlayerHudSnapshot
import com.coilsnake.game.game.ScreenMode
import com.coilsnake.game.ui.components.ToyText
import com.coilsnake.game.ui.components.parseHexColor
import com.coilsnake.game.ui.theme.BungeeFontFamily
import com.coilsnake.game.ui.theme.CoilColors
import com.coilsnake.game.ui.theme.RubikFontFamily

@Composable
fun GameHudOverlay(
    ui: GameUiState,
    onToggleMute: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = ui.screen == ScreenMode.PLAY || ui.screen == ScreenMode.PAUSE
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompactH = maxHeight < 480.dp || maxWidth < 460.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = if (isCompactH) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    PlayerHudBlock(
                        player = ui.p1,
                        alignRight = false,
                        compact = isCompactH,
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isCompactH) 3.dp else 5.dp),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudIconButton(
                                icon = if (ui.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                description = if (ui.muted) "Turn sound on" else "Mute sound",
                                onClick = onToggleMute,
                                tag = "mute_button"
                            )
                            HudIconButton(
                                icon = Icons.Filled.Pause,
                                description = if (ui.net.role != NetRole.NONE) "Leave match" else "Pause",
                                onClick = onPause,
                                tag = "pause_button"
                            )
                        }
                        ToyText(
                            text = "Lv ${ui.level} · ${ui.themeName}",
                            fontSize = if (isCompactH) 10.sp else 11.sp,
                            strokeWidth = 5f,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (i in 0 until GameConst.APPLES_PER_LEVEL) {
                                val on = i < ui.applesLv
                                Box(
                                    modifier = Modifier
                                        .size(width = if (isCompactH) 10.dp else 12.dp, height = 5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (on) Color(0xFFFF5B5B) else CoilColors.Ink.copy(alpha = 0.55f))
                                        .border(
                                            1.dp,
                                            if (on) CoilColors.Ink else CoilColors.Cream.copy(alpha = 0.5f),
                                            RoundedCornerShape(3.dp)
                                        )
                                )
                            }
                        }
                    }

                    if (!ui.isSolo) {
                        PlayerHudBlock(
                            player = ui.p2,
                            alignRight = true,
                            compact = isCompactH,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHudBlock(
    player: PlayerHudSnapshot,
    alignRight: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val pColor = parseHexColor(player.colorHex)
    val horiz = if (alignRight) Alignment.End else Alignment.Start
    Column(
        modifier = modifier.alpha(if (player.isOut) 0.42f else 1f),
        horizontalAlignment = horiz,
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (!alignRight) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(pColor)
                        .border(1.5.dp, CoilColors.Ink, CircleShape)
                )
                Text(
                    text = player.name,
                    fontFamily = RubikFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = pColor
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (k in 0 until GameConst.HEARTS) {
                    val active = k < player.hearts
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = if (active) CoilColors.HeartRed else CoilColors.Cream.copy(alpha = 0.25f),
                        modifier = Modifier.size(if (compact) 12.dp else 14.dp)
                    )
                }
            }
            if (alignRight) {
                Text(
                    text = player.name,
                    fontFamily = RubikFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = pColor
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(pColor)
                        .border(1.5.dp, CoilColors.Ink, CircleShape)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (alignRight && player.comboMult >= 2) {
                ComboBadge(mult = player.comboMult, frac = player.comboFrac)
            }
            ToyText(
                text = "%,d".format(player.score),
                fontSize = if (compact) 19.sp else 24.sp,
                strokeWidth = if (compact) 6f else 7f
            )
            if (!alignRight && player.comboMult >= 2) {
                ComboBadge(mult = player.comboMult, frac = player.comboFrac)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (alignRight) {
                PowerRingsRow(player)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0 until GameConst.HOP_MAX) {
                    val full = k < player.hops
                    val frac = if (full) 1f else if (k == player.hops) player.hopCharge else 0f
                    HopPie(full = full, frac = frac, color = pColor)
                }
            }
            if (!alignRight) {
                PowerRingsRow(player)
            }
        }
    }
}

@Composable
private fun ComboBadge(mult: Int, frac: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ToyText(text = "×$mult", color = CoilColors.Gold, fontSize = 15.sp, strokeWidth = 5f)
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CoilColors.Ink.copy(alpha = 0.65f))
                .border(1.dp, CoilColors.Ink, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(CoilColors.Gold)
            )
        }
    }
}

@Composable
private fun HopPie(full: Boolean, frac: Float, color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        drawCircle(color = CoilColors.Ink.copy(alpha = 0.65f))
        if (full) {
            drawCircle(color = color)
        } else if (frac > 0.01f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = frac * 360f,
                useCenter = true
            )
        }
        drawCircle(color = CoilColors.Ink, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun PowerRingsRow(player: PlayerHudSnapshot) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (player.blazeFrac > 0f) PowerRing(player.blazeFrac, CoilColors.Blaze, Icons.Filled.Bolt, "Blaze")
        if (player.frostFrac > 0f) PowerRing(player.frostFrac, CoilColors.Frost, Icons.Filled.AcUnit, "Frost")
        if (player.ghostFrac > 0f) PowerRing(player.ghostFrac, CoilColors.Ghost, Icons.Filled.VisibilityOff, "Ghost")
    }
}

@Composable
private fun PowerRing(frac: Float, color: Color, icon: ImageVector, label: String) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .semantics { contentDescription = "$label power active" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = CoilColors.Ink.copy(alpha = 0.65f))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = frac * 360f,
                useCenter = true
            )
            drawCircle(color = CoilColors.Ink, style = Stroke(width = 2.dp.toPx()))
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CoilColors.Cream,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun HudIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(tag)
            .clip(RoundedCornerShape(12.dp))
            .background(CoilColors.Ink.copy(alpha = 0.65f))
            .border(1.5.dp, CoilColors.Cream.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = CoilColors.Cream,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun CountdownOverlay(ui: GameUiState) {
    if (!ui.showCount || ui.screen != ScreenMode.PLAY) return
    val scale = remember(ui.countKey) { Animatable(0.45f) }
    LaunchedEffect(ui.countKey) {
        scale.animateTo(
            targetValue = 1.05f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ToyText(
            text = ui.countText,
            fontSize = 86.sp,
            strokeWidth = 16f,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        )
    }
}

@Composable
fun LevelBannerOverlay(ui: GameUiState) {
    AnimatedVisibility(
        visible = ui.showBanner && ui.screen == ScreenMode.PLAY,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactH = maxHeight < 480.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(top = if (isCompactH) 62.dp else 108.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ToyText(
                        text = ui.bannerTitle,
                        fontSize = if (isCompactH) 28.sp else 36.sp,
                        strokeWidth = if (isCompactH) 9f else 11f,
                        textAlign = TextAlign.Center
                    )
                    if (ui.bannerSub.isNotEmpty()) {
                        ToyText(
                            text = ui.bannerSub,
                            color = CoilColors.Gold,
                            fontSize = if (isCompactH) 14.sp else 17.sp,
                            strokeWidth = 6f,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (ui.bannerBlurb.isNotEmpty()) {
                        Text(
                            text = ui.bannerBlurb,
                            fontFamily = RubikFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (isCompactH) 12.sp else 14.sp,
                            color = CoilColors.Cream,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HintPillOverlay(ui: GameUiState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = ui.showHint && ui.screen == ScreenMode.PLAY,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(CoilColors.Ink.copy(alpha = 0.78f))
                .border(1.5.dp, CoilColors.Cream.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = ui.hintText,
                fontFamily = RubikFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = CoilColors.Cream,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ToastPillOverlay(ui: GameUiState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = ui.showToast,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = 10.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(CoilColors.Ink.copy(alpha = 0.94f))
                .border(1.5.dp, CoilColors.Cream.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 9.dp)
        ) {
            Text(
                text = ui.toastText,
                fontFamily = RubikFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = CoilColors.Cream,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TouchControlsOverlay(
    ui: GameUiState,
    playMode: PlayMode,
    onSteer: (playerIdx: Int, dx: Int, dz: Int) -> Unit,
    onHop: (playerIdx: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (ui.screen != ScreenMode.PLAY) return

    val isLocalDual = playMode == PlayMode.LOCAL_MULTI && !ui.isSolo && ui.net.role == NetRole.NONE
    val primaryIdx = when {
        ui.isSolo -> 0
        ui.net.role == NetRole.HOST -> 0
        ui.net.role == NetRole.GUEST -> 1
        else -> ui.meIndex
    }
    val primaryPlayer = if (primaryIdx == 0) ui.p1 else ui.p2

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val isCompactPhone = maxHeight < 460.dp || maxWidth < 680.dp
        val isTablet = maxHeight >= 560.dp && maxWidth >= 820.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x550E0B22))
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = if (isCompactPhone) 12.dp else 18.dp, vertical = if (isCompactPhone) 8.dp else 12.dp)
        ) {
            if (!isLocalDual) {
                val padBtn = when {
                    isTablet -> 64.dp
                    isCompactPhone -> 50.dp
                    else -> 58.dp
                }
                val padGap = if (isCompactPhone) 5.dp else 7.dp
                val jumpDiam = when {
                    isTablet -> 114.dp
                    isCompactPhone -> 92.dp
                    else -> 104.dp
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    DPadCluster(
                        btnSize = padBtn,
                        gap = padGap,
                        accentColor = CoilColors.Cream,
                        onDir = { dx, dz -> onSteer(primaryIdx, dx, dz) }
                    )
                    JumpButton(
                        hops = primaryPlayer.hops,
                        diameter = jumpDiam,
                        accentColor = CoilColors.Gold,
                        bevelColor = CoilColors.GoldDark,
                        onClick = { onHop(primaryIdx) }
                    )
                }
            } else {
                val leftIdx = ui.meIndex
                val rightIdx = 1 - ui.meIndex
                val leftPlayer = if (leftIdx == 0) ui.p1 else ui.p2
                val rightPlayer = if (rightIdx == 0) ui.p1 else ui.p2
                val leftCol = parseHexColor(leftPlayer.colorHex)
                val rightCol = parseHexColor(rightPlayer.colorHex)
                val dualBtn = if (isTablet) 54.dp else 48.dp
                val dualGap = if (isTablet) 6.dp else 4.dp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${leftPlayer.name} (YOU)",
                                fontFamily = BungeeFontFamily,
                                fontSize = 10.sp,
                                color = leftCol
                            )
                            JumpMiniButton(
                                hops = leftPlayer.hops,
                                color = leftCol,
                                onClick = { onHop(leftIdx) },
                                tag = "jump_p${leftIdx + 1}"
                            )
                        }
                        DPadCluster(
                            btnSize = dualBtn,
                            gap = dualGap,
                            accentColor = leftCol.copy(alpha = 0.92f),
                            onDir = { dx, dz -> onSteer(leftIdx, dx, dz) }
                        )
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            JumpMiniButton(
                                hops = rightPlayer.hops,
                                color = rightCol,
                                onClick = { onHop(rightIdx) },
                                tag = "jump_p${rightIdx + 1}"
                            )
                            Text(
                                text = rightPlayer.name,
                                fontFamily = BungeeFontFamily,
                                fontSize = 10.sp,
                                color = rightCol
                            )
                        }
                        DPadCluster(
                            btnSize = dualBtn,
                            gap = dualGap,
                            accentColor = rightCol.copy(alpha = 0.92f),
                            onDir = { dx, dz -> onSteer(rightIdx, dx, dz) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DPadCluster(
    btnSize: Dp,
    gap: Dp,
    accentColor: Color,
    onDir: (Int, Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        DPadBtn(
            icon = Icons.Filled.KeyboardArrowUp,
            label = "Up",
            size = btnSize,
            bgColor = accentColor,
            onPress = { onDir(0, -1) },
            tag = "dpad_up"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            DPadBtn(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = "Left",
                size = btnSize,
                bgColor = accentColor,
                onPress = { onDir(-1, 0) },
                tag = "dpad_left"
            )
            DPadBtn(
                icon = Icons.Filled.KeyboardArrowDown,
                label = "Down",
                size = btnSize,
                bgColor = accentColor,
                onPress = { onDir(0, 1) },
                tag = "dpad_down"
            )
            DPadBtn(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                label = "Right",
                size = btnSize,
                bgColor = accentColor,
                onPress = { onDir(1, 0) },
                tag = "dpad_right"
            )
        }
    }
}

@Composable
private fun DPadBtn(
    icon: ImageVector,
    label: String,
    size: Dp,
    bgColor: Color,
    onPress: () -> Unit,
    tag: String
) {
    var pressed by remember { mutableStateOf(false) }
    val bevelColor = Color(0xFFB9A98D)
    Box(
        modifier = Modifier
            .size(size)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(tag)
            .drawBehind {
                val r = 16.dp.toPx()
                drawRoundRect(
                    color = bevelColor,
                    topLeft = Offset(0f, 5.dp.toPx()),
                    size = this.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            .offset(y = if (pressed) 4.dp else 0.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor.copy(alpha = 0.94f))
            .pointerInput(onPress) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPress()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = CoilColors.Ink,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
private fun JumpButton(
    hops: Int,
    diameter: Dp,
    accentColor: Color,
    bevelColor: Color,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val empty = hops <= 0
    val fill = if (empty) Color(0xFFC9B89A) else accentColor
    val bevel = if (empty) Color(0xFF8D7D62) else bevelColor

    Box(
        modifier = Modifier
            .size(diameter)
            .testTag("jump_button")
            .drawBehind {
                drawCircle(
                    color = bevel,
                    center = Offset(size.width / 2f, size.height / 2f + 6.dp.toPx())
                )
            }
            .offset(y = if (pressed) 5.dp else 0.dp)
            .clip(CircleShape)
            .background(fill)
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onClick()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = "Jump",
                tint = CoilColors.Ink,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "JUMP",
                fontFamily = BungeeFontFamily,
                fontSize = 15.sp,
                color = CoilColors.Ink
            )
            Text(
                text = "×$hops",
                fontFamily = RubikFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = CoilColors.Ink.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun JumpMiniButton(
    hops: Int,
    color: Color,
    onClick: () -> Unit,
    tag: String
) {
    var pressed by remember { mutableStateOf(false) }
    val empty = hops <= 0
    val fill = if (empty) Color(0xFFC9B89A) else color
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
            .testTag(tag)
            .drawBehind {
                val r = 24.dp.toPx()
                drawRoundRect(
                    color = CoilColors.Ink,
                    topLeft = Offset(0f, 3.dp.toPx()),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
            .offset(y = if (pressed) 2.dp else 0.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onClick()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "JUMP ×$hops",
            fontFamily = BungeeFontFamily,
            fontSize = 12.sp,
            color = CoilColors.Ink
        )
    }
}
