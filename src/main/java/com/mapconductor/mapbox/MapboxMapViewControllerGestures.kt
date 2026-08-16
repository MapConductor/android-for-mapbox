package com.mapconductor.mapbox

import androidx.compose.ui.geometry.Offset
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.ScreenCoordinate

/**
 * 地図のタップ・長押しとマーカーのドラッグ。
 *
 * タップのカスケード（marker → circle → groundImage → polyline → polygon → map）は
 * コアの [com.mapconductor.core.controller.BaseMapViewController.dispatchTap] が回すので、
 * ここは座標を変換して渡すだけ。長押しはドラッグ開始の判定が要るのでここに残す。
 */
internal fun MapboxMapViewController.handleMapLongClick(point: Point): Boolean {
    val touchPosition = point.toGeoPoint()
    markerEventControllers.forEach { controller ->
        controller.find(touchPosition)?.let { entity ->
            if (entity.state.draggable) {
                activeDragController = controller
                controller.setSelectedMarker(entity)
                controller.dispatchDragStart(entity.state)
                return true
            }
        }
    }

    emitMapLongClick(touchPosition)
    return true
}

internal fun MapboxMapViewController.handleMapClick(point: Point): Boolean = dispatchTap(point.toGeoPoint())

internal fun MapboxMapViewController.handleMove(detector: MoveGestureDetector): Boolean {
    val controller = activeDragController ?: return false
    val entity = controller.getSelectedMarker() ?: return false
    val screenCoordinate =
        Offset(
            detector.focalPoint.x,
            detector.focalPoint.y,
        )

    holder.fromScreenOffsetSync(screenCoordinate)?.let {
        entity.state.position = it
        controller.updateDragPosition(it)
    }

    controller.dispatchDrag(entity.state)
    return true
}

internal fun MapboxMapViewController.handleMoveBegin(detector: MoveGestureDetector) {
    // Do nothing here
}

internal fun MapboxMapViewController.handleMoveEnd(detector: MoveGestureDetector) {
    val controller = activeDragController ?: return
    val entity = controller.getSelectedMarker() ?: return
    val screenCoordinate =
        ScreenCoordinate(
            detector.focalPoint.x.toDouble(),
            detector.focalPoint.y.toDouble(),
        )
    val point = holder.map.coordinateForPixel(screenCoordinate)
    controller.updateDragPosition(point.toGeoPoint())
    controller.setSelectedMarker(null)
    controller.dispatchDragEnd(entity.state)
    activeDragController = null
}
