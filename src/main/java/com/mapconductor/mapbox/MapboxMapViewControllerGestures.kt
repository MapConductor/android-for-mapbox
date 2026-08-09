package com.mapconductor.mapbox

import androidx.compose.ui.geometry.Offset
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.ScreenCoordinate
import com.mapconductor.core.circle.CircleEvent
import com.mapconductor.core.groundimage.GroundImageEvent
import com.mapconductor.core.marker.clickableOnly
import com.mapconductor.core.polygon.PolygonEvent
import com.mapconductor.core.polyline.PolylineEvent
import kotlinx.coroutines.launch

/**
 * 地図のタップ・長押しとマーカーのドラッグ。
 *
 * タップは**マーカーが先**で、どのマーカーにも当たらなかったときだけ地図の
 * タップとして扱う（android の他プロバイダと同じ順序）。
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

internal fun MapboxMapViewController.handleMapClick(point: Point): Boolean {
    val touchPosition = point.toGeoPoint()

    markerEventControllers.forEach { controller ->
        controller.find(touchPosition).clickableOnly()?.let { entity ->
            controller.dispatchClick(entity.state)
            return true
        }
    }

    circleController.find(touchPosition)?.let { entity ->
        val event =
            CircleEvent(
                state = entity.state,
                clicked = touchPosition,
            )
        circleController.dispatchClick(event)
        return true
    }

    groundImageController.find(touchPosition)?.let { entity ->
        val event =
            GroundImageEvent(
                state = entity.state,
                clicked = touchPosition,
            )
        groundImageController.dispatchClick(event)
        return true
    }

    polylineController.findWithClosestPoint(touchPosition)?.let { hitResult ->
        val event =
            PolylineEvent(
                state = hitResult.entity.state,
                clicked = hitResult.closestPoint,
            )
        mainCoroutine.launch {
            polylineController.dispatchClick(event)
        }
        return true
    }

    polygonController.find(touchPosition)?.let { polygonEntity ->
        val event =
            PolygonEvent(
                state = polygonEntity.state,
                clicked = touchPosition,
            )
        polygonController.dispatchClick(event)
        return true
    }

    emitMapClick(touchPosition)
    return true
}

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
        controller.renderer.dragLayer.updatePosition(it)
        controller.renderer.drawDragLayer()
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
    controller.renderer.dragLayer.updatePosition(point.toGeoPoint())
    controller.setSelectedMarker(null)
    controller.dispatchDragEnd(entity.state)
    activeDragController = null
}
