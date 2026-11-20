package com.usee.animal.ar

import io.github.sceneview.SceneView
import io.github.sceneview.model.ModelInstance
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Only file IO runs off-thread. Creation, callbacks and destruction share the UI thread. */
internal class SceneModelLoad {
  private var job: Job? = null

  fun start(view: SceneView, onLoaded: (ModelInstance) -> Unit) {
    cancel()
    job = CoroutineScope(Dispatchers.Main.immediate).launch {
      val buffer = withContext(Dispatchers.IO) {
        view.context.assets.open("models/dog.glb").use {
          ByteBuffer.wrap(it.readBytes())
        }
      }
      // Cancellation during IO prevents any access to the released engine.
      onLoaded(view.modelLoader.createModelInstance(buffer))
    }
  }

  fun cancel() {
    job?.cancel()
    job = null
  }
}
