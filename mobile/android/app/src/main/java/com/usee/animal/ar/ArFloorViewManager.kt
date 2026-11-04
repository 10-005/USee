package com.usee.animal.ar

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class ArFloorViewManager : SimpleViewManager<ArFloorView>() {
  override fun getName(): String = NAME

  override fun createViewInstance(reactContext: ThemedReactContext): ArFloorView =
    ArFloorView(reactContext)

  override fun onDropViewInstance(view: ArFloorView) {
    view.dispose()
    super.onDropViewInstance(view)
  }

  @ReactProp(name = "petName")
  fun setPetName(view: ArFloorView, name: String?) {
    view.setPetName(name.orEmpty())
  }

  @ReactProp(name = "active", defaultBoolean = true)
  fun setActive(view: ArFloorView, active: Boolean) {
    view.setActive(active)
  }

  @ReactProp(name = "showTraversalDebug", defaultBoolean = true)
  fun setShowTraversalDebug(view: ArFloorView, visible: Boolean) {
    view.setShowTraversalDebug(visible)
  }

  @ReactProp(name = "placementRequest", defaultInt = 0)
  fun setPlacementRequest(view: ArFloorView, request: Int) {
    view.requestPlacement(request)
  }

  @ReactProp(name = "actionRequest")
  fun setActionRequest(view: ArFloorView, request: String?) {
    view.requestAction(request.orEmpty())
  }

  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
    mutableMapOf("topTracking" to mutableMapOf("registrationName" to "onTracking"))

  private companion object {
    const val NAME = "ArFloorView"
  }
}
