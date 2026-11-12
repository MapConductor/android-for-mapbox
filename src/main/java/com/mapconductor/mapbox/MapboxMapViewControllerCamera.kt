package com.mapconductor.mapbox

import com.mapbox.maps.CameraBoundsOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import android.view.View
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.map.CameraPaddingEmulation
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapPaddings
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
    CameraPaddingEmulation.fromNativeCamera(
        MapboxCameraStateSnapshot(
            cameraState = holder.map.cameraState,
            logicalTiltHint = lastLogicalCameraPosition?.tilt,
            density = density(),
        ).toMapCameraPosition(),
        cameraPaddings,
        MAPBOX_VERTICAL_FOV,
        viewportHeightDp(),
    )

/** 地図の高さ（dp）。パディングのエミュレーションで遠近を解くのに使う。 */
internal fun MapboxMapViewController.viewportHeightDp(): Double = holder.mapView.height / density().toDouble()

/**
 * パディングを中心のずらしに置き換えたカメラ。Mapbox のパディングは常に 0 にする。
 *
 * ネイティブのパディングは視錐台を上へ広げる。tilt があると上のパディングが
 * 大きいほど画面の上端が地平線の先まで届き、そこまでタイルを読みに行って固まる。
 * 中心をずらせば、パディング無しと同じ視錐台のまま target をパディング中央に置ける。
 * paddings が null なら今のパディングを使い続ける。
 */
internal fun MapboxMapViewController.toNativeCamera(position: MapCameraPosition): MapCameraPosition {
    position.paddings?.let { cameraPaddings = it }
    return CameraPaddingEmulation
        .toNativeCamera(position.copy(paddings = cameraPaddings), MAPBOX_VERTICAL_FOV, viewportHeightDp())
        .copy(paddings = MapPaddings.Zeros)
}

/** 地図がまだ大きさを持たない（レイアウト前）ときは、パディングを解けないので最初のレイアウトまで待つ。 */
private fun MapboxMapViewController.needsLayoutFor(position: MapCameraPosition): Boolean {
    val paddings = position.paddings ?: cameraPaddings
    val padded = paddings.top != 0.0 || paddings.left != 0.0 || paddings.bottom != 0.0 || paddings.right != 0.0
    return padded && holder.mapView.height <= 0
}

private fun MapboxMapViewController.afterFirstLayout(action: () -> Unit) {
    holder.mapView.addOnLayoutChangeListener(
        object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                if (bottom - top <= 0) return
                v.removeOnLayoutChangeListener(this)
                action()
            }
        },
    )
}

internal fun MapboxMapViewController.density(): Float = holder.mapView.resources.displayMetrics.density

/**
 * Async version of getMapCameraPosition that can be called from background threads.
 * Uses withContext to ensure Mapbox SDK calls run on the main thread.
 */
internal suspend fun MapboxMapViewController.getMapCameraPositionAsync(): MapCameraPosition? =
    withContext(mainCoroutine.coroutineContext) {
        getMapCameraPosition()
    }

internal fun MapboxMapViewController.handleMoveCamera(position: MapCameraPosition) {
    lastLogicalCameraPosition = position
    mainCoroutine.launch {
        if (needsLayoutFor(position)) {
            afterFirstLayout { handleMoveCamera(position) }
            return@launch
        }
        holder.map.setCamera(toNativeCamera(position).toCameraOptions(density()))
    }
}

internal fun MapboxMapViewController.handleAnimateCamera(
    position: MapCameraPosition,
    duration: Long,
) {
    if (needsLayoutFor(position)) {
        afterFirstLayout { handleAnimateCamera(position, duration) }
        return
    }
    val targetCamera = toNativeCamera(position).toCameraOptions(density())
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
