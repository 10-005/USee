package com.usee.animal.ar

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class RoomPetViewManager : SimpleViewManager<RoomPetView>() {
  override fun getName() = "RoomPetView"
  override fun createViewInstance(context: ThemedReactContext) = RoomPetView(context)
  @ReactProp(name = "petId")
  fun setPetId(view: RoomPetView, value: String?) = view.setPetId(value.orEmpty())
  @ReactProp(name = "active")
  fun setActive(view: RoomPetView, value: Boolean) = view.setActive(value)
  override fun onDropViewInstance(view: RoomPetView) {
    view.dispose()
    super.onDropViewInstance(view)
  }
}
