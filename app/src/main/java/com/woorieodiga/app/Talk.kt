package com.woorieodiga.app

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener
import android.content.Context
import android.util.Log
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** "언제 도착해?"의 답. 숫자 대신 아이가 아는 길이(만화, 동요)로 말한다. */
data class TimeAnswer(val icon: Int, val title: String, val speech: String)

object Copy {
    private val NATIVE = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")

    /** 만화 한 편 ≈ 11분, 동요 한 곡 ≈ 3분. */
    fun timeAnswer(remainSeconds: Int, destKind: DestKind): TimeAnswer {
        val min = ceil(remainSeconds / 60.0).toInt()
        val to = if (destKind == DestKind.OTHER) "도착" else destKind.label
        return when {
            min >= 8 -> {
                val n = (min / 11.0).roundToInt().coerceIn(1, 10)
                val word = NATIVE[n] + " 편"
                TimeAnswer(
                    if (n >= 2) R.drawable.time_cartoon_2 else R.drawable.time_cartoon_1,
                    "만화 $word 보면 도착해",
                    "${to}까지 만화 $word 남았어!",
                )
            }
            min >= 2 -> {
                val n = ceil(min / 3.0).toInt().coerceIn(1, 3)
                val word = NATIVE[n] + " 곡"
                TimeAnswer(R.drawable.time_songs_3, "동요 $word 부르면 도착해", "${to}까지 동요 $word 남았어!")
            }
            else -> TimeAnswer(GOAL_ICON, "거의 다 왔어!", "거의 다 왔어! 곧 ${iya(to)}!")
        }
    }

    /** 받침에 맞춰 "이야/야": 우리 집이야, 마트야. */
    fun iya(word: String): String {
        val c = word.lastOrNull() ?: return word
        val batchim = c in '가'..'힣' && (c - '가') % 28 != 0
        return word + if (batchim) "이야" else "야"
    }

    /** 부모용 작은 글씨. */
    fun detail(remainSeconds: Int, remainMeters: Int): String {
        val min = ceil(remainSeconds / 60.0).toInt()
        val km = remainMeters / 1000.0
        return "약 ${min}분 · ${"%.1f".format(km)}km"
    }

    fun stageSpeech(stage: Stage): String = when (stage) {
        Stage.NEIGHBORHOOD -> "우리 동네를 지나고 있어"
        Stage.CITY -> "큰 도시를 지나고 있어"
        Stage.FIELD -> "넓은 들판과 강을 지나고 있어"
        Stage.MOUNTAIN -> "산길을 지나고 있어"
        Stage.DESTINATION -> "거의 다 왔어"
    }

    /** 목적지 그림은 하나로 통일한다(어디든 "도착 지점"). 종류는 이름과 말에만 쓴다. */
    val GOAL_ICON = R.drawable.ic_stop_goal
    val GOAL_BG = R.drawable.bg_arrival_celebration

    fun stageBg(stage: Stage) = when (stage) {
        Stage.NEIGHBORHOOD -> R.drawable.bg_01_neighborhood
        Stage.CITY -> R.drawable.bg_02_city
        Stage.FIELD -> R.drawable.bg_03_field_river
        Stage.MOUNTAIN -> R.drawable.bg_04_mountain
        Stage.DESTINATION -> GOAL_BG
    }

    val stops = intArrayOf(
        R.drawable.ic_stop_house,
        R.drawable.ic_stop_city,
        R.drawable.ic_stop_bridge,
        R.drawable.ic_stop_mountain,
        GOAL_ICON,
    )
}

/**
 * 안내 음성. Pleos 이미지엔 안드로이드 기본 TTS 엔진이 없어 Pleos TextToSpeech 를 쓴다.
 *  - 지금은 ON_DEVICE(기기 안 합성, 등록 불필요). 언어는 ko_KR 이어야 한다. 에뮬레이터에서 소리 확인(10-30).
 *  - 서버 합성은 My Project 에서 TextToSpeech 가 "준비 중"이라 키를 넣어도 401 이 난다.
 *    SERVER_TTS_OPEN 을 켜면 키(local.properties 의 pleos.clientId/clientSecret)로 HYBRID 를 쓴다.
 */
class Voice(context: Context) {
    private val hasKey = SERVER_TTS_OPEN &&
        BuildConfig.PLEOS_CLIENT_ID.isNotEmpty() && BuildConfig.PLEOS_CLIENT_SECRET.isNotEmpty()
    private val tts = TextToSpeech(context.applicationContext, if (hasKey) Mode.HYBRID else Mode.ON_DEVICE)
    @Volatile private var ready = false

    // initialize 가 끝나기 전에 addEventListener 를 부르면 SDK 안에서 죽는다(에뮬레이터 확인)
    private val events = object : EventListener {
        override fun onStart() = Log.d(TAG, "말하기 시작").let {}
        override fun onReady() {}
        override fun onDone() = Log.d(TAG, "말하기 끝").let {}
        override fun onStop() {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "음성 오류 $errorType")
        }
    }

    init {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.addEventListener(events)
                    tts.setLocale(Locale.KOREA)  // "ko" 만 주면 기기 안 엔진이 unsupported locale(ko-) 로 실패, ko_KR 이어야 함
                    if (hasKey) register() else markReady("기기 안 합성")
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "음성 초기화 실패 $errorType")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "음성을 쓸 수 없음", e)
        }
    }

    /** 서버 방식은 onConnected 뒤에만 요청할 수 있다. */
    private fun register() = tts.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET,
        object : OnServerConnectionListener {
            override fun onConnected() = markReady("서버 합성")
            override fun onFailed(errorType: ErrorType) {
                Log.w(TAG, "음성 서버 등록 실패 $errorType")
            }
        })

    private fun markReady(how: String) {
        ready = true
        Log.i(TAG, "음성 준비됨($how)")
    }

    /** 말할 수 없으면 false. 화면 글씨로 대신한다. */
    fun say(text: String): Boolean {
        if (!ready) return false
        try {
            tts.stop()
            tts.speak(text, true)
        } catch (e: Exception) {
            Log.w(TAG, "말하기 실패", e)
            return false
        }
        return true
    }

    fun release() = try {
        tts.release()
    } catch (_: Exception) {
    }

    companion object {
        private const val TAG = "WooriVoice"
        /** Pleos 가 TextToSpeech 신청을 열고 승인되면 true(그 전엔 401 뒤 기기 안으로 넘어가며 1초쯤 늦어진다). */
        private const val SERVER_TTS_OPEN = false
    }
}
