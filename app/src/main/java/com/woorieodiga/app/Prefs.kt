package com.woorieodiga.app

import android.content.Context

enum class RideType { UNICORN, TRAIN }

/** 탈것 색. 그림 이름과 1:1 이다. */
enum class Ride(val type: RideType, val drawable: Int, val label: String) {
    UNICORN_WHITE(RideType.UNICORN, R.drawable.ride_unicorn, "하얀 유니콘"),
    UNICORN_SKY(RideType.UNICORN, R.drawable.ride_unicorn_sky, "하늘 유니콘"),
    UNICORN_YELLOW(RideType.UNICORN, R.drawable.ride_unicorn_yellow, "노랑 유니콘"),
    UNICORN_PINK(RideType.UNICORN, R.drawable.ride_unicorn_pink, "분홍 유니콘"),
    TRAIN_RED(RideType.TRAIN, R.drawable.ride_train, "빨간 꼬마 기차"),
    TRAIN_BLUE(RideType.TRAIN, R.drawable.ride_train_blue, "파란 꼬마 기차"),
    TRAIN_YELLOW(RideType.TRAIN, R.drawable.ride_train_yellow, "노란 꼬마 기차"),
    TRAIN_PINK(RideType.TRAIN, R.drawable.ride_train_pink, "분홍 꼬마 기차");

    val mini: Int get() = if (type == RideType.UNICORN) R.drawable.mini_unicorn else R.drawable.mini_train
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("woori", Context.MODE_PRIVATE)

    var ride: Ride
        get() = runCatching { Ride.valueOf(sp.getString("ride", null)!!) }.getOrDefault(Ride.UNICORN_WHITE)
        set(value) = sp.edit().putString("ride", value.name).apply()

    val stamps: Int get() = sp.getInt("stamps", 0)

    fun addStamp() = sp.edit().putInt("stamps", stamps + 1).apply()

    /** 부모가 바꾼 목적지 그림을 목적지 이름별로 기억한다. 없으면 이름으로 짐작한다. */
    fun kindFor(name: String): DestKind =
        sp.getString("kind:$name", null)?.let { runCatching { DestKind.valueOf(it) }.getOrNull() }
            ?: guessKind(name)

    fun saveKind(name: String, kind: DestKind) = sp.edit().putString("kind:$name", kind.name).apply()

    fun totalFor(name: String): Int? = sp.getInt("total:$name", -1).takeIf { it > 0 }

    fun saveTotal(name: String, meters: Int) = sp.edit().putInt("total:$name", meters).apply()

    companion object {
        // 앞에서부터 먼저 걸리는 것: "롯데월드 아쿠아리움"은 동물원, "키즈카페"는 놀이공원보다 먼저
        private val RULES = listOf(
            DestKind.HOME to listOf("우리집", "우리 집", "집으로", "자택"),
            DestKind.GRANDMA to listOf("할머니", "할아버지", "본가", "처가", "시댁", "친정", "외가", "시골"),
            DestKind.ZOO to listOf("동물원", "아쿠아리움", "수족관", "아쿠아플라넷", "사파리", "주렁주렁"),
            DestKind.KIDS_CAFE to listOf("키즈카페", "키즈파크", "키즈", "실내놀이터", "플레이파크"),
            DestKind.CAMPING to listOf("캠핑", "글램핑", "카라반", "야영", "휴양림", "계곡"),
            DestKind.BEACH to listOf("해수욕장", "해변", "바다", "비치", "해안", "항구", "해양"),
            DestKind.AMUSEMENT to listOf("랜드", "월드", "놀이공원", "놀이동산", "테마파크"),
            DestKind.MART to listOf("마트", "이마트", "홈플러스", "코스트코", "트레이더스", "아울렛", "백화점", "스타필드"),
            DestKind.FRIEND to listOf("친구"),
        )

        fun guessKind(name: String): DestKind =
            if (name.trim() == "집") DestKind.HOME
            else RULES.firstOrNull { (_, words) -> words.any { it in name } }?.first ?: DestKind.OTHER
    }
}
