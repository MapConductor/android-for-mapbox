package com.mapconductor.mapbox.polygon

import com.mapconductor.core.polygon.PolygonEntityInterface
import com.mapconductor.core.polygon.PolygonOverlayRendererInterface
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polyline.PolylineEntity
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.mapbox.MapboxActualPolygon
import com.mapconductor.mapbox.polyline.MapboxPolylineOverlayRenderer

/**
 * 塗りと輪郭を 1 つのレンダラとして [com.mapconductor.core.polygon.PolygonController] に渡すための合成レンダラ。
 *
 * fill レイヤーは幅 1 px の輪郭しか描けないので、`strokeWidth` を守るために輪郭は
 * 別のポリライン（`outline-<id>`）として [outline] で描く。差分計算はコアの
 * PolygonController に任せ、ここは「塗りへの指示を輪郭にも写す」ことだけをする。
 *
 * 塗りのエンティティはコントローラと同じ [fill] の polygonManager に載る。輪郭は
 * [outline] の polylineManager に載り、コントローラからは見えない。
 */
class MapboxPolygonCompositeRenderer(
    val fill: MapboxPolygonOverlayRenderer,
    val outline: MapboxPolylineOverlayRenderer,
) : PolygonOverlayRendererInterface<MapboxActualPolygon> {
    override suspend fun onAdd(data: List<PolygonOverlayRendererInterface.AddParamsInterface>): List<MapboxActualPolygon?> {
        val polygons = fill.onAdd(data)
        data.forEachIndexed { index, params ->
            if (polygons[index] != null) putOutline(params.state)
        }
        return polygons
    }

    override suspend fun onChange(
        data: List<PolygonOverlayRendererInterface.ChangeParamsInterface<MapboxActualPolygon>>,
    ): List<MapboxActualPolygon?> {
        val polygons = fill.onChange(data)
        data.forEachIndexed { index, params ->
            if (polygons[index] != null) putOutline(params.current.state)
        }
        return polygons
    }

    override suspend fun onRemove(data: List<PolygonEntityInterface<MapboxActualPolygon>>) {
        data.forEach { outline.polylineManager.removeEntity(outlineId(it.state.id)) }
        // clear() は onPostProcess を呼ばないので、消した結果はここで描き直す。
        fill.onRemove(data)
        outline.onPostProcess()
    }

    override suspend fun onPostProcess() {
        fill.onPostProcess()
        outline.onPostProcess()
    }

    private suspend fun putOutline(state: PolygonState) {
        val outlineState = state.toOutlineState() ?: return
        outline.createPolyline(outlineState)?.let { polyline ->
            outline.polylineManager.registerEntity(PolylineEntity(polyline = polyline, state = outlineState))
        }
    }
}

private fun outlineId(polygonId: String): String = "outline-$polygonId"

/** 外周だけを閉じたポリラインにする（穴の輪郭は描かない）。 */
private fun PolygonState.toOutlineState(): PolylineState? {
    if (points.isEmpty()) return null
    val closedPoints = if (points.first() != points.last()) points + points.first() else points
    return PolylineState(
        points = closedPoints,
        id = outlineId(id),
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        geodesic = geodesic,
        zIndex = zIndex,
        extra = extra,
    )
}
