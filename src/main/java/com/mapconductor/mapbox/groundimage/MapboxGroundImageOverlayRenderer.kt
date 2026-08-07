package com.mapconductor.mapbox.groundimage

import com.mapbox.bindgen.DataRef
import com.mapbox.maps.Image
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.rasterLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.imageSource
import com.mapbox.maps.extension.style.utils.TypeUtils
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.groundimage.AbstractGroundImageOverlayRenderer
import com.mapconductor.core.groundimage.GroundImageEntityInterface
import com.mapconductor.core.groundimage.GroundImageFingerPrint
import com.mapconductor.core.groundimage.GroundImageState
import com.mapconductor.mapbox.MapboxActualGroundImage
import com.mapconductor.mapbox.MapboxMapViewHolder
import java.nio.ByteBuffer
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapboxGroundImageOverlayRenderer(
    override val holder: MapboxMapViewHolder,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractGroundImageOverlayRenderer<MapboxActualGroundImage>() {
    override suspend fun createGroundImage(state: GroundImageState): MapboxActualGroundImage? =
        withContext(coroutine.coroutineContext) {
            val style = holder.map.style ?: return@withContext null
            val coordinates = state.bounds.toImageCoordinates() ?: return@withContext null
            val handle =
                MapboxGroundImageHandle(
                    sourceId = sourceId(state.id),
                    layerId = layerId(state.id),
                    applied = state.fingerPrint().toAppliedGroundImage(),
                )

            removeSourceAndLayerIfExists(style, handle)
            addSourceAndLayer(style, handle, state, coordinates)
            handle
        }

    @SuppressLint("RestrictedApi")
    override suspend fun updateGroundImageProperties(
        groundImage: MapboxActualGroundImage,
        current: GroundImageEntityInterface<MapboxActualGroundImage>,
        prev: GroundImageEntityInterface<MapboxActualGroundImage>,
    ): MapboxActualGroundImage? =
        withContext(coroutine.coroutineContext) {
            val style = holder.map.style ?: return@withContext groundImage

            if (!style.styleSourceExists(groundImage.sourceId) || !style.styleLayerExists(groundImage.layerId)) {
                removeSourceAndLayerIfExists(style, groundImage)
                return@withContext createGroundImage(current.state)
            }

            val finger = current.fingerPrint
            val prevFinger = groundImage.applied
            val coordinates = current.state.bounds.toImageCoordinates() ?: return@withContext groundImage

            if (finger.image != prevFinger.image) {
                style.updateStyleImageSourceImage(
                    groundImage.sourceId,
                    current.state.image
                        .toBitmap()
                        .toMapboxImage(),
                )
                style.setStyleSourceProperty(
                    groundImage.sourceId,
                    "coordinates",
                    TypeUtils.wrapToValue(coordinates),
                )
            } else if (finger.bounds != prevFinger.bounds) {
                style.setStyleSourceProperty(
                    groundImage.sourceId,
                    "coordinates",
                    TypeUtils.wrapToValue(coordinates),
                )
            }

            if (finger.opacity != prevFinger.opacity) {
                style.setStyleLayerProperty(
                    groundImage.layerId,
                    "raster-opacity",
                    TypeUtils.wrapToValue(
                        current.state.opacity
                            .coerceIn(0.0f, 1.0f)
                            .toDouble(),
                    ),
                )
            }

            groundImage.copy(applied = finger.toAppliedGroundImage())
        }

    override suspend fun removeGroundImage(entity: GroundImageEntityInterface<MapboxActualGroundImage>) {
        coroutine.launch {
            val style = holder.map.style ?: return@launch
            removeSourceAndLayerIfExists(style, entity.groundImage)
        }
    }

    private fun addSourceAndLayer(
        style: Style,
        handle: MapboxGroundImageHandle,
        state: GroundImageState,
        coordinates: List<List<Double>>,
    ) {
        val source =
            imageSource(handle.sourceId) {
                coordinates(coordinates)
            }

        val layer =
            rasterLayer(handle.layerId, handle.sourceId) {
                rasterOpacity(state.opacity.coerceIn(0.0f, 1.0f).toDouble())
                visibility(Visibility.VISIBLE)
            }

        try {
            style.addSource(source)
            style.updateStyleImageSourceImage(handle.sourceId, state.image.toBitmap().toMapboxImage())
        } catch (e: Exception) {
            Log.w("Mapbox", "Failed to add ground image source: ${e.message}")
        }

        try {
            style.addLayerBelow(layer, BELOW_LAYER_ID)
        } catch (_: Exception) {
            try {
                style.addLayer(layer)
            } catch (e: Exception) {
                Log.w("Mapbox", "Failed to add ground image layer: ${e.message}")
            }
        }
    }

    private fun removeSourceAndLayerIfExists(
        style: Style,
        handle: MapboxGroundImageHandle,
    ) {
        try {
            style.removeStyleLayer(handle.layerId)
        } catch (_: Exception) {
        }
        try {
            style.removeStyleSource(handle.sourceId)
        } catch (_: Exception) {
        }
    }

    private fun GeoRectBounds.toImageCoordinates(): List<List<Double>>? {
        val sw = southWest ?: return null
        val ne = northEast ?: return null
        return listOf(
            listOf(sw.longitude, ne.latitude),
            listOf(ne.longitude, ne.latitude),
            listOf(ne.longitude, sw.latitude),
            listOf(sw.longitude, sw.latitude),
        )
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap
        }

        val width = intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = Rect(bounds)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bounds = oldBounds
        return bitmap
    }

    private fun Bitmap.toMapboxImage(): Image {
        val src = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        val buffer = ByteBuffer.allocateDirect(pixels.size * 4)
        pixels.forEach { argb ->
            buffer.put(((argb shr 16) and 0xff).toByte())
            buffer.put(((argb shr 8) and 0xff).toByte())
            buffer.put((argb and 0xff).toByte())
            buffer.put(((argb ushr 24) and 0xff).toByte())
        }
        buffer.flip()
        return Image(src.width, src.height, DataRef(buffer))
    }

    private fun sourceId(id: String): String = "mc-gimg-src-${id.toStyleIdPart()}"

    private fun layerId(id: String): String = "mc-gimg-lyr-${id.toStyleIdPart()}"

    private fun String.toStyleIdPart(): String =
        buildString(length) {
            this@toStyleIdPart.forEach { ch ->
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch == '-' || ch == '_' -> append(ch)
                    else -> append('_')
                }
            }
        }

    private fun GroundImageFingerPrint.toAppliedGroundImage(): AppliedGroundImage =
        AppliedGroundImage(
            bounds = bounds,
            image = image,
            opacity = opacity,
        )

    companion object {
        private const val BELOW_LAYER_ID = "polyline-layer"
    }
}
