package com.mapconductor.mapbox.circle

import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.extension.style.expressions.generated.Expression.Companion.get
import com.mapbox.maps.extension.style.layers.generated.fillLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapconductor.core.circle.CircleEntityInterface
import com.mapconductor.mapbox.MapboxActualCircle

class MapboxCircleLayer(
    val sourceId: String,
    val layerId: String,
) {
    val fillLayerId = "$layerId-fill"
    val strokeLayerId = "$layerId-stroke"

    object Prop {
        const val FILL_COLOR = "fillColor"
        const val STROKE_COLOR = "strokeColor"
        const val STROKE_WIDTH = "strokeWidth"
        const val Z_INDEX = "zIndex"
    }

    val source = geoJsonSource(sourceId)

    val fillLayer =
        fillLayer(fillLayerId, sourceId) {
            fillColor(get { literal(Prop.FILL_COLOR) })
            fillSortKey(get { literal(Prop.Z_INDEX) })
        }

    val strokeLayer =
        lineLayer(strokeLayerId, sourceId) {
            lineJoin(LineJoin.ROUND)
            lineCap(LineCap.ROUND)
            lineColor(get { literal(Prop.STROKE_COLOR) })
            lineWidth(get { literal(Prop.STROKE_WIDTH) })
            lineSortKey(get { literal(Prop.Z_INDEX) })
        }

    fun draw(entities: List<CircleEntityInterface<MapboxActualCircle>>) {
        source.featureCollection(FeatureCollection.fromFeatures(entities.map { it.circle }))
    }
}
