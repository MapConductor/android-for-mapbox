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
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.mapbox.MapboxActualCircle
import com.mapconductor.mapbox.MapboxMapViewHolder
import com.mapconductor.mapbox.toMapboxColorString
import com.mapconductor.mapbox.toPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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

    private fun createCirclePolygon(state: CircleState): Polygon {
        val center = GeoPoint.from(state.center).toPoint()
        val lat = center.latitude()
        val lng = center.longitude()
        val segments = 64
        val latCorrection = if (state.geodesic) cos(Math.toRadians(lat)) else 1.0
        val metersPerDegree = 111320.0

        val ring = (0 until segments).map { i ->
            val angle = 2.0 * PI * i / segments
            val deltaLat = state.radiusMeters / metersPerDegree * cos(angle)
            val deltaLng = state.radiusMeters / (metersPerDegree * latCorrection) * sin(angle)
            Point.fromLngLat(lng + deltaLng, lat + deltaLat)
        }.toMutableList()
        ring.add(ring.first())

        return Polygon.fromLngLats(listOf(ring))
    }
}
