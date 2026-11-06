package com.woorieodiga.app

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/**
 * 순정 내비(NaviHelper)에서 목적지와 남은 거리·시간을 읽는다.
 * 목적지는 우리 앱에서 입력받지 않는다. 운전자가 내비에 찍은 목적지를 그대로 쓴다.
 * NaviHelper 호출은 전용 스레드에서만 한다. 내비가 바쁘면 연결이 수십 초 걸려 메인에서 부르면 앱 시작이 ANR 로 죽는다.
 */
@SuppressLint("MissingPermission")
class NaviSource(private val context: Context, private val engine: TripEngine) : NaviHelperEventListener {
    private val worker = Handler(HandlerThread("woori-navi").apply { start() }.looper)
    private lateinit var navi: NaviHelper
    @Volatile private var polling = false

    /** 남은 거리는 이벤트가 따로 없어 화면이 보일 때만 20초마다 묻는다. */
    private val poll = object : Runnable {
        override fun run() {
            safe { navi.getDestinationInfo() }
            worker.postDelayed(this, POLL_MS)
        }
    }

    fun start() = worker.post {
        navi = NaviHelper(context.applicationContext)
        navi.addListener(this)
        navi.initialize(
            onConnected = {
                engine.setNaviConnected(true)
                safe { navi.getDestinationInfo() }
                safe { navi.getBookmarkInfo() }
                safe { navi.getRecentDestinationInfo() }
            },
            onFailed = {
                Log.w(TAG, "NaviHelper 연결 실패", it)
                engine.setNaviConnected(false)
            },
        )
    }

    fun stop() {
        setPolling(false)
        worker.post {
            navi.removeListener(this)
            navi.release()
        }
    }

    fun setPolling(on: Boolean) {
        if (on == polling) return
        polling = on
        worker.removeCallbacks(poll)
        if (on) worker.post(poll)
    }

    /** 즐겨찾기·최근 목적지로 길안내 시작. 정차 중에만 부른다. */
    fun startRoute(s: Shortcut) = worker.post { safe {
        navi.requestRoute(
            RouteInfo(
                latitude = s.latitude,
                longitude = s.longitude,
                displayLatitude = s.displayLatitude,
                displayLongitude = s.displayLongitude,
                poiName = s.name,
                poiId = s.poiId,
                address = s.address,
                poiSubId = s.poiSubId,
                poiProvider = s.poiProvider,
            )
        )
    } }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) =
        engine.onRouteStarted(routeStartInfo.name, routeStartInfo.distance, routeStartInfo.duration)

    override fun onDestinationInfo(destinationInfo: DestinationInfo) =
        engine.onRemaining(destinationInfo.name, destinationInfo.remainDistance, destinationInfo.remainTime)

    override fun onDrivingInfo(drivingInfo: DrivingInfo) =
        engine.onRemainTime(drivingInfo.destination.duration)

    override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
        engine.onArrived(destinationArrivedInfo.name)

    override fun onRouteCancelled() = engine.onRouteCancelled()

    override fun onReRouteEnded() = engine.onReroute()

    override fun onNavigationStatus(status: NavigationStatus) {
        if (status == NavigationStatus.ROUTE_DRIVE) worker.post { safe { navi.getDestinationInfo() } }
    }

    override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) = engine.setShortcuts(
        bookmarkInfoList.map {
            Shortcut(it.name.ifBlank { it.address }, it.address, it.latitude, it.longitude, it.displayLatitude, it.displayLongitude,
                it.poiId, it.poiSubId.orEmpty(), it.poiProvider)
        }
    )

    override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) = engine.setShortcuts(
        recentDestinationInfoList.map {
            Shortcut(it.name.ifBlank { it.address }, it.address, it.latitude, it.longitude, it.displayLatitude, it.displayLongitude,
                it.poiId, it.poiSubId.orEmpty(), it.poiProvider)
        }
    )

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "NaviHelper 호출 실패", e)
        }
    }

    companion object {
        private const val TAG = "WooriNavi"
        private const val POLL_MS = 20_000L
    }
}
