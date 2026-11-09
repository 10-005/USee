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

  @ReactProp(name = "active", defaultBoolean = true)
  fun setActive(view: ArFloorView, active: Boolean) {
    view.setActive(active)
  }

  @ReactProp(name = "showTraversalDebug", defaultBoolean = false)
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

  @ReactProp(name = "careRequest")
  fun setCareRequest(view: ArFloorView, request: String?) {
    view.requestCare(request.orEmpty())
  }

  @ReactProp(name = "bagAvailable", defaultBoolean = false)
  fun setBagAvailable(view: ArFloorView, available: Boolean) { view.setBagAvailable(available) }

  @ReactProp(name = "cleanupResponse")
  fun setCleanupResponse(view: ArFloorView, response: String?) { view.confirmCleanup(response.orEmpty()) }

  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
    mutableMapOf(
      "topTracking" to mutableMapOf("registrationName" to "onTracking"),
      "topBagPurchase" to mutableMapOf("registrationName" to "onBagPurchase"),
    )

  private companion object {
    const val NAME = "ArFloorView"
  }
}
