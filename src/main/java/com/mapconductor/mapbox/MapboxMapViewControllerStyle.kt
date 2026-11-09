package com.mapconductor.mapbox

import com.mapbox.maps.extension.style.sources.addSource
import com.mapconductor.core.controller.OverlayKind
import com.mapconductor.mapbox.marker.MapboxMarkerOverlayRenderer

/**
 * スタイル（ソースとレイヤー）の組み立て。
 *
 * Mapbox はスタイルを読み込み直すとレイヤーが消えるため、ここは何度呼ばれても
 * 同じ状態になるように書いてある。ポリゴンの z 順は z 値ごとのレイヤーで表す
 * （Mapbox にフィーチャー単位の z 指定が無く、レイヤーの並び順だけが順序を決めるため）。
 */
internal fun MapboxMapViewController.attachMarkerLayers(
    style: com.mapbox.maps.Style,
    renderer: MapboxMarkerOverlayRenderer,
) {
    try {
        style.addSource(renderer.markerLayer.source)
    } catch (_: Exception) {
    }
    MapboxOverlayStacking.add(holder.map, style, renderer.markerLayer.layer, MapboxOverlayStacking.key(OverlayKind.Marker))
    try {
        style.addSource(renderer.dragLayer.source)
    } catch (_: Exception) {
    }
    MapboxOverlayStacking.add(holder.map, style, renderer.dragLayer.layer, MapboxOverlayStacking.MARKER_DRAG)
}

internal fun MapboxMapViewController.attachOverlaySourcesAndLayers(style: com.mapbox.maps.Style) {
    // Polygon sources only (z-indexed layers added below)
    style.addSource(polygonController.renderer.outline.layer.source)
    style.addSource(polygonController.renderer.fill.layer.source)

    // Circle fill (fill pass) then stroke (line pass) — both below general polyline
    style.addSource(circleController.renderer.layer.source)
    // 層の位置は MapboxOverlayStacking が帯の順で決める（追加の順に依らない）。
    // 線は塗りの直後（同じ鍵なら後から入れたものが上）。
    val circleKey = MapboxOverlayStacking.key(OverlayKind.Circle)
    MapboxOverlayStacking.add(holder.map, style, circleController.renderer.layer.fillLayer, circleKey)
    MapboxOverlayStacking.add(holder.map, style, circleController.renderer.layer.strokeLayer, circleKey)

    // Polyline (general)
    style.addSource(polylineController.renderer.layer.source)
    MapboxOverlayStacking.add(
        holder.map,
        style,
        polylineController.renderer.layer.layer,
        MapboxOverlayStacking.key(OverlayKind.Polyline),
    )

    // Polygon z-layers (polygon band: above ground images, below circles)
    ensurePolygonZLayers(style)

    // Marker + drag layers
    attachMarkerLayers(style, markerController.renderer as MapboxMarkerOverlayRenderer)
    markerEventControllers
        .map { it.renderer as MapboxMarkerOverlayRenderer }
        .filter { it != markerController.renderer }
        .forEach { renderer -> attachMarkerLayers(style, renderer) }
}

internal fun MapboxMapViewController.ensurePolygonZLayers(style: com.mapbox.maps.Style) {
    val fillSourceId = polygonController.renderer.fill.layer.sourceId
    val outlineSourceId = polygonController.renderer.outline.layer.sourceId
    val anchorId = polylineController.renderer.layer.layerId

    val zSet =
        polygonController.polygonManager
            .allEntities()
            .map { it.state.zIndex }
            .toSet()

    // Remove stale z-indexed layers we previously created
    val toRemove = polygonZLayers.subtract(zSet)
    toRemove.forEach { z ->
        val fillId = "polygon-fill-layer-$z"
        val outlineId = "polygon-outline-layer-$z"
        MapboxOverlayStacking.remove(holder.map, style, outlineId)
        MapboxOverlayStacking.remove(holder.map, style, fillId)
    }

    val zList = zSet.toList().sorted()
    zList.forEach { z ->
        val fillId = "polygon-fill-layer-$z"
        val outlineId = "polygon-outline-layer-$z"

        // Fill layer for this z
        if (!style.styleLayerExists(fillId)) {
            val layer =
                com.mapbox.maps.extension.style.layers.generated.fillLayer(fillId, fillSourceId) {
                    filter(
                        com.mapbox.maps.extension.style.expressions.generated.Expression.eq(
                            com.mapbox.maps.extension.style.expressions.generated.Expression
                                .get("zIndex"),
                            com.mapbox.maps.extension.style.expressions.generated.Expression
                                .literal(z.toDouble()),
                        ),
                    )
                    fillColor(
                        com.mapbox.maps.extension.style.expressions.generated.Expression
                            .get("fillColor"),
                    )
                }
            MapboxOverlayStacking.add(holder.map, style, layer, MapboxOverlayStacking.key(OverlayKind.Polygon, z))
        }

        // Outline layer above its fill
        if (!style.styleLayerExists(outlineId)) {
            val layer =
                com.mapbox.maps.extension.style.layers.generated.lineLayer(outlineId, outlineSourceId) {
                    lineJoin(com.mapbox.maps.extension.style.layers.properties.generated.LineJoin.ROUND)
                    lineCap(com.mapbox.maps.extension.style.layers.properties.generated.LineCap.ROUND)
                    filter(
                        com.mapbox.maps.extension.style.expressions.generated.Expression.eq(
                            com.mapbox.maps.extension.style.expressions.generated.Expression
                                .get("zIndex"),
                            com.mapbox.maps.extension.style.expressions.generated.Expression
                                .literal(z.toDouble()),
                        ),
                    )
                    lineColor(
                        com.mapbox.maps.extension.style.expressions.generated.Expression
                            .get("strokeColor"),
                    )
                    lineWidth(
                        com.mapbox.maps.extension.style.expressions.generated.Expression
                            .get("strokeWidth"),
                    )
                }
            // 塗りの直後（同じ鍵なら後から入れたものが上）。
            MapboxOverlayStacking.add(holder.map, style, layer, MapboxOverlayStacking.key(OverlayKind.Polygon, z))
        }
    }
    // Update tracked set
    polygonZLayers.clear()
    polygonZLayers.addAll(zSet)
}
