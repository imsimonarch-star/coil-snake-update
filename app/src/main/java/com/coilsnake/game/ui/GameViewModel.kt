package com.coilsnake.game.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.coilsnake.game.audio.SoundEngine
import com.coilsnake.game.data.GamePrefs
import com.coilsnake.game.game.GameConst
import com.coilsnake.game.game.GameEngine
import com.coilsnake.game.game.GameRenderer
import com.coilsnake.game.game.GameUiState
import com.coilsnake.game.game.ISLAND_THEMES
import com.coilsnake.game.game.NetRole
import com.coilsnake.game.game.NetUiState
import com.coilsnake.game.game.PlayMode
import com.coilsnake.game.game.PlayerHudSnapshot
import com.coilsnake.game.game.SKIN_LIST
import com.coilsnake.game.game.ScreenMode
import com.coilsnake.game.game.TitleSubView
import com.coilsnake.game.net.HotspotNetManager
import com.coilsnake.game.net.NetEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = GamePrefs(application)
    val sound = SoundEngine(application)
    val renderer = GameRenderer(application)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var toastToken = 0
    private var toastText = ""
    private var showToastFlag = false
    private var quitTapAtMs = 0L

    private var netHostIps = ""
    private var netJoinIp = prefs.lastHostIp.ifBlank { "192.168.43.1" }

    private lateinit var netManager: HotspotNetManager

    val engine: GameEngine = GameEngine(
        prefs = prefs,
        sound = sound,
        onNetSend = { line -> netManager.send(line) },
        onToast = { msg -> showToast(msg) },
        onGoToMenuAfterDisconnect = { keepNet -> toMenu(keepNet) }
    )

    init {
        netManager = HotspotNetManager(application) { ev ->
            mainHandler.post { handleNetEvent(ev) }
        }
    }

    var renderFrameTick by mutableLongStateOf(0L)
        private set

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var hudSyncAcc = 0f

    fun stepFrame(dt: Float) {
        engine.update(dt)
        renderFrameTick++
        hudSyncAcc += dt
        if (hudSyncAcc >= 0.04f) {
            hudSyncAcc = 0f
            syncUi()
        }
    }

    fun onViewportChanged(
        w: Float,
        h: Float,
        density: Float,
        safeLeft: Float = 0f,
        safeTop: Float = 0f,
        safeRight: Float = 0f,
        safeBottom: Float = 0f
    ) {
        engine.setViewport(w, h, density, safeLeft, safeTop, safeRight, safeBottom)
    }

    /* ---------- Navigation & Menus ---------- */
    fun setModeSel(idx: Int) {
        val m = if (idx == 0) 0 else 1
        if (engine.modeSel != m) {
            engine.modeSel = m
            sound.play("ui_pick")
        }
        syncUi()
    }

    fun chooseMode(idx: Int) {
        engine.modeSel = if (idx == 0) 0 else 1
        engine.playMode = if (idx == 0) PlayMode.SOLO else PlayMode.LOCAL_MULTI
        engine.titleView = if (idx == 0) TitleSubView.SOLO else TitleSubView.MULTI
        sound.play("ui_pick")
        engine.computeFit()
        syncUi()
    }

    fun openPhoneVsPhone() {
        sound.play("ui_pick")
        engine.titleView = TitleSubView.NET
        engine.computeFit()
        syncUi()
    }

    fun backToModeSelect() {
        if (engine.netRole != NetRole.NONE) netLeave()
        engine.titleView = TitleSubView.MODE
        sound.play("ui_menu")
        engine.computeFit()
        syncUi()
    }

    fun setMe(idx: Int) {
        val v = if (idx == 0) 0 else 1
        if (engine.meIndex != v) {
            engine.meIndex = v
            prefs.meIndex = v
            sound.play("ui_pick")
            syncUi()
        }
    }

    fun setSkin(playerIdx: Int, skinIdx: Int) {
        if (skinIdx !in SKIN_LIST.indices || engine.skinIndices[playerIdx] == skinIdx) return
        val other = 1 - playerIdx
        if (engine.skinIndices[other] == skinIdx) {
            engine.skinIndices[other] = engine.skinIndices[playerIdx]
        }
        engine.skinIndices[playerIdx] = skinIdx
        prefs.skinP1 = engine.skinIndices[0]
        prefs.skinP2 = engine.skinIndices[1]
        sound.play("ui_pick")
        syncUi()
    }

    fun startMatch() {
        if (engine.netRole == NetRole.GUEST) {
            engine.sendNetRematch()
            return
        }
        val solo = engine.playMode == PlayMode.SOLO
        engine.newGame(demo = false, solo = solo)
        engine.bannerTimer = 0f
        engine.countTimer = 0f
        engine.screen = ScreenMode.PLAY
        engine.computeFit()

        when {
            engine.netRole == NetRole.HOST -> {
                engine.sendNetStart(engine.skinIndices.toList())
                engine.fxShowHint("You are P1. Arrows steer, JUMP hops over trouble.", 0f, "start")
            }
            solo -> {
                engine.fxShowHint("Use D-pad or swipe to steer. Tap JUMP to hop.", 0f, "start")
            }
            else -> {
                val me = if (engine.meIndex == 0) "P1" else "P2"
                engine.fxShowHint("2P Local: $me uses left pad (or WASD), rival uses right pad (or Arrows).", 0f, "start")
            }
        }
        sound.play("ui_start")
        syncUi()
    }

    private fun startGuestMatch() {
        engine.resetFx()
        engine.bannerTimer = 0f
        engine.countTimer = 0f
        engine.netFresh = true
        engine.screen = ScreenMode.PLAY
        engine.computeFit()
        engine.fxShowHint("You are P2. Arrows steer, JUMP hops over trouble.", 0f, "start")
        syncUi()
    }

    fun pauseOrNetQuit() {
        if (engine.screen != ScreenMode.PLAY) return
        if (engine.netRole != NetRole.NONE) {
            val now = System.currentTimeMillis()
            if (now - quitTapAtMs < 3000L) {
                quitTapAtMs = 0L
                toMenu(keepNet = false)
            } else {
                quitTapAtMs = now
                showToast("Tap again to leave the match")
            }
            return
        }
        engine.screen = ScreenMode.PAUSE
        syncUi()
    }

    fun resumeGame() {
        if (engine.screen != ScreenMode.PAUSE) return
        sound.play("ui_start")
        engine.screen = ScreenMode.PLAY
        engine.computeFit()
        syncUi()
    }

    fun restartMatch() {
        sound.play("ui_restart")
        startMatch()
    }

    fun toMenu(keepNet: Boolean = false) {
        sound.play("ui_menu")
        engine.bannerTimer = 0f
        engine.countTimer = 0f
        var view = TitleSubView.MODE
        if (engine.netRole == NetRole.HOST) {
            engine.sendNetLobby()
            view = TitleSubView.NET
        } else if (engine.netRole == NetRole.GUEST) {
            if (keepNet) view = TitleSubView.NET else netLeave()
        }
        engine.newGame(demo = true, solo = false)
        engine.titleView = view
        engine.screen = ScreenMode.TITLE
        engine.flash = 0.25f
        engine.flashColorHex = "#fff5e3"
        engine.computeFit()
        syncUi()
    }

    fun toggleMute() {
        sound.setMuted(!sound.muted)
        syncUi()
    }

    /* ---------- Phone vs Phone Hotspot ---------- */
    fun netHost() {
        sound.play("ui_pick")
        engine.netRole = NetRole.HOST
        engine.netMe = 0
        engine.netUp = false
        engine.netBusy = false
        val ips = netManager.localIps()
        netHostIps = if (ips.isNotBlank()) ips.split(",").joinToString("  or  ") else "turn the hotspot on first"
        netManager.host(GameConst.NET_PORT)
        syncUi()
    }

    fun netJoinPick() {
        sound.play("ui_pick")
        engine.netRole = NetRole.GUEST
        engine.netMe = 1
        engine.netUp = false
        engine.netBusy = false
        val gw = netManager.gateway()
        netJoinIp = gw.ifBlank { prefs.lastHostIp }.ifBlank { "192.168.43.1" }
        syncUi()
    }

    fun updateJoinIp(ip: String) {
        netJoinIp = ip
        syncUi()
    }

    fun netConnect() {
        val ip = netJoinIp.trim()
        val ipRegex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        if (!ipRegex.matches(ip)) {
            showToast("Type the address like 192.168.43.1")
            return
        }
        engine.netBusy = true
        engine.netUp = false
        syncUi()
        netManager.join(ip, GameConst.NET_PORT)
    }

    fun netStartPressed() {
        if (engine.netRole == NetRole.GUEST) {
            showToast("The host starts the match")
            return
        }
        if (engine.netRole != NetRole.HOST) return
        if (!engine.netUp) {
            showToast("Waiting for your friend to join…")
            return
        }
        engine.playMode = PlayMode.NET
        startMatch()
    }

    fun netLeave() {
        netManager.close()
        engine.netRole = NetRole.NONE
        engine.netUp = false
        engine.netBusy = false
        syncUi()
    }

    private fun handleNetEvent(ev: NetEvent) {
        when (ev) {
            is NetEvent.Open -> {
                if (engine.netRole == NetRole.GUEST) {
                    prefs.lastHostIp = netJoinIp.trim()
                }
                engine.onNetOpen()
                syncUi()
            }
            is NetEvent.Close -> {
                engine.onNetClose()
                syncUi()
            }
            is NetEvent.Message -> {
                engine.onNetMessage(
                    raw = ev.line,
                    onStartGuestMatch = { startGuestMatch() },
                    onHostRematch = { startMatch() }
                )
            }
            is NetEvent.Error -> {
                engine.netBusy = false
                showToast(ev.message)
                syncUi()
            }
        }
    }

    /* ---------- Gameplay Input ---------- */
    fun steerPlayer(playerIdx: Int, dx: Int, dz: Int) {
        engine.steer(playerIdx, dx, dz)
    }

    fun hopPlayer(playerIdx: Int) {
        engine.tryHop(playerIdx)
    }

    fun steerPrimary(dx: Int, dz: Int) {
        engine.steer(engine.currentPlayerIndex(), dx, dz)
    }

    fun hopPrimary() {
        engine.tryHop(engine.currentPlayerIndex())
    }

    fun onKeyDown(keyCode: Int): Boolean {
        val g = engine.state
        val isSolo = g?.solo == true
        val me = engine.currentPlayerIndex()
        val pWasd = if (isSolo) 0 else me
        val pArrows = if (isSolo) 0 else 1 - me

        when (keyCode) {
            KeyEvent.KEYCODE_W -> { steerIfPlayingOrCycle(pWasd, 0, -1); return true }
            KeyEvent.KEYCODE_S -> { steerIfPlayingOrCycle(pWasd, 0, 1); return true }
            KeyEvent.KEYCODE_A -> { steerIfPlayingOrNav(pWasd, -1, 0); return true }
            KeyEvent.KEYCODE_D -> { steerIfPlayingOrNav(pWasd, 1, 0); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { steerIfPlayingOrCycle(pArrows, 0, -1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { steerIfPlayingOrCycle(pArrows, 0, 1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { steerIfPlayingOrNav(pArrows, -1, 0); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { steerIfPlayingOrNav(pArrows, 1, 0); return true }
            KeyEvent.KEYCODE_SPACE -> {
                when (engine.screen) {
                    ScreenMode.PLAY -> engine.tryHop(pWasd)
                    ScreenMode.TITLE -> {
                        if (engine.titleView == TitleSubView.MODE) chooseMode(engine.modeSel)
                        else if (engine.titleView == TitleSubView.NET) netStartPressed()
                        else startMatch()
                    }
                    ScreenMode.OVER -> startMatch()
                    ScreenMode.PAUSE -> resumeGame()
                }
                return true
            }
            KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_NUMPAD_0 -> {
                if (engine.screen == ScreenMode.PLAY) engine.tryHop(pArrows)
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when (engine.screen) {
                    ScreenMode.TITLE -> {
                        if (engine.titleView == TitleSubView.MODE) chooseMode(engine.modeSel)
                        else if (engine.titleView == TitleSubView.NET) netStartPressed()
                        else startMatch()
                    }
                    ScreenMode.OVER -> startMatch()
                    ScreenMode.PLAY -> pauseOrNetQuit()
                    ScreenMode.PAUSE -> resumeGame()
                }
                return true
            }
            KeyEvent.KEYCODE_R -> {
                if (engine.screen != ScreenMode.TITLE) restartMatch()
                return true
            }
            KeyEvent.KEYCODE_P -> {
                if (engine.screen == ScreenMode.PLAY) pauseOrNetQuit()
                else if (engine.screen == ScreenMode.PAUSE) resumeGame()
                return true
            }
            KeyEvent.KEYCODE_M -> {
                toggleMute()
                return true
            }
        }
        return false
    }

    private fun steerIfPlayingOrCycle(playerIdx: Int, dx: Int, dz: Int) {
        if (engine.screen == ScreenMode.PLAY) {
            engine.steer(playerIdx, dx, dz)
        } else if (engine.screen == ScreenMode.TITLE) {
            val target = if (engine.titleView == TitleSubView.SOLO) 0 else playerIdx
            val next = (engine.skinIndices[target] + dz + SKIN_LIST.size) % SKIN_LIST.size
            setSkin(target, next)
        }
    }

    private fun steerIfPlayingOrNav(playerIdx: Int, dx: Int, dz: Int) {
        if (engine.screen == ScreenMode.PLAY) {
            engine.steer(playerIdx, dx, dz)
        } else if (engine.screen == ScreenMode.TITLE) {
            if (engine.titleView == TitleSubView.MODE) setModeSel(if (dx < 0) 0 else 1)
            else if (engine.titleView == TitleSubView.MULTI) setMe(if (dx < 0) 0 else 1)
        }
    }

    fun handleBackPress(): Boolean {
        return when {
            engine.screen == ScreenMode.PLAY -> {
                pauseOrNetQuit()
                true
            }
            engine.screen == ScreenMode.PAUSE -> {
                resumeGame()
                true
            }
            engine.screen == ScreenMode.OVER -> {
                toMenu(keepNet = false)
                true
            }
            engine.screen == ScreenMode.TITLE && engine.titleView != TitleSubView.MODE -> {
                backToModeSelect()
                true
            }
            else -> false
        }
    }

    fun showToast(msg: String) {
        toastText = msg
        showToastFlag = true
        val token = ++toastToken
        syncUi()
        mainHandler.postDelayed({
            if (toastToken == token) {
                showToastFlag = false
                syncUi()
            }
        }, 2800L)
    }

    /* ---------- Lifecycle ---------- */
    fun onHostPause() {
        if (engine.screen == ScreenMode.PLAY && engine.state?.started == true && engine.netRole == NetRole.NONE) {
            engine.screen = ScreenMode.PAUSE
            syncUi()
        }
        sound.onPause()
    }

    fun onHostResume() {
        sound.onResume()
    }

    override fun onCleared() {
        netManager.close()
        sound.release()
        super.onCleared()
    }

    /* ---------- Snapshot ---------- */
    private fun syncUi() {
        val next = buildUiState()
        if (_uiState.value != next) {
            _uiState.value = next
        }
    }

    private fun buildUiState(): GameUiState {
        val g = engine.state
        fun snapPlayer(idx: Int): PlayerHudSnapshot {
            val p = g?.players?.getOrNull(idx)
            val col = engine.playerColorHex(idx)
            if (p == null) return PlayerHudSnapshot(id = idx, name = "P${idx + 1}", colorHex = col)
            val mult = if (p.comboT > 0f) min(p.combo, GameConst.MAX_MULT) else 0
            return PlayerHudSnapshot(
                id = idx,
                name = p.name,
                colorHex = col,
                score = p.score,
                hearts = p.hearts,
                isOut = p.st != "run",
                comboMult = if (mult >= 2) mult else 0,
                comboFrac = (p.comboT / GameConst.COMBO_WINDOW).coerceIn(0f, 1f),
                hops = p.hops,
                hopCharge = p.hopCharge.coerceIn(0f, 1f),
                blazeFrac = (p.power.blaze / GameConst.POWER_BLAZE_DUR).coerceIn(0f, 1f),
                frostFrac = (p.power.frost / GameConst.POWER_FROST_DUR).coerceIn(0f, 1f),
                ghostFrac = (p.power.ghost / GameConst.POWER_GHOST_DUR).coerceIn(0f, 1f),
                maxLen = p.stats.maxLen,
                lassos = p.stats.lassos,
                beetles = p.stats.beetles,
                cause = p.cause
            )
        }

        val p1 = snapPlayer(0)
        val p2 = snapPlayer(1)
        val isNewBest = g != null && g.players.any { it.score > 0 && it.score >= engine.bestScore }

        val netStatus = when (engine.netRole) {
            NetRole.NONE -> "Pick Host or Join."
            NetRole.HOST -> if (engine.netUp) "Friend connected. Press Start!" else "Waiting for your friend to join…"
            NetRole.GUEST -> when {
                engine.netUp -> "Connected! Waiting for the host to press Start…"
                engine.netBusy -> "Connecting…"
                else -> "Type the host address, then tap Connect."
            }
        }

        val lv = g?.level ?: 1
        val thName = ISLAND_THEMES[(lv - 1) % ISLAND_THEMES.size].name

        return GameUiState(
            screen = engine.screen,
            titleView = engine.titleView,
            modeSel = engine.modeSel,
            meIndex = engine.meIndex,
            skinP1 = engine.skinIndices[0],
            skinP2 = engine.skinIndices[1],
            bestScore = engine.bestScore,
            winsP1 = engine.wins[0],
            winsP2 = engine.wins[1],
            muted = sound.muted,
            isSolo = g?.solo == true,
            level = lv,
            themeName = thName,
            applesLv = g?.applesLv ?: 0,
            p1 = p1,
            p2 = p2,
            winner = g?.winner ?: -2,
            isNewBest = isNewBest,
            bannerTitle = engine.bannerTitle,
            bannerSub = engine.bannerSub,
            bannerBlurb = engine.bannerBlurb,
            showBanner = engine.bannerTimer > 0f,
            countText = engine.countText,
            showCount = engine.countTimer > 0f,
            countKey = engine.countKey,
            hintText = engine.hintText,
            showHint = engine.hintTimer > 0f || (engine.hintKind == "start" && engine.hintText.isNotEmpty()),
            toastText = toastText,
            showToast = showToastFlag,
            net = NetUiState(
                role = engine.netRole,
                connected = engine.netUp,
                busy = engine.netBusy,
                hostIps = netHostIps,
                joinIp = netJoinIp,
                statusText = netStatus
            )
        )
    }
}
