package com.woorieodiga.app

import android.os.Handler
import android.os.Looper

/** 목적지 종류. 그림은 모두 같은 "도착 지점"이고, 이름과 안내 말("우리 집까지 만화 두 편")에만 쓴다. */
enum class DestKind(val label: String) {
    HOME("우리 집"),
    GRANDMA("할머니 집"),
    BEACH("바다"),
    AMUSEMENT("놀이공원"),
    ZOO("동물원"),
    CAMPING("캠핑장"),
    KIDS_CAFE("키즈카페"),
    MART("마트"),
    FRIEND("친구 집"),
    OTHER("목적지"),
}

enum class Phase { IDLE, ROUTING, ARRIVED }

/** 여행 구간. 실제 장소가 아니라 남은 비율로 넘어가는 "여행 단계"다. */
enum class Stage(val until: Float) {
    NEIGHBORHOOD(0.25f),
    CITY(0.50f),
    FIELD(0.75f),
    MOUNTAIN(0.92f),
    DESTINATION(1.01f);

    companion object {
        fun of(progress: Float): Stage = entries.first { progress < it.until }
    }
}

data class TripState(
    val phase: Phase = Phase.IDLE,
    val destName: String = "",
    val destKind: DestKind = DestKind.OTHER,
    val totalMeters: Int = 0,
    val remainMeters: Int = 0,
    val remainSeconds: Int = 0,
    val parked: Boolean = true,
    val rerouting: Boolean = false,
    val naviConnected: Boolean = false,
    val shortcuts: List<Shortcut> = emptyList(),
) {
    val progress: Float
        get() = if (totalMeters <= 0) 0f
        else (1f - remainMeters.toFloat() / totalMeters).coerceIn(0f, 1f)

    val stage: Stage
        get() = if (phase == Phase.ARRIVED) Stage.DESTINATION else Stage.of(progress)
}

/** 즐겨찾기·최근 목적지에서 온 바로가기. 정차 중에만 누를 수 있다. */
data class Shortcut(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val displayLatitude: Double?,
    val displayLongitude: Double?,
    val poiId: String,
    val poiSubId: String,
    val poiProvider: String,
)

/**
 * 내비·기어·데모에서 오는 값을 하나의 상태로 모은다.
 * 화면은 이 상태만 보고 그린다. 값이 같으면 다시 그리지 않는다(주행 중 CPU 절약).
 */
class TripEngine(private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(TripState) -> Unit>()
    private val clearReroute = Runnable { update { it.copy(rerouting = false) } }

    var state = TripState()
        private set

    fun observe(listener: (TripState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun unobserve(listener: (TripState) -> Unit) {
        listeners -= listener
    }

    fun update(change: (TripState) -> TripState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { update(change) }
            return
        }
        val next = change(state)
        if (next == state) return
        state = next
        listeners.toList().forEach { it(next) }
    }

    /** 길안내 시작: 전체 거리를 기억해 둔다(앱이 중간에 켜져도 이어서 쓰도록). */
    fun onRouteStarted(name: String, totalMeters: Int, totalSeconds: Int) {
        prefs.saveTotal(name, totalMeters)
        update {
            it.copy(
                phase = Phase.ROUTING,
                destName = name,
                destKind = prefs.kindFor(name),
                totalMeters = totalMeters,
                remainMeters = totalMeters,
                remainSeconds = totalSeconds,
            )
        }
    }

    /** 남은 거리·시간. 앱이 길안내 도중에 켜졌다면 저장된 전체 거리, 없으면 처음 본 값을 전체로 쓴다. */
    fun onRemaining(name: String, remainMeters: Int, remainSeconds: Int) {
        if (remainMeters <= 0 && remainSeconds <= 0) return
        update {
            // 도착 화면을 보는 중에 같은 목적지 값이 늦게 오면 무시한다
            if (it.phase == Phase.ARRIVED && (name.isEmpty() || name == it.destName)) return@update it
            val sameTrip = it.phase == Phase.ROUTING && (name.isEmpty() || name == it.destName)
            val total = when {
                sameTrip && it.totalMeters >= remainMeters -> it.totalMeters
                else -> prefs.totalFor(name)?.takeIf { t -> t >= remainMeters } ?: remainMeters
            }
            if (!sameTrip) prefs.saveTotal(name, total)
            val dest = name.ifEmpty { it.destName }
            it.copy(
                phase = Phase.ROUTING,
                destName = dest,
                destKind = prefs.kindFor(dest),
                totalMeters = total,
                remainMeters = remainMeters,
                remainSeconds = remainSeconds,
            )
        }
    }

    /** 남은 시간만 자주 오는 경우(onDrivingInfo). 거리는 getDestinationInfo 로 받는다. */
    fun onRemainTime(remainSeconds: Int) {
        if (remainSeconds <= 0) return
        update { if (it.phase == Phase.ROUTING) it.copy(remainSeconds = remainSeconds) else it }
    }

    fun setNaviConnected(connected: Boolean) = update { it.copy(naviConnected = connected) }

    fun setShortcuts(list: List<Shortcut>) = update {
        // 이름도 주소도 없는 항목(내비 최근 목적지에 가끔 섞임)은 빈 버튼이 되므로 뺀다
        val merged = (it.shortcuts + list).filter { s -> s.name.isNotBlank() }.distinctBy { s -> s.name }.take(4)
        it.copy(shortcuts = merged)
    }

    fun onArrived(name: String) {
        update {
            it.copy(
                phase = Phase.ARRIVED,
                destName = name.ifEmpty { it.destName },
                remainMeters = 0,
                remainSeconds = 0,
                rerouting = false,
            )
        }
    }

    fun onRouteCancelled() {
        update { it.copy(phase = Phase.IDLE, totalMeters = 0, remainMeters = 0, remainSeconds = 0) }
    }

    fun onReroute() {
        update { it.copy(rerouting = true) }
        main.removeCallbacks(clearReroute)
        main.postDelayed(clearReroute, 8_000)
    }

    fun onGear(parked: Boolean) = update { it.copy(parked = parked) }

    fun setDestKind(kind: DestKind) {
        prefs.saveKind(state.destName, kind)
        update { it.copy(destKind = kind) }
    }

    /** 도착 화면을 닫고 처음으로. 완주 도장은 한 번만 찍는다. */
    fun finishTrip() {
        if (state.phase == Phase.ARRIVED) prefs.addStamp()
        update { it.copy(phase = Phase.IDLE, totalMeters = 0, remainMeters = 0, remainSeconds = 0) }
    }
}
