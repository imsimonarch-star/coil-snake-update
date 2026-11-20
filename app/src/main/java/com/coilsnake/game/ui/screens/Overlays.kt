package com.coilsnake.game.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coilsnake.game.R
import com.coilsnake.game.game.GameConst
import com.coilsnake.game.game.GameUiState
import com.coilsnake.game.game.NetRole
import com.coilsnake.game.game.PlayerHudSnapshot
import com.coilsnake.game.game.ScreenMode
import com.coilsnake.game.game.TitleSubView
import com.coilsnake.game.ui.components.DockActionButton
import com.coilsnake.game.ui.components.LogoTitle
import com.coilsnake.game.ui.components.ModeCard
import com.coilsnake.game.ui.components.SkinSwatchRow
import com.coilsnake.game.ui.components.ToyButton
import com.coilsnake.game.ui.components.ToyText
import com.coilsnake.game.ui.components.parseHexColor
import com.coilsnake.game.ui.theme.BungeeFontFamily
import com.coilsnake.game.ui.theme.CoilColors
import com.coilsnake.game.ui.theme.RubikFontFamily

@Composable
fun TitleScreenOverlay(
    ui: GameUiState,
    onChooseMode: (Int) -> Unit,
    onOpenNet: () -> Unit,
    onBackToMode: () -> Unit,
    onSetMe: (Int) -> Unit,
    onSetSkin: (Int, Int) -> Unit,
    onStartMatch: () -> Unit,
    onNetHost: () -> Unit,
    onNetJoinPick: () -> Unit,
    onJoinIpChange: (String) -> Unit,
    onNetConnect: () -> Unit,
    onNetStart: () -> Unit
) {
    AnimatedVisibility(
        visible = ui.screen == ScreenMode.TITLE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscapeSplit = maxWidth >= 500.dp && maxWidth > maxHeight
            val isCompactH = maxHeight < 480.dp
            val isUltraCompactH = maxHeight < 395.dp
            val dockSpace = if (isCompactH) 62.dp else 82.dp
            val leftColMaxW = if (maxWidth >= 900.dp) 440.dp else 360.dp

            val bgBrush = if (isLandscapeSplit) {
                Brush.horizontalGradient(
                    0.0f to CoilColors.InkOverlay,
                    0.38f to Color(0xB30E0B22),
                    0.62f to Color.Transparent
                )
            } else {
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.34f to Color.Transparent,
                    0.50f to Color(0xCC0E0B22),
                    0.72f to CoilColors.InkOverlay
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgBrush)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(bottom = dockSpace)
                    .padding(horizontal = if (isCompactH) 14.dp else 20.dp, vertical = if (isCompactH) 8.dp else 14.dp),
                contentAlignment = if (isLandscapeSplit) Alignment.CenterStart else Alignment.BottomCenter
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .then(
                            if (isLandscapeSplit) Modifier.widthIn(max = leftColMaxW)
                            else Modifier.widthIn(max = 520.dp).fillMaxWidth()
                        )
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactH) 8.dp else 12.dp)
                ) {
                    if (isLandscapeSplit || ui.titleView == TitleSubView.MODE) {
                        LogoTitle(
                            compact = !isLandscapeSplit || isCompactH,
                            ultraCompact = isUltraCompactH
                        )
                        if (isLandscapeSplit && !isCompactH) {
                            Text(
                                text = stringResource(R.string.tagline),
                                fontFamily = RubikFontFamily,
                                fontSize = 14.sp,
                                color = CoilColors.Cream.copy(alpha = 0.9f)
                            )
                        }
                    }

                    when (ui.titleView) {
                        TitleSubView.MODE -> {
                            Text(
                                text = stringResource(R.string.choose_mode).uppercase(),
                                fontFamily = BungeeFontFamily,
                                fontSize = 12.sp,
                                letterSpacing = 1.4.sp,
                                color = CoilColors.Cream.copy(alpha = 0.85f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ModeCard(
                                    selected = ui.modeSel == 0,
                                    accentColor = CoilColors.Gold,
                                    onClick = { onChooseMode(0) },
                                    modifier = Modifier.weight(1f),
                                    tag = "mode_single"
                                ) {
                                    Text(
                                        text = stringResource(R.string.mode_single_title),
                                        fontFamily = BungeeFontFamily,
                                        fontSize = 15.sp,
                                        color = CoilColors.Gold
                                    )
                                    Text(
                                        text = stringResource(R.string.mode_single_desc),
                                        fontFamily = RubikFontFamily,
                                        fontSize = 12.sp,
                                        color = CoilColors.Cream.copy(alpha = 0.9f)
                                    )
                                }
                                ModeCard(
                                    selected = ui.modeSel == 1,
                                    accentColor = CoilColors.Teal,
                                    onClick = { onChooseMode(1) },
                                    modifier = Modifier.weight(1f),
                                    tag = "mode_local_multi"
                                ) {
                                    Text(
                                        text = stringResource(R.string.mode_local_title),
                                        fontFamily = BungeeFontFamily,
                                        fontSize = 15.sp,
                                        color = CoilColors.Teal
                                    )
                                    Text(
                                        text = stringResource(R.string.mode_local_desc),
                                        fontFamily = RubikFontFamily,
                                        fontSize = 12.sp,
                                        color = CoilColors.Cream.copy(alpha = 0.9f)
                                    )
                                }
                            }
                            ModeCard(
                                selected = false,
                                accentColor = CoilColors.Coral,
                                onClick = onOpenNet,
                                modifier = Modifier.fillMaxWidth(),
                                tag = "mode_phone_vs_phone"
                            ) {
                                Text(
                                    text = stringResource(R.string.mode_net_title),
                                    fontFamily = BungeeFontFamily,
                                    fontSize = 15.sp,
                                    color = CoilColors.Coral
                                )
                                Text(
                                    text = stringResource(R.string.mode_net_desc),
                                    fontFamily = RubikFontFamily,
                                    fontSize = 12.sp,
                                    color = CoilColors.Cream.copy(alpha = 0.9f)
                                )
                            }
                            Text(
                                text = "Best %,d".format(ui.bestScore),
                                fontFamily = RubikFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = CoilColors.Cream.copy(alpha = 0.8f)
                            )
                        }

                        TitleSubView.SOLO -> {
                            Text(
                                text = "SINGLE PLAYER · PICK YOUR SKIN",
                                fontFamily = BungeeFontFamily,
                                fontSize = 12.sp,
                                letterSpacing = 1.2.sp,
                                color = CoilColors.Cream.copy(alpha = 0.85f)
                            )
                            SkinSwatchRow(
                                whoLabel = "Your skin",
                                playerColor = parseHexColor(ui.p1.colorHex),
                                selectedIdx = ui.skinP1,
                                onSelect = { onSetSkin(0, it) },
                                compact = isCompactH
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ToyButton(
                                    text = stringResource(R.string.btn_start),
                                    onClick = onStartMatch,
                                    tag = "solo_start_button"
                                )
                                ToyButton(
                                    text = stringResource(R.string.btn_back),
                                    onClick = onBackToMode,
                                    containerColor = CoilColors.Cream,
                                    bevelColor = Color(0xFFBBA98A),
                                    tag = "solo_back_button"
                                )
                                Text(
                                    text = "Best %,d".format(ui.bestScore),
                                    fontFamily = RubikFontFamily,
                                    fontSize = 13.sp,
                                    color = CoilColors.Cream.copy(alpha = 0.8f)
                                )
                            }
                        }

                        TitleSubView.MULTI -> {
                            Text(
                                text = "2P LOCAL · PICK YOUR PLAYER",
                                fontFamily = BungeeFontFamily,
                                fontSize = 12.sp,
                                letterSpacing = 1.2.sp,
                                color = CoilColors.Cream.copy(alpha = 0.85f)
                            )
                            val p1Col = parseHexColor(ui.p1.colorHex)
                            val p2Col = parseHexColor(ui.p2.colorHex)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ModeCard(
                                    selected = ui.meIndex == 0,
                                    accentColor = p1Col,
                                    onClick = { onSetMe(0) },
                                    modifier = Modifier.weight(1f),
                                    tag = "pick_p1"
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Player 1", fontFamily = BungeeFontFamily, fontSize = 14.sp, color = p1Col)
                                        if (ui.meIndex == 0) YouBadge(p1Col)
                                    }
                                    Text(
                                        text = if (ui.meIndex == 0) "Left D-Pad / WASD" else "Right D-Pad / Arrows",
                                        fontFamily = RubikFontFamily,
                                        fontSize = 11.sp,
                                        color = CoilColors.Cream.copy(alpha = 0.78f)
                                    )
                                }
                                ModeCard(
                                    selected = ui.meIndex == 1,
                                    accentColor = p2Col,
                                    onClick = { onSetMe(1) },
                                    modifier = Modifier.weight(1f),
                                    tag = "pick_p2"
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Player 2", fontFamily = BungeeFontFamily, fontSize = 14.sp, color = p2Col)
                                        if (ui.meIndex == 1) YouBadge(p2Col)
                                    }
                                    Text(
                                        text = if (ui.meIndex == 1) "Left D-Pad / WASD" else "Right D-Pad / Arrows",
                                        fontFamily = RubikFontFamily,
                                        fontSize = 11.sp,
                                        color = CoilColors.Cream.copy(alpha = 0.78f)
                                    )
                                }
                            }
                            SkinSwatchRow(
                                whoLabel = "Player 1",
                                playerColor = p1Col,
                                selectedIdx = ui.skinP1,
                                onSelect = { onSetSkin(0, it) },
                                compact = isCompactH
                            )
                            SkinSwatchRow(
                                whoLabel = "Player 2",
                                playerColor = p2Col,
                                selectedIdx = ui.skinP2,
                                onSelect = { onSetSkin(1, it) },
                                compact = isCompactH
                            )
                            Text(
                                text = "P1 ${ui.winsP1} : ${ui.winsP2} P2 · Best %,d".format(ui.bestScore),
                                fontFamily = RubikFontFamily,
                                fontSize = 12.sp,
                                color = CoilColors.Cream.copy(alpha = 0.8f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ToyButton(
                                    text = stringResource(R.string.btn_start),
                                    onClick = onStartMatch,
                                    tag = "multi_start_button"
                                )
                                ToyButton(
                                    text = stringResource(R.string.btn_back),
                                    onClick = onBackToMode,
                                    containerColor = CoilColors.Cream,
                                    bevelColor = Color(0xFFBBA98A),
                                    tag = "multi_back_button"
                                )
                            }
                        }

                        TitleSubView.NET -> {
                            Text(
                                text = "PHONE VS PHONE · HOTSPOT",
                                fontFamily = BungeeFontFamily,
                                fontSize = 12.sp,
                                letterSpacing = 1.2.sp,
                                color = CoilColors.Cream.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "1) Turn on hotspot on one phone. 2) Connect the other phone to it. 3) One taps Host, the other taps Join.",
                                fontFamily = RubikFontFamily,
                                fontSize = 12.sp,
                                color = CoilColors.Cream.copy(alpha = 0.88f)
                            )
                            if (ui.net.role == NetRole.NONE) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ModeCard(
                                        selected = false,
                                        accentColor = CoilColors.Gold,
                                        onClick = onNetHost,
                                        modifier = Modifier.weight(1f),
                                        tag = "net_host_button"
                                    ) {
                                        Text("Host", fontFamily = BungeeFontFamily, fontSize = 15.sp, color = CoilColors.Gold)
                                        Text("You are Player 1. Your friend joins you.", fontFamily = RubikFontFamily, fontSize = 12.sp, color = CoilColors.Cream.copy(alpha = 0.85f))
                                    }
                                    ModeCard(
                                        selected = false,
                                        accentColor = CoilColors.Teal,
                                        onClick = onNetJoinPick,
                                        modifier = Modifier.weight(1f),
                                        tag = "net_join_button"
                                    ) {
                                        Text("Join", fontFamily = BungeeFontFamily, fontSize = 15.sp, color = CoilColors.Teal)
                                        Text("You are Player 2. Enter host address.", fontFamily = RubikFontFamily, fontSize = 12.sp, color = CoilColors.Cream.copy(alpha = 0.85f))
                                    }
                                }
                            } else if (ui.net.role == NetRole.HOST) {
                                Text(
                                    text = "Tell your friend to join: ${ui.net.hostIps}",
                                    fontFamily = RubikFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CoilColors.Gold
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = ui.net.joinIp,
                                        onValueChange = onJoinIpChange,
                                        singleLine = true,
                                        placeholder = { Text("192.168.43.1") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = CoilColors.Cream,
                                            unfocusedContainerColor = CoilColors.Cream,
                                            focusedTextColor = CoilColors.Ink,
                                            unfocusedTextColor = CoilColors.Ink,
                                            focusedBorderColor = CoilColors.Teal,
                                            unfocusedBorderColor = CoilColors.Gold
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("net_ip_input")
                                    )
                                    ToyButton(
                                        text = stringResource(R.string.btn_connect),
                                        onClick = onNetConnect,
                                        enabled = !ui.net.busy,
                                        tag = "net_connect_button"
                                    )
                                }
                            }
                            Text(
                                text = ui.net.statusText,
                                fontFamily = RubikFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (ui.net.connected) CoilColors.GreenReady else CoilColors.Cream.copy(alpha = 0.9f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (ui.net.role == NetRole.HOST) {
                                    ToyButton(
                                        text = stringResource(R.string.btn_start),
                                        onClick = onNetStart,
                                        enabled = ui.net.connected,
                                        tag = "net_start_button"
                                    )
                                }
                                ToyButton(
                                    text = stringResource(R.string.btn_back),
                                    onClick = onBackToMode,
                                    containerColor = CoilColors.Cream,
                                    bevelColor = Color(0xFFBBA98A),
                                    tag = "net_back_button"
                                )
                            }
                        }
                    }

                    if (isLandscapeSplit && !isCompactH) {
                        Spacer(modifier = Modifier.height(2.dp))
                        RulesSummaryList()
                    }

                    if (ui.titleView == TitleSubView.MODE) {
                        Text(
                            text = stringResource(R.string.music_credit),
                            fontFamily = RubikFontFamily,
                            fontSize = 11.sp,
                            color = CoilColors.Cream.copy(alpha = 0.58f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YouBadge(color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "YOU",
            fontFamily = BungeeFontFamily,
            fontSize = 9.sp,
            color = CoilColors.Ink
        )
    }
}

@Composable
private fun RulesSummaryList() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RuleRow(Icons.Filled.TrendingUp, CoilColors.Teal, "Hop over rocks, holes, beetles, and the other snake. Hops refill over time.")
        RuleRow(Icons.Filled.Loop, Color(0xFFFF4A4A), "Close a loop with your body to lasso fruit for bigger multipliers.")
        RuleRow(Icons.Filled.AutoAwesome, CoilColors.Gold, "Orbs: Blaze smashes rocks, Frost slows your rival, Ghost slips through snakes.")
        RuleRow(Icons.Filled.Star, CoilColors.Gold, "3 hearts each: lose all 3 and you are out. Higher score wins!")
    }
}

