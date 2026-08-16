package com.mapconductor.mapbox

import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerAbove
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.sources.addSource
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
    try {
        style.addLayer(renderer.markerLayer.layer)
    } catch (_: Exception) {
    }
    try {
        style.addSource(renderer.dragLayer.source)
    } catch (_: Exception) {
    }
    try {
        style.addLayer(renderer.dragLayer.layer)
    } catch (_: Exception) {
    }
}

internal fun MapboxMapViewController.attachOverlaySourcesAndLayers(style: com.mapbox.maps.Style) {
    // Polygon sources only (z-indexed layers added below)
    style.addSource(polygonController.polylineOverlay.layer.source)
    style.addSource(polygonController.polygonOverlay.layer.source)

    // Circle fill (fill pass) then stroke (line pass) — both below general polyline
    style.addSource(circleController.renderer.layer.source)
    style.addLayer(circleController.renderer.layer.fillLayer)
    style.addLayer(circleController.renderer.layer.strokeLayer)

    // Polyline (general)
    style.addSource(polylineController.renderer.layer.source)
    style.addLayer(polylineController.renderer.layer.layer)

    // Add z-indexed polygon layers below general polylines
    ensurePolygonZLayers(style)

    // Marker + drag layers
    attachMarkerLayers(style, markerController.renderer as MapboxMarkerOverlayRenderer)
    markerEventControllers
        .map { it.renderer as MapboxMarkerOverlayRenderer }
        .filter { it != markerController.renderer }
        .forEach { renderer -> attachMarkerLayers(style, renderer) }
}

internal fun MapboxMapViewController.ensurePolygonZLayers(style: com.mapbox.maps.Style) {
    val fillSourceId = polygonController.polygonOverlay.layer.sourceId
    val outlineSourceId = polygonController.polylineOverlay.layer.sourceId
    val anchorId = polylineController.renderer.layer.layerId

    val zSet =
        polygonController.polygonOverlay.polygonManager
            .allEntities()
            .map { it.state.zIndex }
            .toSet()

    // Remove stale z-indexed layers we previously created
    val toRemove = polygonZLayers.subtract(zSet)
    toRemove.forEach { z ->
        val fillId = "polygon-fill-layer-$z"
        val outlineId = "polygon-outline-layer-$z"
        try {
            style.removeStyleLayer(outlineId)
        } catch (_: Exception) {
        }
        try {
            style.removeStyleLayer(fillId)
        } catch (_: Exception) {
        }
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
            try {
                style.addLayerBelow(layer, anchorId)
            } catch (_: Exception) {
                style.addLayer(layer)
            }
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
            try {
                style.addLayerAbove(layer, fillId)
            } catch (_: Exception) {
                style.addLayer(layer)
            }
        }
    }
    // Update tracked set
    polygonZLayers.clear()
    polygonZLayers.addAll(zSet)
}
