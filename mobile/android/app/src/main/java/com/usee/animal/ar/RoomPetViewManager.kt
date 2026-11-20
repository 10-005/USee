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
  @ReactProp(name = "careMotion")
  fun setCareMotion(view: RoomPetView, value: String?) = view.setCareMotion(value.orEmpty())
  @ReactProp(name = "bathing")
  fun setBathing(view: RoomPetView, value: Boolean) = view.setBathing(value)
  @ReactProp(name = "sceneOpacity", defaultFloat = 1f)
  fun setSceneOpacity(view: RoomPetView, value: Float) = view.setSceneOpacity(value)
  @ReactProp(name = "returningFromWalk")
  fun setReturningFromWalk(view: RoomPetView, value: Boolean) = view.setReturningFromWalk(value)
  @ReactProp(name = "entryX")
  fun setEntryX(view: RoomPetView, value: Float) = view.setEntryX(value)
  @ReactProp(name = "entryY")
  fun setEntryY(view: RoomPetView, value: Float) = view.setEntryY(value)
  override fun onAfterUpdateTransaction(view: RoomPetView) {
    super.onAfterUpdateTransaction(view)
    view.onPropsUpdated()
  }
  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
    mutableMapOf(
      "topReady" to mutableMapOf("registrationName" to "onReady"),
      "topReturnComplete" to mutableMapOf("registrationName" to "onReturnComplete"),
      "topPetPress" to mutableMapOf("registrationName" to "onPetPress"),
      "topHidden" to mutableMapOf("registrationName" to "onHidden"),
    )
  override fun onDropViewInstance(view: RoomPetView) {
    view.dispose()
    super.onDropViewInstance(view)
  }
}
