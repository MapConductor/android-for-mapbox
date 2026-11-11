package com.mapconductor.mapbox.raster

import com.mapbox.maps.extension.style.layers.generated.rasterLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.rasterSource
import com.mapconductor.core.circle.CIRCLE_TILE_LAYER_ID
import com.mapconductor.core.controller.OverlayKind
import com.mapconductor.core.polygon.POLYGON_TILE_LAYER_ID
import com.mapconductor.core.polyline.POLYLINE_TILE_LAYER_ID
import com.mapconductor.core.raster.RasterHeaderRuleSet
import com.mapconductor.core.raster.RasterLayerEntityInterface
import com.mapconductor.core.raster.RasterLayerOverlayRendererInterface
import com.mapconductor.core.raster.RasterLayerSource
import com.mapconductor.core.raster.RasterLayerState
import com.mapconductor.core.raster.TileScheme
import com.mapconductor.mapbox.MapboxMapViewHolder
import com.mapconductor.mapbox.MapboxOverlayStacking
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MapboxRasterLayerOverlayRenderer(
    private val holder: MapboxMapViewHolder,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : RasterLayerOverlayRendererInterface<MapboxRasterLayerHandle> {
    private val stateById: MutableMap<String, RasterLayerState> = mutableMapOf()
    private val handleById: MutableMap<String, MapboxRasterLayerHandle> = mutableMapOf()

    private fun isMarkerTileRaster(state: RasterLayerState): Boolean = state.id.startsWith(MARKER_TILE_RASTER_ID_PREFIX)

    private fun isPolygonTileRaster(state: RasterLayerState): Boolean = state.id == POLYGON_TILE_LAYER_ID

    private fun isPolylineTileRaster(state: RasterLayerState): Boolean = state.id == POLYLINE_TILE_LAYER_ID

    private fun isCircleTileRaster(state: RasterLayerState): Boolean = state.id == CIRCLE_TILE_LAYER_ID

    /** Mapbox Android はタイル要求を書き換える公開 API を持たない。 */
    override suspend fun onAdd(
        data: List<RasterLayerOverlayRendererInterface.AddParamsInterface>,
    ): List<MapboxRasterLayerHandle?> =
        data
            .map { params ->
                RasterHeaderRuleSet.warnUnsupported(provider = "Mapbox", state = params.state)
                addLayer(params.state).also { handle ->
                    if (handle != null) {
                        stateById[params.state.id] = params.state
                        handleById[params.state.id] = handle
                    }
                }
            }.also {
                holder.map.style?.let { style -> rebuildNonMarkerRasterLayers(style) }
            }

    override suspend fun onChange(
        data: List<RasterLayerOverlayRendererInterface.ChangeParamsInterface<MapboxRasterLayerHandle>>,
    ): List<MapboxRasterLayerHandle?> =
        data
            .map { params ->
                val prev = params.prev
                val next = params.current.state
                val handle =
                    if (prev.state.source != next.source) {
                        removeLayer(prev)
                        addLayer(next)
                    } else {
                        updateLayer(prev.layer, next)
                        prev.layer
                    }
                if (handle != null) {
                    stateById[next.id] = next
                    handleById[next.id] = handle
                }
                handle
            }.also {
                holder.map.style?.let { style -> rebuildNonMarkerRasterLayers(style) }
            }

    override suspend fun onRemove(data: List<RasterLayerEntityInterface<MapboxRasterLayerHandle>>) {
        data.forEach { entity ->
            stateById.remove(entity.state.id)
            handleById.remove(entity.state.id)
            removeLayer(entity)
        }
        holder.map.style?.let { style -> rebuildNonMarkerRasterLayers(style) }
    }

    override suspend fun onPostProcess() {}

    private fun addLayer(state: RasterLayerState): MapboxRasterLayerHandle? {
        val sourceId = "raster-source-${state.id}"
        val layerId = "raster-layer-${state.id}"
        val source = buildSource(sourceId, state.source) ?: return null
        val handle = MapboxRasterLayerHandle(sourceId = sourceId, layerId = layerId)
        val style = holder.map.style ?: return handle
        val opacity =
            if (state.visible) {
                state.opacity.coerceIn(0.0f, 1.0f).toDouble()
            } else {
                0.0
            }
        val layer =
            rasterLayer(layerId, sourceId) {
                rasterOpacity(opacity)
            }
        try {
            style.addSource(source)
        } catch (e: Exception) {
            Log.w("Mapbox", "Failed to add raster source: ${e.message}")
        }
        addToStack(style, layer, state)
        return handle
    }

    private fun updateLayer(
        handle: MapboxRasterLayerHandle,
        state: RasterLayerState,
    ) {
        val style = holder.map.style ?: return
        MapboxOverlayStacking.remove(holder.map, style, handle.layerId)
        val opacity =
            if (state.visible) {
                state.opacity.coerceIn(0.0f, 1.0f).toDouble()
            } else {
                0.0
            }
        val layer =
            rasterLayer(handle.layerId, handle.sourceId) {
                rasterOpacity(opacity)
            }
        addToStack(style, layer, state)
    }

    private fun removeLayer(entity: RasterLayerEntityInterface<MapboxRasterLayerHandle>) {
        val style = holder.map.style ?: return
        val handle = entity.layer
        MapboxOverlayStacking.remove(holder.map, style, handle.layerId)
        try {
            style.removeStyleSource(handle.sourceId)
        } catch (_: Exception) {
        }
    }

    private fun buildSource(
        sourceId: String,
        source: RasterLayerSource,
    ) = when (source) {
        is RasterLayerSource.UrlTemplate ->
            rasterSource(sourceId) {
                tiles(listOf(source.template))
                tileSize(source.tileSize.toLong())
                // tileCacheBudget(TileCacheBudget(TileCacheBudgetInMegabytes(0L)))
                source.minZoom?.let { minzoom(it.toLong()) }
                source.maxZoom?.let { maxzoom(it.toLong()) }
                if (source.scheme == TileScheme.TMS) {
                    // Mapbox raster sources default to XYZ; TMS is best-effort.
                    // If needed, provide a TMS-compatible URL template instead.
                }
            }
        is RasterLayerSource.TileJson ->
            rasterSource(sourceId) {
                url(source.url)
            }
        is RasterLayerSource.ArcGisService -> {
            val base = source.serviceUrl.trimEnd('/')
            rasterSource(sourceId) {
                tiles(listOf("$base/tile/{z}/{y}/{x}"))
                tileSize(RasterLayerSource.DEFAULT_TILE_SIZE.toLong())
            }
        }
    }

    /**
     * 帯の順（[MapboxOverlayStacking]）で足す。アプリのラスターはラスターの帯
     * （一番下、ベースマップのラベルの下）、マーカーのタイルはマーカーの帯の一番下、
     * ポリゴンのタイルはポリゴンの帯の一番下（画像になったポリゴンであって、地図の画像ではない）。
     */
    private fun addToStack(
        style: com.mapbox.maps.Style,
        layer: com.mapbox.maps.extension.style.layers.Layer,
        state: RasterLayerState,
    ) {
        val key =
            if (isMarkerTileRaster(state)) {
                MapboxOverlayStacking.MARKER_TILE
            } else if (isPolygonTileRaster(state)) {
                MapboxOverlayStacking.POLYGON_TILE
            } else if (isCircleTileRaster(state)) {
                MapboxOverlayStacking.CIRCLE_TILE
            } else if (isPolylineTileRaster(state)) {
                MapboxOverlayStacking.POLYLINE_TILE
            } else {
                MapboxOverlayStacking.key(OverlayKind.RasterLayer, state.zIndex)
            }
        MapboxOverlayStacking.add(holder.map, style, layer, key)
    }

    private fun rebuildNonMarkerRasterLayers(style: com.mapbox.maps.Style) {
        val ordered =
            stateById.values
                .asSequence()
                .filter { !isMarkerTileRaster(it) && !isPolygonTileRaster(it) && !isPolylineTileRaster(it) && !isCircleTileRaster(it) }
                .sortedBy { it.zIndex }
                .mapNotNull { state -> handleById[state.id]?.let { handle -> state to handle } }
                .toList()

        // Remove and re-add non-marker raster layers in zIndex order to ensure deterministic stacking.
        ordered.forEach { (_, handle) ->
            MapboxOverlayStacking.remove(holder.map, style, handle.layerId)
        }

        ordered.forEach { (state, handle) ->
            val opacity =
                if (state.visible) {
                    state.opacity.coerceIn(0.0f, 1.0f).toDouble()
                } else {
                    0.0
                }
            val layer =
                rasterLayer(handle.layerId, handle.sourceId) {
                    rasterOpacity(opacity)
                }
            addToStack(style, layer, state)
        }
    }

    private companion object {
        private const val MARKER_TILE_RASTER_ID_PREFIX = "marker-tile-"
    }
}

data class MapboxRasterLayerHandle(
    val sourceId: String,
    val layerId: String,
)
