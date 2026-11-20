package com.usee.animal.ar

import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Size
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.safeDestroyTexture
import io.github.sceneview.texture.ImageTexture

internal class OwnedImageNode(
  private val materialLoader: MaterialLoader,
  imageFileLocation: String,
  size: Size,
  normal: Direction,
  private val texture: Texture = ImageTexture.Builder()
    .bitmap(materialLoader.assets, imageFileLocation)
    .build(materialLoader.engine),
  private val imageMaterial: MaterialInstance = materialLoader.createImageInstance(texture),
) : PlaneNode(engine = materialLoader.engine, size = size, normal = normal, materialInstance = imageMaterial) {
  private var released = false

  override fun destroy() {
    if (released) return
    released = true
    // Filament requires the renderable to release its material before the texture is destroyed.
    engine.renderableManager.destroy(entity)
    super.destroy()
    // The loader tracks the original Java wrapper returned at creation.
    materialLoader.destroyMaterialInstance(imageMaterial)
    engine.safeDestroyTexture(texture)
  }
}
