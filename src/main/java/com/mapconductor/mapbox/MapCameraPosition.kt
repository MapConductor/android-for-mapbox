package com.mapconductor.mapbox

import com.mapbox.maps.CameraChanged
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CameraState
import com.mapbox.maps.EdgeInsets
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapPaddingsInterface
import com.mapconductor.core.map.VisibleRegion
import com.mapconductor.core.spherical.Spherical
import com.mapconductor.mapbox.zoom.ZoomAltitudeConverter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

private val converter = ZoomAltitudeConverter()

fun CameraChanged.toMapCameraPosition() =
    cameraState
        .toMapCameraPosition()
        .copy(paddings = cameraState.padding.toPaddings())

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
        val tiltAbsDeg = abs(tilt).coerceIn(0.0, 60.0)
        val tiltAbsRad = Math.toRadians(tiltAbsDeg)
        val mapboxZoomForAltitude = ZoomAltitudeConverter.googleZoomToMapboxZoom(zoom)
        val altitude = converter.zoomLevelToAltitude(mapboxZoomForAltitude, position.latitude, 0.0)
        val distanceForward = altitude * tan(tiltAbsRad)
        val target = Spherical.computeOffset(position, distanceForward, bearing)
        val mapboxZoom = converter.altitudeToZoomLevel(altitude / cos(tiltAbsRad), target.latitude, 0.0)

        return CameraOptions
            .Builder()
            .center(target.toPoint())
            .zoom(mapboxZoom)
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
    toMapCameraPosition(
        center = center?.toGeoPoint() ?: GeoPoint.fromLongLat(0.0, 0.0),
        mapboxZoom = zoom ?: 2.0,
        bearing = bearing ?: 0.0,
        pitch = pitch ?: 0.0,
        paddings = padding?.toPaddings(),
        logicalTiltHint = null,
    )

fun CameraState.toMapCameraPosition() =
    toMapCameraPosition(
        center = center.toGeoPoint(),
        mapboxZoom = zoom,
        bearing = bearing,
        pitch = pitch,
        logicalTiltHint = null,
    )

internal fun CameraState.toMapCameraPosition(logicalTiltHint: Double?) =
    toMapCameraPosition(
        center = center.toGeoPoint(),
        mapboxZoom = zoom,
        bearing = bearing,
        pitch = pitch,
        logicalTiltHint = logicalTiltHint,
    )

internal data class MapboxCameraStateSnapshot(
    val cameraState: CameraState,
    val logicalTiltHint: Double?,
) {
    fun toMapCameraPosition(): MapCameraPosition = cameraState.toMapCameraPosition(logicalTiltHint)
}

private fun toMapCameraPosition(
    center: GeoPointInterface,
    mapboxZoom: Double,
    bearing: Double,
    pitch: Double,
    paddings: MapPaddingsInterface? = null,
    visibleRegion: VisibleRegion? = null,
    logicalTiltHint: Double? = null,
): MapCameraPosition {
    val pitchAbsDeg = abs(pitch).coerceIn(0.0, 90.0)
    if (logicalTiltHint == null || logicalTiltHint >= 0.0 || pitchAbsDeg == 0.0) {
        return MapCameraPosition(
            position = center,
            zoom = ZoomAltitudeConverter.mapboxZoomToGoogleZoom(mapboxZoom),
            bearing = bearing,
            tilt = pitch,
            paddings = paddings,
            visibleRegion = visibleRegion,
        )
    }

    val pitchAbsRad = Math.toRadians(pitchAbsDeg)
    val shiftedCenter = GeoPoint.from(center)
    val adjustedAltitude = converter.zoomLevelToAltitude(mapboxZoom, shiftedCenter.latitude, 0.0)
    val originalAltitude = adjustedAltitude * cos(pitchAbsRad)
    val distanceBackward = originalAltitude * tan(pitchAbsRad)
    val originalCenter = Spherical.computeOffset(shiftedCenter, distanceBackward, bearing + 180.0)
    val originalMapboxZoom = converter.altitudeToZoomLevel(originalAltitude, originalCenter.latitude, 0.0)

    return MapCameraPosition(
        position = originalCenter,
        zoom = ZoomAltitudeConverter.mapboxZoomToGoogleZoom(originalMapboxZoom),
        bearing = bearing,
        tilt = -pitchAbsDeg,
        paddings = paddings,
        visibleRegion = visibleRegion,
    )
}
