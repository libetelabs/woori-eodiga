package com.woorieodiga.app

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 화면은 셋이다.
 *  - 처음(IDLE): 내비에 목적지를 찍으면 시작. 정차 중엔 탈것 고르기, 유튜브 보기, 즐겨찾기로 바로 출발.
 *  - 여행 중(ROUTING): 구간 배경 + 탈것 + 진행 띠 + "언제 도착해?" 답 + 음성 버튼 하나.
 *  - 도착(ARRIVED): P 에서만 축하 장면과 완주 도장.
 * portrait(내비 옆 창 하나)와 landscape(창 두 개) 모두 같은 화면을 배치만 바꿔 쓴다.
 */
class MainActivity : Activity() {
    private val app get() = application as App
    private lateinit var root: FrameLayout
    private lateinit var voice: Voice
    private var picking = false
    private var watching = false
    private var videoKind = VideoKind.CARTOON
    private var lastKey: Any? = null
    private val observer: (TripState) -> Unit = { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(color(R.color.ivi_background_primary)) }
        setContentView(root)
        voice = Voice(this)
        // 창 크기가 바뀌면 다시 배치(세로 창은 높이에 따라 그림 크기가 달라진다)
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) root.post { lastKey = null; render(app.engine.state) }
        }
        // 밀도 변경 등으로 다시 만들어질 땐 같은 intent 로 데모가 또 시작되지 않게
        if (savedInstanceState == null) startDemoIfAsked(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        startDemoIfAsked(intent)
    }

    /** 시험 스크립트용: `am start -n .../.MainActivity --ez demo true` (디버그 빌드만). */
    private fun startDemoIfAsked(intent: android.content.Intent?) {
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("demo", false) == true) {
            intent.removeExtra("demo")
            app.demo.start()
        }
    }

    override fun onStart() {
        super.onStart()
        app.engine.observe(observer)
        app.navi.setPolling(true)
    }

    override fun onStop() {
        // 화면이 안 보이면 갱신·폴링을 멈춘다(백그라운드 최소화)
        app.navi.setPolling(false)
        app.engine.unobserve(observer)
        super.onStop()
    }

    override fun onDestroy() {
        voice.release()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 주야간이 바뀌면 색을 다시 읽어 그린다(액티비티는 다시 만들지 않음)
        root.setBackgroundColor(color(R.color.ivi_background_primary))
        lastKey = null
        render(app.engine.state)
    }

    /** 창 비율로 판단한다(내비 옆 세로 창 / 창 두 개를 쓰는 가로 창). */
    private val landscape
        get() = if (root.width > 0) root.width > root.height
        else resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun render(s: TripState) {
        if (!s.parked) { picking = false; watching = false }
        // 화면에 보이는 값이 같으면 다시 만들지 않는다
        val key = listOf(
            s.phase, s.stage, s.destKind, s.parked, s.rerouting, s.naviConnected, s.shortcuts.size,
            (s.progress * 20).toInt(), timeKey(s), picking, watching, videoKind, app.prefs.ride, landscape, root.height,
        )
        if (key == lastKey) return
        lastKey = key
        root.removeAllViews()
        root.addView(
            when {
                picking && s.parked -> ridePicker(s)
                watching && s.parked -> videoPicker()
                s.phase == Phase.ROUTING -> tripScreen(s)
                s.phase == Phase.ARRIVED -> arrivedScreen(s)
                else -> homeScreen(s)
            }
        )
    }

    private fun timeKey(s: TripState) = Copy.timeAnswer(s.remainSeconds, s.destKind).title + Copy.detail(s.remainSeconds, s.remainMeters)

    // ---------- 처음 ----------

    private fun homeScreen(s: TripState): View = scroll(column(pad = 40).apply {
        addView(text("우리 어디가?", 56f, bold = true))
        addView(
            text(
                if (s.naviConnected) "내비에서 목적지를 정하면 여행이 시작돼요" else "내비와 연결하는 중이에요",
                28f, secondary = true,
            ), lp(top = 8)
        )
        addView(image(app.prefs.ride.drawable, 0), lp(top = 24, w = dp(260), h = dp(260), gravity = Gravity.CENTER_HORIZONTAL))
        addView(text(app.prefs.ride.label, 28f).apply { gravity = Gravity.CENTER }, lp())

        if (s.parked) {
            addView(button("탈것 바꾸기", filled = false) { picking = true; lastKey = null; render(app.engine.state) }, lp(top = 16))
            addView(videoButton(), lp(top = 12))
            if (s.shortcuts.isNotEmpty()) {
                addView(text("바로 출발", 32f, bold = true), lp(top = 40))
                s.shortcuts.forEach { sc ->
                    addView(button(sc.name, filled = false) { app.navi.startRoute(sc) }, lp(top = 12))
                }
            }
        } else {
            addView(text("멈췄을 때 탈것을 고를 수 있어요", 26f, secondary = true), lp(top = 24))
        }

        if (app.prefs.stamps > 0) addView(text("완주 도장 ${app.prefs.stamps}개", 24f, secondary = true), lp(top = 32))
        if (BuildConfig.DEBUG) {
            addView(button("데모 여행 (시험용)", filled = false) { app.demo.start() }, lp(top = 40))
        }
    })

    private fun ridePicker(s: TripState): View = scroll(column(pad = 40).apply {
        addView(text("무엇을 타고 갈까?", 48f, bold = true))
        val grid = GridLayout(context).apply { columnCount = if (landscape) 4 else 2 }
        Ride.entries.forEach { ride ->
            val cell = column(pad = 12).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                toggle(this, ride == app.prefs.ride)
                addView(image(ride.drawable, 200))
                addView(text(ride.label, 26f, bold = ride == app.prefs.ride).apply { gravity = Gravity.CENTER })
                setOnClickListener {
                    app.prefs.ride = ride
                    voice.say("${ride.label}${if (ride.type == RideType.UNICORN) "을" else "를"} 골랐어!")
                    picking = false
                    lastKey = null
                    render(app.engine.state)
                }
            }
            grid.addView(cell, GridLayout.LayoutParams().apply { setMargins(dp(8), dp(8), dp(8), dp(8)); width = dp(if (landscape) 300 else 360) })
        }
        addView(grid, lp(top = 16))
        addView(button("닫기", filled = false) { picking = false; lastKey = null; render(s) }, lp(top = 16))
    })

    // ---------- 여행 중 ----------

    private fun tripScreen(s: TripState): View {
        val scene = SceneView(this).apply {
            set(Copy.stageBg(s.stage), app.prefs.ride.drawable, if (s.rerouting) R.drawable.state_reroute else 0)
        }
        val answer = Copy.timeAnswer(s.remainSeconds, s.destKind)
        val panel = column(pad = 32).apply {
            setBackgroundColor(color(R.color.ivi_background_primary))
            addView(
                text(if (s.rerouting) "길이 바뀌었어! 다시 가 보자" else Copy.stageSpeech(s.stage), 30f, secondary = true),
            )
            addView(StripView(context).apply { set(Copy.stops, s.progress, app.prefs.ride.mini) }, LinearLayout.LayoutParams(-1, dp(130)).apply { topMargin = dp(8) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = round(R.color.ivi_background_secondary, 32f)
                setPadding(dp(20), dp(16), dp(20), dp(16))
                addView(image(answer.icon, 120))
                addView(column(pad = 0).apply {
                    addView(text(answer.title, 36f, bold = true))
                    addView(text(Copy.detail(s.remainSeconds, s.remainMeters), 24f, secondary = true), lp(top = 4))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
            }, lp(top = 16))
            addView(button("얼마나 남았어?", filled = true) {
                voice.say(answer.speech)
            }, lp(top = 20))
            if (s.parked) {
                addView(videoButton(), lp(top = 12))
                addView(text("어디 가?", 24f, secondary = true), lp(top = 24))
                addView(kindChips(s.destKind), lp(top = 8))
            }
        }
        return split(scene, panel, sceneWeight = if (landscape) 1.25f else 1.1f, panelMinDp = TRIP_PANEL_DP)
    }

    /** 목적지 종류(이름과 말에만 쓰인다). 칸이 좁아도 넘치지 않게 3줄로 접는다. */
    private fun kindChips(current: DestKind) = GridLayout(this).apply {
        columnCount = 4
        clipChildren = false
        DestKind.entries.forEach { k ->
            addView(text(k.label, 26f, bold = k == current).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                minHeight = dp(80)
                setPadding(dp(8), 0, dp(8), 0)
                toggle(this, k == current)
                setOnClickListener { app.engine.setDestKind(k) }
            }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0
                setMargins(dp(4), dp(4), dp(4), dp(8))
            })
        }
    }

    // ---------- 유튜브 ----------

    private fun videoButton() = button("유튜브 보기", filled = false) { watching = true; lastKey = null; render(app.engine.state) }

    /** 만화·애니·동요 탭과 플레이리스트 목록. 고르면 차의 유튜브 앱이 열린다. */
    private fun videoPicker(): View = scroll(column(pad = 40).apply {
        addView(text("무엇을 볼까?", 48f, bold = true))
        addView(LinearLayout(context).apply {
            VideoKind.entries.forEach { k ->
                addView(text(k.label, 28f, bold = k == videoKind).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(80)
                    toggle(this, k == videoKind)
                    setOnClickListener { videoKind = k; lastKey = null; render(app.engine.state) }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            }
        }, lp(top = 16))
        val grid = GridLayout(context).apply { columnCount = if (landscape) 4 else 2 }
        KidsVideos.of(videoKind).forEach { v ->
            grid.addView(text(v.title, 26f).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                minHeight = dp(100)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                toggle(this, false)
                setOnClickListener {
                    if (KidsVideos.open(context, v)) {
                        watching = false
                        lastKey = null
                    } else {
                        voice.say("유튜브 앱이 없어. 앱 마켓에서 받아 줘")
                    }
                }
            }, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0
                setMargins(dp(6), dp(6), dp(6), dp(6))
            })
        }
        addView(grid, lp(top = 16))
        addView(button("닫기", filled = false) { watching = false; lastKey = null; render(app.engine.state) }, lp(top = 16))
    })

    // ---------- 도착 ----------

    private fun arrivedScreen(s: TripState): View {
        val celebrate = s.parked
        val scene = SceneView(this).apply { set(Copy.GOAL_BG, app.prefs.ride.drawable) }
        val panel = column(pad = 32).apply {
            setBackgroundColor(color(R.color.ivi_background_primary))
            gravity = Gravity.CENTER_HORIZONTAL
            if (celebrate) {
                // 카드는 남는 높이만 쓴다: 낮은 화면에서도 "여행 끝내기"가 밀려나지 않게
                addView(FrameLayout(context).apply {
                    addView(image(R.drawable.card_frame, 0), FrameLayout.LayoutParams(-1, -1))
                    addView(column(pad = 0).apply {
                        gravity = Gravity.CENTER
                        addView(image(R.drawable.stamp_complete, 120))
                        addView(text("${app.prefs.stamps + 1}번째 여행 완주!", 32f, bold = true).apply {
                            gravity = Gravity.CENTER
                            setTextColor(color(R.color.card_text))
                        })
                    }, FrameLayout.LayoutParams(-1, -1))
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(button("여행 끝내기", filled = true) { app.engine.finishTrip() }, lp(top = 20))
            } else {
                addView(text("도착했어!", 48f, bold = true))
                addView(text("차가 멈추면 축하해 줄게", 28f, secondary = true), lp(top = 8))
            }
        }
        if (celebrate) voice.say("${s.destKind.label}에 도착했어! 여행 완주!")
        return split(scene, panel, sceneWeight = 1.2f, panelMinDp = ARRIVED_PANEL_DP)
    }

    // ---------- 조립 도우미 ----------

    /**
     * 가로 창은 그림|패널 비율로 나눈다. 세로 창은 패널에 필요한 높이를 먼저 주고 남는 만큼만 그림에 준다:
     * 12.9″ 세로(628×855dp)처럼 낮은 창에서도 주행 중 버튼("얼마나 남았어?")이 스크롤 없이 보이게.
     */
    private fun split(scene: View, panel: View, sceneWeight: Float, panelMinDp: Int) = LinearLayout(this).apply {
        orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val weighted = (root.height * sceneWeight / (sceneWeight + 1f)).toInt()
        val sceneLp = when {
            landscape -> LinearLayout.LayoutParams(0, -1, sceneWeight)
            root.height == 0 -> LinearLayout.LayoutParams(-1, 0, sceneWeight)
            else -> LinearLayout.LayoutParams(-1, minOf(weighted, maxOf(root.height - dp(panelMinDp), dp(MIN_SCENE_DP))))
        }
        val panelLp = if (landscape) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f)
        addView(scene, sceneLp)
        addView(scroll(panel), panelLp)
    }

    private fun scroll(child: View) = ScrollView(this).apply { isFillViewport = true; addView(child) }

    private fun column(pad: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

    /**
     * Pleos 타이포: Headline 60/56/48, Title 40/36/32, Body 30/28/26, Label 24/20(줄 높이도 가이드 값).
     * 글꼴은 시스템 sans-serif 가 Pleos 에선 Asta Sans 다. 굵게는 Bold(600)이다. 700 은 ExtraBold 로 너무 두껍다.
     */
    private fun text(value: String, sp: Float, bold: Boolean = false, secondary: Boolean = false) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        LINE_HEIGHT[sp.toInt()]?.let { lineHeight = dp(it) }
        setTextColor(color(if (secondary) R.color.ivi_text_secondary else R.color.ivi_text_primary))
        if (bold) typeface = BOLD
        maxLines = 3
    }

    /**
     * Pleos Basic Button, Large(높이 100, 모서리 32, Body Large 30 Bold).
     * 보통 버튼은 Pleos Primary(바탕 위 옅은 회색), 화면마다 하나뿐인 주 버튼만 앱 색(보라)으로 채운다.
     */
    private fun button(label: String, filled: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        lineHeight = dp(38)
        typeface = BOLD
        minHeight = dp(100)
        setPadding(dp(32), dp(12), dp(32), dp(12))
        setTextColor(color(if (filled) R.color.ivi_button_text else R.color.ivi_text_primary))
        background = round(if (filled) R.color.ivi_button_filled_enabled else R.color.pleos_button_primary, 32f)
        setOnClickListener { onClick() }
    }

    /** Pleos Toggle Button: 평소 옅은 회색, 고르면 흰 바탕에 그림자(Floating). 모서리 24. */
    private fun toggle(view: View, selected: Boolean) = view.apply {
        background = round(if (selected) R.color.pleos_toggle_selected else R.color.pleos_toggle_enabled, 24f)
        elevation = if (selected) dp(6).toFloat() else 0f
    }

    private fun image(res: Int, sizeDp: Int) = ImageView(this).apply {
        setImageBitmap(Bitmaps.get(context, res))
        scaleType = ImageView.ScaleType.FIT_CENTER
        if (sizeDp > 0) layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun round(colorRes: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color(colorRes))
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun lp(
        top: Int = 0,
        w: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        gravity: Int = Gravity.NO_GRAVITY,
    ) = LinearLayout.LayoutParams(w, h).apply {
        topMargin = dp(top)
        this.gravity = gravity
    }

    private fun color(res: Int) = getColor(res)

    companion object {
        private val BOLD: Typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        /** 세로 창에서 패널이 스크롤 없이 차지해야 할 높이(dp): 여행 중 = 말·띠·답 카드·버튼, 도착 = 카드 최소·버튼. */
        private const val TRIP_PANEL_DP = 540
        private const val ARRIVED_PANEL_DP = 420
        private const val MIN_SCENE_DP = 200
        /** Pleos 글자 크기 → 줄 높이. */
        private val LINE_HEIGHT = mapOf(60 to 80, 56 to 72, 48 to 64, 40 to 52, 36 to 44, 32 to 40, 30 to 38, 28 to 36, 26 to 34, 24 to 28, 20 to 24)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
