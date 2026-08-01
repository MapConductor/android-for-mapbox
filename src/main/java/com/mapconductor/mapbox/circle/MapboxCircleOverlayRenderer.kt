package com.mapconductor.mapbox.circle

import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.extension.style.sources.removeGeoJSONSourceFeatures
import com.mapconductor.core.calculateZIndex
import com.mapconductor.core.circle.AbstractCircleOverlayRenderer
import com.mapconductor.core.circle.CircleEntityInterface
import com.mapconductor.core.circle.CircleManagerInterface
import com.mapconductor.core.circle.CircleState
import com.mapconductor.core.geometry.circleToRing
import com.mapconductor.core.geometry.closeRing
import com.mapconductor.mapbox.MapboxActualCircle
import com.mapconductor.mapbox.MapboxMapViewHolder
import com.mapconductor.mapbox.toMapboxColorString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapboxCircleOverlayRenderer(
    val layer: MapboxCircleLayer =
        MapboxCircleLayer(
            sourceId = "circles-source",
            layerId = "circles-layer",
        ),
    val circleManager: CircleManagerInterface<MapboxActualCircle>,
    override val holder: MapboxMapViewHolder,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractCircleOverlayRenderer<MapboxActualCircle>() {
    override suspend fun removeCircle(entity: CircleEntityInterface<MapboxActualCircle>) {
        val featureIds = listOf("circle-${entity.state.id}")
        layer.source.removeGeoJSONSourceFeatures(featureIds)
    }

    override suspend fun createCircle(state: CircleState): MapboxActualCircle? =
        Feature.fromGeometry(
            createCirclePolygon(state),
            JsonObject().apply {
                addProperty(MapboxCircleLayer.Prop.FILL_COLOR, state.fillColor.toMapboxColorString())
                addProperty(MapboxCircleLayer.Prop.STROKE_COLOR, state.strokeColor.toMapboxColorString())
                addProperty(MapboxCircleLayer.Prop.STROKE_WIDTH, state.strokeWidth.value)
                addProperty(MapboxCircleLayer.Prop.Z_INDEX, state.zIndex ?: calculateZIndex(state.center))
            },
            "circle-${state.id}",
        )

    override suspend fun updateCircleProperties(
        circle: MapboxActualCircle,
        current: CircleEntityInterface<MapboxActualCircle>,
        prev: CircleEntityInterface<MapboxActualCircle>,
    ): MapboxActualCircle? {
        val state = current.state
        return Feature.fromGeometry(
            createCirclePolygon(state),
            JsonObject().apply {
                addProperty(MapboxCircleLayer.Prop.FILL_COLOR, state.fillColor.toMapboxColorString())
                addProperty(MapboxCircleLayer.Prop.STROKE_COLOR, state.strokeColor.toMapboxColorString())
                addProperty(MapboxCircleLayer.Prop.STROKE_WIDTH, state.strokeWidth.value)
                addProperty(MapboxCircleLayer.Prop.Z_INDEX, state.zIndex ?: calculateZIndex(state.center))
            },
            "circle-${state.id}",
        )
    }

    override suspend fun onPostProcess() {
        val circles = circleManager.allEntities()
        coroutine.launch {
            layer.draw(circles)
        }
    }

    /**
     * コア共通の [circleToRing] でリングを生成する。リングは中心経度まわりに連続化
     * （unwrap）されており、Mapbox GL は ±180 を超える経度を扱えるため、±180 を跨ぐ円も
     * 分割せず 1 枚の Polygon として描画できる（子午線の継ぎ目が出ない）。
     */
    private fun createCirclePolygon(state: CircleState): Polygon {
        val ring = circleToRing(state.center, state.radiusMeters, state.geodesic)
        val closed = closeRing(ring.map { Point.fromLngLat(it.longitude, it.latitude) })
        return Polygon.fromLngLats(listOf(closed))
    }
}
