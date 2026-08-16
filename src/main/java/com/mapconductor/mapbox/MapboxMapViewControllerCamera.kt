package com.mapconductor.mapbox

import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.buildVisibleRegion
import com.mapconductor.mapbox.zoom.ZoomAltitudeConverter
import android.animation.Animator
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * カメラの読み書きと初回通知。
 *
 * 論理ズーム（Google 基準）と Mapbox のズームのずれは
 * [com.mapconductor.mapbox.zoom.ZoomAltitudeConverter] で吸収する。
 */
internal fun MapboxMapViewController.getMapCameraPosition(): MapCameraPosition? {
    val camera = readLogicalCameraPosition()
    // 4 隅の逆投影は全プロバイダ共通なのでコアの buildVisibleRegion を使う。
    val visibleRegion = holder.buildVisibleRegion() ?: return null
    val mapCameraPosition = camera.copy(visibleRegion = visibleRegion)
    lastLogicalCameraPosition = mapCameraPosition
    return mapCameraPosition
}

internal fun MapboxMapViewController.readLogicalCameraPosition(): MapCameraPosition =
    MapboxCameraStateSnapshot(
        cameraState = holder.map.cameraState,
        logicalTiltHint = lastLogicalCameraPosition?.tilt,
    ).toMapCameraPosition()

/**
 * Async version of getMapCameraPosition that can be called from background threads.
 * Uses withContext to ensure Mapbox SDK calls run on the main thread.
 */
internal suspend fun MapboxMapViewController.getMapCameraPositionAsync(): MapCameraPosition? =
    withContext(mainCoroutine.coroutineContext) {
        getMapCameraPosition()
    }

internal fun MapboxMapViewController.handleMoveCamera(position: MapCameraPosition) {
    val cameraOptions = position.toCameraOptions()
    lastLogicalCameraPosition = position
    mainCoroutine.launch {
        holder.map.setCamera(cameraOptions)
    }
}

internal fun MapboxMapViewController.handleAnimateCamera(
    position: MapCameraPosition,
    duration: Long,
) {
    val targetCamera = position.toCameraOptions()
    lastLogicalCameraPosition = position

    val animationOptions =
        MapAnimationOptions
            .Builder()
            .duration(duration)
            .build()

    val animatorListener =
        object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                emitCameraMoveStart(position)
            }

            override fun onAnimationEnd(animation: Animator) {
            }

            override fun onAnimationCancel(animation: Animator) {
                val mapCameraPosition = readLogicalCameraPosition()
                lastLogicalCameraPosition = mapCameraPosition
                emitCameraMove(mapCameraPosition)
            }

            override fun onAnimationRepeat(animation: Animator) {
                // Do nothing here
            }
        }

    mainCoroutine.launch {
        holder.map.flyTo(
            cameraOptions = targetCamera,
            animationOptions = animationOptions,
            animatorListener = animatorListener,
        )
    }
}

internal fun MapboxMapViewController.handleCameraRestriction(restriction: CameraRestriction?) {
    val builder = CameraBoundsOptions.Builder()
    builder.bounds(restriction?.bounds?.toGeoBox())
    // 統一ズーム（Google 準拠）を Mapbox ズームへ変換して適用。
    builder.minZoom(restriction?.minZoom?.let { ZoomAltitudeConverter.googleZoomToMapboxZoom(it) })
    builder.maxZoom(restriction?.maxZoom?.let { ZoomAltitudeConverter.googleZoomToMapboxZoom(it) })
    mainCoroutine.launch {
        holder.map.setBounds(builder.build())
    }
}

internal fun MapboxMapViewController.handleFitBounds(
    bounds: GeoRectBounds,
    padding: Int,
) {
    val coordinateBounds = bounds.toGeoBox() ?: return
    val edgeInsets =
        com.mapbox.maps.EdgeInsets(
            padding.toDouble(),
            padding.toDouble(),
            padding.toDouble(),
            padding.toDouble(),
        )
    val cameraOptions = holder.map.cameraForCoordinateBounds(coordinateBounds, edgeInsets)
    lastLogicalCameraPosition = cameraOptions.toMapCameraPosition()
    mainCoroutine.launch {
        holder.map.setCamera(cameraOptions)
    }
}

// Trigger an initial camera update after the view and style are ready
internal fun MapboxMapViewController.sendInitialCameraUpdate() {
    mainCoroutine.launch {
        if (!involvedMapInitializedCallback) {
            involvedMapInitializedCallback = true
            emitMapInitialized()
        }
        val mapWidth = holder.mapView.width.toFloat()
        val mapHeight = holder.mapView.height.toFloat()
        if (mapWidth <= 0 || mapHeight <= 0) return@launch

        val camera = readLogicalCameraPosition()
        val visibleRegion = holder.buildVisibleRegion() ?: return@launch
        val mapCameraPosition = camera.copy(visibleRegion = visibleRegion)
        lastLogicalCameraPosition = mapCameraPosition

        defaultCoroutine.launch { emitCameraPosition(mapCameraPosition) }
    }
}
