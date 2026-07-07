package com.mapconductor.mapbox.polygon

import com.mapconductor.core.polygon.AbstractPolygonOverlayRenderer
import com.mapconductor.core.polygon.PolygonEntityInterface
import com.mapconductor.core.polygon.PolygonManagerInterface
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polygon.unionHoles
import com.mapconductor.mapbox.MapboxActualPolygon
import com.mapconductor.mapbox.MapboxMapViewHolder
import com.mapconductor.mapbox.createMapboxPolygons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MapboxPolygonOverlayRenderer(
    val layer: MapboxPolygonLayer,
    val polygonManager: PolygonManagerInterface<MapboxActualPolygon>,
    override val holder: MapboxMapViewHolder,
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractPolygonOverlayRenderer<MapboxActualPolygon>() {

    override suspend fun onRemove(data: List<PolygonEntityInterface<MapboxActualPolygon>>) {
    }

    override suspend fun onPostProcess() {
        layer.draw(getAllPolygonEntities())
    }

    override suspend fun removePolygon(entity: PolygonEntityInterface<MapboxActualPolygon>) {
    }

    override suspend fun createPolygon(state: PolygonState): MapboxActualPolygon? {
        val resolved = if (state.holes.size > 1) state.unionHoles() else state
        val features = createMapboxPolygons(
            id = resolved.id,
            points = resolved.points,
            holes = resolved.holes,
            geodesic = resolved.geodesic,
            fillColor = resolved.fillColor,
            zIndex = resolved.zIndex,
        )

        if (features.isEmpty()) {
            return null
        }
        return features
    }

    override suspend fun updatePolygonProperties(
        polygon: MapboxActualPolygon,
        current: PolygonEntityInterface<MapboxActualPolygon>,
        prev: PolygonEntityInterface<MapboxActualPolygon>,
    ): MapboxActualPolygon? {
        val finger = current.fingerPrint
        val prevFinger = prev.fingerPrint

        if (finger != prevFinger) {
            return createPolygon(current.state)
        }
        return prev.polygon
    }

    private fun getAllPolygonEntities(): List<PolygonEntityInterface<MapboxActualPolygon>> =
        polygonManager.allEntities()
}
