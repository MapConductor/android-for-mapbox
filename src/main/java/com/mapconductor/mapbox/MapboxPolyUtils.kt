package com.mapconductor.mapbox

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Polygon as MBPolygon
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.geometry.buildUnwrappedPolygonRings
import com.mapconductor.core.geometry.buildUnwrappedPolylinePath
import com.mapconductor.core.geometry.closeRing
import com.mapconductor.mapbox.polygon.MapboxPolygonLayer
import com.mapconductor.mapbox.polyline.MapboxPolylineLayer

internal fun createMapboxLines(
    id: String,
    points: List<GeoPointInterface>,
    geodesic: Boolean,
    strokeColor: Color,
    strokeWidth: Dp,
    zIndex: Int = 0,
): List<Feature> {
    // unwrap 座標の単一パス。Mapbox GL は ±180 超の経度を扱えるため分割不要（継ぎ目が出ない）。
    val path = buildUnwrappedPolylinePath(points, geodesic)
    if (path.size < 2) return emptyList()
    val pts = path.map { GeoPoint.from(it).toPoint() }
    val fid = "polyline-$id-0"
    return listOf(
        Feature.fromGeometry(
            LineString.fromLngLats(pts),
            JsonObject().apply {
                addProperty(MapboxPolylineLayer.Prop.STROKE_COLOR, strokeColor.toMapboxColorString())
                addProperty(MapboxPolylineLayer.Prop.STROKE_WIDTH, strokeWidth.value)
                addProperty("zIndex", zIndex)
                addProperty("id", fid)
            },
            fid,
        ),
    )
}

internal fun createMapboxPolygons(
    id: String,
    points: List<GeoPointInterface>,
    holes: List<List<GeoPointInterface>> = emptyList(),
    geodesic: Boolean,
    fillColor: Color,
    zIndex: Int,
): List<Feature> {
    // unwrap 座標の外周 1 リング + 全穴。Mapbox GL は ±180 超の経度を扱えるため分割不要で、
    // ±180 跨ぎのポリゴンでも穴を保持できる。
    val polygonRings = buildUnwrappedPolygonRings(points, holes, geodesic)
    val outer = polygonRings.outerRings.firstOrNull() ?: return emptyList()
    val holeRings =
        polygonRings.holeRings.mapNotNull { hole ->
            val closed = closeRing(hole.map { GeoPoint.from(it).toPoint() })
            if (closed.size < 4) null else closed
        }

    val closed = closeRing(outer.map { GeoPoint.from(it).toPoint() })
    val fid = "polygon-$id-0"
    return listOf(
        Feature.fromGeometry(
            MBPolygon.fromLngLats(listOf(closed) + holeRings),
            JsonObject().apply {
                addProperty(MapboxPolygonLayer.Prop.FILL_COLOR, fillColor.toMapboxColorString())
                addProperty("zIndex", zIndex)
                addProperty("id", fid)
            },
            fid,
        ),
    )
}
