package com.woorieodiga.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

enum class VideoKind(val label: String) {
    CARTOON("만화"),
    ANIME("애니"),
    SONG("동요"),
}

/** 유튜브 공식 채널의 플레이리스트 하나. 목록 근거는 docs/youtube-kids-list.md. */
data class KidsVideo(val kind: VideoKind, val title: String, val url: String)

/**
 * 차에 깔린 유튜브 앱을 플레이리스트 주소로 연다. 다 보면 아래 앱 막대에서 우리 앱을 누르거나 뒤로 가기로 돌아온다.
 * 여행은 App 의 TripEngine 이 계속 따라가므로, 돌아오면 그 사이 진행이 그대로 보인다.
 * 영상은 정차 중에만 고를 수 있게 화면에서 막는다(주행 중 영상은 유튜브 앱·차가 따로 막는다).
 */
object KidsVideos {
    private const val TAG = "WooriVideo"
    private const val YOUTUBE = "com.google.android.youtube"

    val all: List<KidsVideo> = KidsVideoList.items

    fun of(kind: VideoKind) = all.filter { it.kind == kind }

    /** 유튜브 앱이 있으면 그 앱으로, 없으면 주소를 받을 수 있는 아무 앱으로. 둘 다 없으면 false. */
    fun open(context: Context, video: KidsVideo): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val app = Intent(intent).setPackage(YOUTUBE)
        for (candidate in listOf(app, intent)) {
            try {
                context.startActivity(candidate)
                Log.i(TAG, "열기: ${video.title} (${candidate.`package` ?: "아무 앱"})")
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        Log.w(TAG, "주소를 열 앱이 없음: ${video.url}")
        return false
    }
}
