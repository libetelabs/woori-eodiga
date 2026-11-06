package com.woorieodiga.app

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 기어가 P 인지 본다(AAOS 표준 GEAR_SELECTION).
 * 조작(탈것 고르기, 바로 출발, 도착 축하)은 P 일 때만 연다.
 * 기어를 못 읽는 환경이면 정차로 본다.
 */
class GearSource(private val context: Context, private val engine: TripEngine) {
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Int)?.let { engine.onGear(it == VehicleGear.GEAR_PARK) }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "GEAR_SELECTION 오류 $propertyId")
        }
    }

    /**
     * Car 는 메인 스레드에서 만지지 않는다. 차량 서비스가 바쁘면 getCarManager 가 수십 초 걸리고,
     * 그동안 Car 내부 잠금이 잡혀 메인이 같이 멈춰 앱 시작이 ANR 로 죽는다(에뮬레이터에서 확인).
     * 바쁘면 createCar 가 5초 뒤 null 을 주기도 해서, 붙을 때까지 다시 시도한다(그동안은 정차로 본다).
     */
    fun start() = Thread({
        for (attempt in 1..RETRIES) {
            try {
                val c = Car.createCar(context.applicationContext) ?: error("Car 없음")
                val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                m.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
                car = c
                manager = m
                Log.i(TAG, "GEAR_SELECTION 구독 시작($attempt 번째)")
                return@Thread
            } catch (e: Throwable) {
                Log.w(TAG, "기어 정보를 아직 못 읽음($attempt/$RETRIES), 정차로 봅니다: ${e.message}")
                if (attempt == 1) engine.onGear(true)
                Thread.sleep(RETRY_MS)
            }
        }
    }, "woori-gear").start()

    fun stop() {
        try {
            manager?.unregisterCallback(callback)
            car?.disconnect()
        } catch (_: Throwable) {
        }
        manager = null
        car = null
    }

    companion object {
        private const val TAG = "WooriGear"
        private const val RETRIES = 20
        private const val RETRY_MS = 3_000L
    }
}
