package com.mapconductor.mapbox

import com.mapbox.maps.CameraChanged
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CameraState
import com.mapbox.maps.EdgeInsets
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.spherical.Spherical
import com.mapconductor.mapbox.zoom.ZoomAltitudeConverter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

private val converter = ZoomAltitudeConverter()
private const val NEGATIVE_TILT_TARGET_DISTANCE_SCALE = 1.83
private const val NEGATIVE_TILT_ZOOM_OFFSET_AT_MAX_TILT = -0.9

fun CameraChanged.toMapCameraPosition() =
    CameraOptions
        .Builder()
        .padding(cameraState.padding)
        .center(cameraState.center)
        .zoom(ZoomAltitudeConverter.mapboxZoomToGoogleZoom(cameraState.zoom))
        .bearing(cameraState.bearing)
        .pitch(cameraState.pitch)
        .build()

fun MapCameraPosition.toCameraOptions(): CameraOptions {
    if (this.tilt >= 0) {
        return CameraOptions
            .Builder()
            .center(GeoPoint.from(position).toPoint())
            .zoom(ZoomAltitudeConverter.googleZoomToMapboxZoom(zoom))
            .pitch(tilt)
            .bearing(bearing)
            // TODO:
            //    .padding(paddings?.toEdgeInsects())
            .build()
    } else {
        // tilt < 0: Mapbox cannot represent an upward pitch directly.
        // Match the Google Maps workaround: keep the virtual eye direction by moving
        // the ground target forward and rendering with abs(tilt).
        val tiltAbsDeg = abs(tilt).coerceIn(0.0, 90.0)
        val tiltAbsRad = Math.toRadians(tiltAbsDeg)
        val mapboxZoomForAltitude = ZoomAltitudeConverter.googleZoomToMapboxZoom(zoom)
        val altitude = converter.zoomLevelToAltitude(mapboxZoomForAltitude, position.latitude, 0.0)
        val distanceForward =
            altitude *
                cos(tiltAbsRad) *
                tan(tiltAbsRad) *
                NEGATIVE_TILT_TARGET_DISTANCE_SCALE
        val target = Spherical.computeOffset(position, distanceForward, bearing)
        val adjustedZoom = zoom + NEGATIVE_TILT_ZOOM_OFFSET_AT_MAX_TILT * (tiltAbsDeg / 90.0)

        return CameraOptions
            .Builder()
            .center(target.toPoint())
            .zoom(ZoomAltitudeConverter.googleZoomToMapboxZoom(adjustedZoom))
            .pitch(tiltAbsDeg)
            .bearing(bearing)
            // TODO:
            //    .padding(paddings?.toEdgeInsects())
            .build()
    }
}

fun MapCameraPosition.toCameraState(): CameraState =
    CameraState(
        GeoPoint.from(position).toPoint(),
        EdgeInsets(0.0, 0.0, 0.0, 0.0),
        ZoomAltitudeConverter.googleZoomToMapboxZoom(zoom),
        bearing,
        tilt,
    )

fun MapCameraPosition.Companion.from(cameraPosition: MapCameraPositionInterface) =
    when (cameraPosition) {
        is MapCameraPosition -> cameraPosition
        else ->
            MapCameraPosition(
                position = cameraPosition.position,
                zoom = cameraPosition.zoom,
                bearing = cameraPosition.bearing,
                tilt = cameraPosition.tilt,
                paddings = MapboxPaddings.from(cameraPosition.paddings),
                visibleRegion = cameraPosition.visibleRegion,
            )
    }

fun CameraOptions.toMapCameraPosition() =
    MapCameraPosition(
        position = center?.toGeoPoint() ?: GeoPoint.fromLongLat(0.0, 0.0),
        zoom = ZoomAltitudeConverter.mapboxZoomToGoogleZoom(zoom ?: 2.0),
        bearing = bearing ?: 0.0,
        tilt = pitch ?: 0.0,
        paddings = padding?.toPaddings(),
        visibleRegion = null,
    )

fun CameraState.toMapCameraPosition() =
    MapCameraPosition(
        position = center.toGeoPoint(),
        zoom = ZoomAltitudeConverter.mapboxZoomToGoogleZoom(zoom),
        bearing = bearing,
        tilt = pitch,
        visibleRegion = null,
    )
