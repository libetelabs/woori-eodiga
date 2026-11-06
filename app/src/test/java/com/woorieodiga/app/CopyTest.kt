package com.woorieodiga.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CopyTest {
    @Test
    fun timeAnswerUsesCartoonsThenSongs() {
        assertEquals("만화 세 편 보면 도착해", Copy.timeAnswer(35 * 60, DestKind.GRANDMA).title)
        assertEquals("만화 두 편 보면 도착해", Copy.timeAnswer(22 * 60, DestKind.GRANDMA).title)
        assertEquals("만화 한 편 보면 도착해", Copy.timeAnswer(10 * 60, DestKind.GRANDMA).title)
        assertEquals("동요 세 곡 부르면 도착해", Copy.timeAnswer(7 * 60, DestKind.GRANDMA).title)
        assertEquals("동요 한 곡 부르면 도착해", Copy.timeAnswer(2 * 60, DestKind.GRANDMA).title)
        assertEquals("거의 다 왔어!", Copy.timeAnswer(50, DestKind.GRANDMA).title)
    }

    @Test
    fun speechNamesDestination() {
        assertEquals("바다까지 만화 한 편 남았어!", Copy.timeAnswer(12 * 60, DestKind.BEACH).speech)
        assertEquals("도착까지 동요 두 곡 남았어!", Copy.timeAnswer(5 * 60, DestKind.OTHER).speech)
        assertEquals("우리 집까지 만화 한 편 남았어!", Copy.timeAnswer(12 * 60, DestKind.HOME).speech)
        assertEquals("거의 다 왔어! 곧 마트야!", Copy.timeAnswer(60, DestKind.MART).speech)
        assertEquals("거의 다 왔어! 곧 우리 집이야!", Copy.timeAnswer(60, DestKind.HOME).speech)
    }

    @Test
    fun longTripsCapAtTen() {
        assertEquals("만화 열 편 보면 도착해", Copy.timeAnswer(300 * 60, DestKind.GRANDMA).title)
    }

    @Test
    fun stagesFollowProgress() {
        assertEquals(Stage.NEIGHBORHOOD, Stage.of(0f))
        assertEquals(Stage.CITY, Stage.of(0.3f))
        assertEquals(Stage.FIELD, Stage.of(0.6f))
        assertEquals(Stage.MOUNTAIN, Stage.of(0.9f))
        assertEquals(Stage.DESTINATION, Stage.of(0.95f))
        assertEquals(Stage.DESTINATION, Stage.of(1f))
    }

    @Test
    fun guessesDestinationPicture() {
        assertEquals(DestKind.BEACH, Prefs.guessKind("해운대해수욕장"))
        assertEquals(DestKind.AMUSEMENT, Prefs.guessKind("에버랜드"))
        assertEquals(DestKind.GRANDMA, Prefs.guessKind("시골 할머니댁"))
        assertEquals(DestKind.MART, Prefs.guessKind("이마트 성수점"))
        assertEquals(DestKind.HOME, Prefs.guessKind("집"))
        assertEquals(DestKind.ZOO, Prefs.guessKind("롯데월드 아쿠아리움"))
        assertEquals(DestKind.KIDS_CAFE, Prefs.guessKind("점프키즈카페 판교점"))
        assertEquals(DestKind.CAMPING, Prefs.guessKind("가평 글램핑"))
        assertEquals(DestKind.OTHER, Prefs.guessKind("판교역"))
    }

    @Test
    fun progressFromRemaining() {
        val s = TripState(phase = Phase.ROUTING, totalMeters = 10_000, remainMeters = 4_000)
        assertEquals(0.6f, s.progress, 0.0001f)
        assertEquals(Stage.FIELD, s.stage)
        assertEquals(Stage.DESTINATION, s.copy(phase = Phase.ARRIVED).stage)
    }
}
