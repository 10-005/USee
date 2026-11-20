package com.usee.animal.location

import android.location.Geocoder
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.Locale

class LocationGeocoderModule(context: ReactApplicationContext) :
  ReactContextBaseJavaModule(context) {

  override fun getName() = "LocationGeocoder"

  @Suppress("DEPRECATION")
  @ReactMethod
  fun searchPlaces(query: String, promise: Promise) {
    Thread {
      try {
        val results = Arguments.createArray()
        if (Geocoder.isPresent()) {
          val places = Geocoder(reactApplicationContext, Locale.KOREA)
            .getFromLocationName(query, 5, 33.0, 124.0, 39.0, 132.0)
          for (place in places.orEmpty()) {
            val address = place.getAddressLine(0)?.trim()?.removePrefix("대한민국 ")
            if (address.isNullOrBlank() || !place.hasLatitude() || !place.hasLongitude()) continue
            results.pushMap(Arguments.createMap().apply {
              putString("placeName", place.featureName)
              putString("roadAddress", address)
              putNull("jibunAddress")
              putDouble("latitude", place.latitude)
              putDouble("longitude", place.longitude)
            })
          }
        }
        promise.resolve(results)
      } catch (error: Exception) {
        promise.reject("PLACE_SEARCH_FAILED", "장소를 검색하지 못했어요. 지도에서 위치를 선택해 주세요.", error)
      }
    }.start()
  }

  @Suppress("DEPRECATION")
  @ReactMethod
  fun reverse(latitude: Double, longitude: Double, promise: Promise) {
    Thread {
      try {
        if (!Geocoder.isPresent()) {
          promise.reject("GEOCODER_UNAVAILABLE", "주소 변환 서비스를 사용할 수 없습니다.")
          return@Thread
        }
        val address = Geocoder(reactApplicationContext, Locale.KOREA)
          .getFromLocation(latitude, longitude, 1)
          ?.firstOrNull()
          ?.getAddressLine(0)
          ?.trim()
        if (address.isNullOrEmpty()) {
          promise.reject("ADDRESS_NOT_FOUND", "선택한 위치의 주소를 찾지 못했습니다.")
        } else {
          promise.resolve(address)
        }
      } catch (error: Exception) {
        promise.reject("REVERSE_GEOCODE_FAILED", "주소를 확인하지 못했습니다.", error)
      }
    }.start()
  }
}
