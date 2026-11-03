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
  @ReactProp(name = "returningFromWalk")
  fun setReturningFromWalk(view: RoomPetView, value: Boolean) = view.setReturningFromWalk(value)
  override fun onAfterUpdateTransaction(view: RoomPetView) {
    super.onAfterUpdateTransaction(view)
    view.onPropsUpdated()
  }
  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
    mutableMapOf(
      "topReady" to mutableMapOf("registrationName" to "onReady"),
      "topPetPress" to mutableMapOf("registrationName" to "onPetPress"),
      "topHidden" to mutableMapOf("registrationName" to "onHidden"),
    )
  override fun onDropViewInstance(view: RoomPetView) {
    view.dispose()
    super.onDropViewInstance(view)
  }
}
