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
    // 消えた分を除いた残りで描き直す。コントローラはこの呼び出しの後で polygonManager から
    // 外すので（clear() では onPostProcess も来ない）、ここで除外しておかないと消えずに残る。
    override suspend fun onRemove(data: List<PolygonEntityInterface<MapboxActualPolygon>>) {
        layer.draw(getRemainingPolygonEntities(data))
    }

    override suspend fun onPostProcess() {
        layer.draw(getAllPolygonEntities())
    }

    override suspend fun removePolygon(entity: PolygonEntityInterface<MapboxActualPolygon>) {
    }

    override suspend fun createPolygon(state: PolygonState): MapboxActualPolygon? {
        val resolved = if (state.holes.size > 1) state.unionHoles() else state
        val features =
            createMapboxPolygons(
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

    // Polygons drawn as tiles (PolygonController.tiler) stay in the manager, for
    // taps, with no features: their entity's polygon is null.
    @Suppress("USELESS_CAST")
    private fun getAllPolygonEntities(): List<PolygonEntityInterface<MapboxActualPolygon>> =
        polygonManager.allEntities().filter { (it.polygon as MapboxActualPolygon?) != null }

    private fun getRemainingPolygonEntities(
        removed: List<PolygonEntityInterface<MapboxActualPolygon>>,
    ): List<PolygonEntityInterface<MapboxActualPolygon>> {
        val removedIds = removed.mapTo(HashSet()) { it.state.id }
        return getAllPolygonEntities().filter { it.state.id !in removedIds }
    }
}
