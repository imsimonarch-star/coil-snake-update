package com.coilsnake.game

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coilsnake.game.game.ScreenMode
import com.coilsnake.game.game.TitleSubView
import com.coilsnake.game.ui.GameViewModel
import com.coilsnake.game.ui.screens.BottomDockOverlay
import com.coilsnake.game.ui.screens.CountdownOverlay
import com.coilsnake.game.ui.screens.GameHudOverlay
import com.coilsnake.game.ui.screens.GameOverOverlay
import com.coilsnake.game.ui.screens.HintPillOverlay
import com.coilsnake.game.ui.screens.LevelBannerOverlay
import com.coilsnake.game.ui.screens.PauseScreenOverlay
import com.coilsnake.game.ui.screens.TitleScreenOverlay
import com.coilsnake.game.ui.screens.ToastPillOverlay
import com.coilsnake.game.ui.screens.TouchControlsOverlay
import com.coilsnake.game.ui.theme.CoilSnakeTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            CoilSnakeTheme {
                CoilSnakeApp(viewModel = gameViewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        gameViewModel.onHostPause()
    }

    override fun onResume() {
        super.onResume()
        gameViewModel.onHostResume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameViewModel.onKeyDown(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun CoilSnakeApp(viewModel: GameViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val densityObj = LocalDensity.current
    val density = densityObj.density
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing
    val insetLeft = safeInsets.getLeft(densityObj, layoutDirection).toFloat()
    val insetTop = safeInsets.getTop(densityObj).toFloat()
    val insetRight = safeInsets.getRight(densityObj, layoutDirection).toFloat()
    val insetBottom = safeInsets.getBottom(densityObj).toFloat()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val currentScreen by rememberUpdatedState(ui.screen)

    BackHandler(enabled = ui.screen != ScreenMode.TITLE || ui.titleView != TitleSubView.MODE) {
        viewModel.handleBackPress()
    }

    LaunchedEffect(canvasSize, density, insetLeft, insetTop, insetRight, insetBottom) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            viewModel.onViewportChanged(
                w = canvasSize.width.toFloat(),
                h = canvasSize.height.toFloat(),
                density = density,
                safeLeft = insetLeft,
                safeTop = insetTop,
                safeRight = insetRight,
                safeBottom = insetBottom
            )
        }
    }

    LaunchedEffect(Unit) {
        var lastNs = 0L
        while (true) {
            withFrameNanos { frameNs ->
                val dt = if (lastNs == 0L) {
                    1f / 60f
                } else {
                    ((frameNs - lastNs) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                }
                lastNs = frameNs
                viewModel.stepFrame(dt)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscapeBetweenControls = maxWidth > maxHeight && maxWidth >= 560.dp
        val swipeThresholdPx = 24f * density

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    canvasSize = sz
                }
                .pointerInput(swipeThresholdPx) {
                    var dragX = 0f
                    var dragY = 0f
                    var fired = false
                    detectDragGestures(
                        onDragStart = {
                            dragX = 0f
                            dragY = 0f
                            fired = false
                        },
                        onDrag = { change, dragAmount ->
                            if (currentScreen != ScreenMode.PLAY || fired) return@detectDragGestures
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                            if (abs(dragX) > swipeThresholdPx || abs(dragY) > swipeThresholdPx) {
                                fired = true
                                change.consume()
                                if (abs(dragX) > abs(dragY)) {
                                    viewModel.steerPrimary(if (dragX > 0f) 1 else -1, 0)
                                } else {
                                    viewModel.steerPrimary(0, if (dragY > 0f) 1 else -1)
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (currentScreen == ScreenMode.PLAY) {
                                viewModel.hopPrimary()
                            }
                        }
                    )
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val frameTick = viewModel.renderFrameTick
            drawIntoCanvas { canvas ->
                viewModel.renderer.render(
                    canvas = canvas.nativeCanvas,
                    eng = viewModel.engine,
                    width = size.width,
                    height = size.height
                )
            }
        }

        GameHudOverlay(
            ui = ui,
            onToggleMute = viewModel::toggleMute,
            onPause = viewModel::pauseOrNetQuit,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        LevelBannerOverlay(ui = ui)
        CountdownOverlay(ui = ui)

        HintPillOverlay(
            ui = ui,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(bottom = if (isLandscapeBetweenControls) 14.dp else 176.dp)
                .widthIn(max = if (isLandscapeBetweenControls) 320.dp else 440.dp)
        )

        TitleScreenOverlay(
            ui = ui,
            onChooseMode = viewModel::chooseMode,
            onOpenNet = viewModel::openPhoneVsPhone,
            onBackToMode = viewModel::backToModeSelect,
            onSetMe = viewModel::setMe,
            onSetSkin = viewModel::setSkin,
            onStartMatch = viewModel::startMatch,
            onNetHost = viewModel::netHost,
            onNetJoinPick = viewModel::netJoinPick,
            onJoinIpChange = viewModel::updateJoinIp,
            onNetConnect = viewModel::netConnect,
            onNetStart = viewModel::netStartPressed
        )

        PauseScreenOverlay(
            ui = ui,
            onResume = viewModel::resumeGame,
            onRestart = viewModel::restartMatch,
            onMenu = { viewModel.toMenu(keepNet = false) }
        )

        GameOverOverlay(
            ui = ui,
            onRematch = viewModel::startMatch,
            onMenu = { viewModel.toMenu(keepNet = false) }
        )

        TouchControlsOverlay(
            ui = ui,
            playMode = viewModel.engine.playMode,
            onSteer = viewModel::steerPlayer,
            onHop = viewModel::hopPlayer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        BottomDockOverlay(
            ui = ui,
            onStartOrPause = {
                when (ui.screen) {
                    ScreenMode.PAUSE -> viewModel.resumeGame()
                    ScreenMode.OVER -> viewModel.startMatch()
                    ScreenMode.TITLE -> when (ui.titleView) {
                        TitleSubView.MODE -> viewModel.chooseMode(ui.modeSel)
                        TitleSubView.NET -> viewModel.netStartPressed()
                        else -> viewModel.startMatch()
                    }
                    ScreenMode.PLAY -> Unit
                }
            },
            onRestart = viewModel::restartMatch,
            onMenu = {
                if (ui.screen == ScreenMode.TITLE && ui.titleView != TitleSubView.MODE) {
                    viewModel.backToModeSelect()
                } else {
                    viewModel.toMenu(keepNet = false)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        ToastPillOverlay(
            ui = ui,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
