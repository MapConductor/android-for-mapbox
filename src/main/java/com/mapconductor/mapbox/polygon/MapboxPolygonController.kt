package com.mapconductor.mapbox.polygon

import com.mapconductor.core.polygon.PolygonController
import com.mapconductor.mapbox.MapboxActualPolygon

/**
 * Mapbox 用のポリゴンコントローラ。
 *
 * 追加・更新・削除の差分計算、`has`、クリックカスケードはコアの [PolygonController] が担う。
 * 塗りと輪郭（幅のある LineLayer）の 2 系統の描画は [MapboxPolygonCompositeRenderer] が束ねる。
 */
class MapboxPolygonController(
    override val renderer: MapboxPolygonCompositeRenderer,
) : PolygonController<MapboxActualPolygon>(renderer.fill.polygonManager, renderer) {
    // 旧 MapboxPolygonConductor と同じ値を保つ。
    override val zIndex: Int = 2

    override fun destroy() {
        super.destroy()
        // The outlines live in the outline renderer's manager, which core does not
        // know about; a destroyed controller that is still referenced keeps none.
        renderer.outline.polylineManager.clear()
    }
}
