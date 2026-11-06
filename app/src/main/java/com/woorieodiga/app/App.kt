package com.woorieodiga.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var engine: TripEngine
        private set
    lateinit var navi: NaviSource
        private set
    lateinit var gear: GearSource
        private set
    val demo by lazy { DemoTrip(engine) }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        engine = TripEngine(prefs)
        val t0 = SystemClock.uptimeMillis()
        navi = NaviSource(this, engine).also { it.start() }
        val t1 = SystemClock.uptimeMillis()
        gear = GearSource(this, engine).also { it.start() }
        Log.i("WooriApp", "시작: 내비 ${t1 - t0}ms, 기어 ${SystemClock.uptimeMillis() - t1}ms")
    }
}

/**
 * 시험·시연용 가짜 여행(디버그 빌드에서만 버튼이 보인다).
 * 12km·25분 길을 90초에 달리고, 중간에 재탐색 한 번, 도착 3초 뒤 P 로 바꾼다.
 */
class DemoTrip(private val engine: TripEngine) {
    private val main = Handler(Looper.getMainLooper())
    private var step = 0
    var running = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            step++
            val left = 1f - step / STEPS.toFloat()
            if (step == STEPS / 2) engine.onReroute()
            if (left <= 0f) {
                engine.onArrived(NAME)
                main.postDelayed({ engine.onGear(true); running = false }, 3_000)
                return
            }
            engine.onRemaining(NAME, (TOTAL_M * left).toInt(), (TOTAL_S * left).toInt())
            main.postDelayed(this, 1_000)
        }
    }

    fun start() {
        stop()
        running = true
        step = 0
        engine.onRouteStarted(NAME, TOTAL_M, TOTAL_S)
        engine.onGear(false)
        main.postDelayed(tick, 1_000)
    }

    fun stop() {
        main.removeCallbacks(tick)
        running = false
    }

    companion object {
        const val NAME = "할머니 집"
        private const val TOTAL_M = 12_000
        private const val TOTAL_S = 25 * 60
        private const val STEPS = 90
    }
}
