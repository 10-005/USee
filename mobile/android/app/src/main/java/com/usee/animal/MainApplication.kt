package com.usee.animal

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.usee.animal.ar.ArFloorPackage
import com.usee.animal.location.LocationGeocoderPackage
import com.usee.animal.notification.PushBannerChannels
import com.usee.animal.notification.PushBannerPackage

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(ArFloorPackage())
          add(PullRefreshPackage())
          add(LocationGeocoderPackage())
          add(PushBannerPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    PushBannerChannels.ensure(this)
    loadReactNative(this)
  }
}