@Composable
private fun RuleRow(icon: ImageVector, tint: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text = text, fontFamily = RubikFontFamily, fontSize = 12.sp, color = CoilColors.Cream.copy(alpha = 0.85f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PauseScreenOverlay(
    ui: GameUiState,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit
) {
    AnimatedVisibility(
        visible = ui.screen == ScreenMode.PAUSE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x940E0B22))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ToyText(
                    text = stringResource(R.string.title_paused),
                    fontSize = 42.sp,
                    strokeWidth = 11f
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToyButton(
                        text = stringResource(R.string.btn_resume),
                        onClick = onResume,
                        tag = "resume_button"
                    )
                    ToyButton(
                        text = stringResource(R.string.btn_restart),
                        onClick = onRestart,
                        containerColor = CoilColors.Cream,
                        bevelColor = Color(0xFFBBA98A),
                        tag = "pause_restart_button"
                    )
                    ToyButton(
                        text = stringResource(R.string.btn_menu),
                        onClick = { onMenu() },
                        containerColor = CoilColors.Cream,
                        bevelColor = Color(0xFFBBA98A),
                        tag = "pause_menu_button"
                    )
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    ui: GameUiState,
    onRematch: () -> Unit,
    onMenu: () -> Unit
) {
    AnimatedVisibility(
        visible = ui.screen == ScreenMode.OVER,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscapeSplit = maxWidth >= 500.dp && maxWidth > maxHeight
            val isCompactH = maxHeight < 480.dp
            val dockSpace = if (isCompactH) 62.dp else 82.dp
            val leftColMaxW = if (maxWidth >= 900.dp) 440.dp else 360.dp

            val bgBrush = if (isLandscapeSplit) {
                Brush.horizontalGradient(0f to CoilColors.InkOverlay, 0.45f to Color(0xB30E0B22), 0.7f to Color.Transparent)
            } else {
                Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0xCC0E0B22), 0.75f to CoilColors.InkOverlay)
            }

            val titleText = when {
                ui.isSolo -> stringResource(R.string.title_game_over)
                ui.winner >= 0 -> "P${ui.winner + 1} wins!"
                else -> "Draw!"
            }
            val titleColor = when {
                ui.isSolo -> parseHexColor(ui.p1.colorHex)
                ui.winner == 0 -> parseHexColor(ui.p1.colorHex)
                ui.winner == 1 -> parseHexColor(ui.p2.colorHex)
                else -> CoilColors.Gold
            }
            val causes = buildList {
                if (ui.p1.cause.isNotEmpty()) {
                    add("P1 ${GameConst.CAUSES[ui.p1.cause] ?: ui.p1.cause}.")
                }
                if (!ui.isSolo && ui.p2.cause.isNotEmpty()) {
                    add("P2 ${GameConst.CAUSES[ui.p2.cause] ?: ui.p2.cause}.")
                }
            }.joinToString(" ").ifEmpty { "Match over." }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgBrush)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(bottom = dockSpace)
                    .padding(horizontal = if (isCompactH) 14.dp else 20.dp, vertical = if (isCompactH) 8.dp else 16.dp),
                contentAlignment = if (isLandscapeSplit) Alignment.CenterStart else Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .then(
                            if (isLandscapeSplit) Modifier.widthIn(max = leftColMaxW)
                            else Modifier.widthIn(max = 500.dp).fillMaxWidth()
                        )
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactH) 8.dp else 12.dp)
                ) {
                    ToyText(
                        text = titleText,
                        color = titleColor,
                        fontSize = if (isCompactH) 32.sp else 44.sp,
                        strokeWidth = if (isCompactH) 9f else 12f
                    )
                    Text(
                        text = causes,
                        fontFamily = RubikFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = if (isCompactH) 13.sp else 15.sp,
                        color = CoilColors.Cream
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverStatCard(
                            player = ui.p1,
                            isWinner = !ui.isSolo && ui.winner == 0,
                            modifier = Modifier.weight(1f)
                        )
                        if (!ui.isSolo) {
                            OverStatCard(
                                player = ui.p2,
                                isWinner = ui.winner == 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (ui.isNewBest) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(CoilColors.Coral)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.new_best_badge),
                                fontFamily = RubikFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.3.sp,
                                color = CoilColors.Cream
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToyButton(
                            text = stringResource(R.string.btn_rematch),
                            onClick = onRematch,
                            tag = "rematch_button"
                        )
                        ToyButton(
                            text = stringResource(R.string.btn_menu),
                            onClick = onMenu,
                            containerColor = CoilColors.Cream,
                            bevelColor = Color(0xFFBBA98A),
                            tag = "over_menu_button"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverStatCard(
    player: PlayerHudSnapshot,
    isWinner: Boolean,
    modifier: Modifier = Modifier
) {
    val pCol = parseHexColor(player.colorHex)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isWinner) CoilColors.Cream.copy(alpha = 0.12f) else CoilColors.Cream.copy(alpha = 0.06f))
            .border(
                width = if (isWinner) 2.5.dp else 1.5.dp,
                color = if (isWinner) pCol else CoilColors.Cream.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(player.name, fontFamily = BungeeFontFamily, fontSize = 14.sp, color = pCol)
        Text("%,d".format(player.score), fontFamily = BungeeFontFamily, fontSize = 28.sp, color = CoilColors.Gold)
        StatLine("Length", player.maxLen.toString())
        StatLine("Lassos", player.lassos.toString())
        StatLine("Beetles", player.beetles.toString())
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontFamily = RubikFontFamily, fontSize = 12.sp, color = CoilColors.Cream.copy(alpha = 0.75f))
        Text(value, fontFamily = RubikFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = CoilColors.Cream)
    }
}

@Composable
fun BottomDockOverlay(
    ui: GameUiState,
    onStartOrPause: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (ui.screen == ScreenMode.PLAY) return
    val startLabel = when (ui.screen) {
        ScreenMode.PAUSE -> stringResource(R.string.btn_resume)
        else -> stringResource(R.string.btn_start)
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompactH = maxHeight < 480.dp
        val isLandscapeSplit = maxWidth >= 500.dp && maxWidth > maxHeight
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, CoilColors.InkOverlay)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 14.dp, vertical = if (isCompactH) 6.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(
                10.dp,
                if (isLandscapeSplit && ui.screen != ScreenMode.PAUSE) Alignment.Start else Alignment.CenterHorizontally
            )
        ) {
            val btnMaxW = if (isLandscapeSplit) 136.dp else 150.dp
            DockActionButton(
                label = startLabel,
                icon = Icons.Filled.PlayArrow,
                containerColor = CoilColors.Gold,
                bevelColor = CoilColors.GoldDark,
                contentColor = CoilColors.Ink,
                onClick = onStartOrPause,
                compact = isCompactH,
                modifier = Modifier.weight(1f, fill = !isLandscapeSplit).widthIn(min = 104.dp, max = btnMaxW),
                tag = "dock_start"
            )
            DockActionButton(
                label = stringResource(R.string.btn_restart),
                icon = Icons.Filled.Refresh,
                containerColor = CoilColors.Teal,
                bevelColor = CoilColors.TealDark,
                contentColor = CoilColors.Ink,
                onClick = onRestart,
                compact = isCompactH,
                modifier = Modifier.weight(1f, fill = !isLandscapeSplit).widthIn(min = 104.dp, max = btnMaxW),
                tag = "dock_restart"
            )
            DockActionButton(
                label = stringResource(R.string.btn_menu),
                icon = Icons.Filled.Home,
                containerColor = CoilColors.Coral,
                bevelColor = CoilColors.CoralDark,
                contentColor = CoilColors.Cream,
                onClick = onMenu,
                compact = isCompactH,
                modifier = Modifier.weight(1f, fill = !isLandscapeSplit).widthIn(min = 104.dp, max = btnMaxW),
                tag = "dock_menu"
            )
        }
    }
}
