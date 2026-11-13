package com.mapconductor.mapbox

import com.mapbox.maps.CameraChanged
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CameraState
import com.mapbox.maps.EdgeInsets
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.map.CameraBearing
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapPaddings
import com.mapconductor.core.map.MapPaddingsInterface
import com.mapconductor.core.map.VisibleRegion
import com.mapconductor.core.spherical.Spherical
import com.mapconductor.mapbox.zoom.ZoomAltitudeConverter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tan

private val converter = ZoomAltitudeConverter()

/**
 * Mapbox のカメラの縦方向の視野角（度）。Mapbox Maps の既定値で、地図の大きさに
 * よらず一定（焦点距離 = 地図の高さ × 1.5）。TB520FU で 36.9° を実測。
 */
internal const val MAPBOX_VERTICAL_FOV = 36.87

/** dp のパディング → Mapbox の px のパディング。 */
internal fun MapPaddingsInterface.toMapboxEdgeInsets(density: Float): EdgeInsets =
    EdgeInsets(top * density, left * density, bottom * density, right * density)

/** Mapbox の px のパディング → dp のパディング。 */
internal fun EdgeInsets.toMapPaddings(density: Float): MapPaddings =
    MapPaddings(top = top / density, left = left / density, bottom = bottom / density, right = right / density)

fun CameraChanged.toMapCameraPosition() =
    cameraState
        .toMapCameraPosition()
        .copy(paddings = cameraState.padding.toPaddings())

fun MapCameraPosition.toCameraOptions(): CameraOptions = toCameraOptions(density = null)

/**
 * [density] があれば [MapCameraPosition.paddings] を Mapbox のパディングとして渡す。
 * Mapbox はパディングを除いた領域の中央にカメラの中心を置き、tilt の遠近もエンジンが解く。
 * パディングは次に変えるまで残るので、null（未指定）のときは渡さない。
 */
internal fun MapCameraPosition.toCameraOptions(density: Float?): CameraOptions {
    val padding = if (density != null) paddings?.toMapboxEdgeInsets(density) else null
    if (this.tilt >= 0) {
        return CameraOptions
            .Builder()
            .center(GeoPoint.from(position).toPoint())
            .zoom(ZoomAltitudeConverter.googleZoomToMapboxZoom(zoom))
            .pitch(tilt)
            .bearing(CameraBearing.toNativeHeading(bearing))
            .padding(padding)
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
        val target = Spherical.computeOffset(position, distanceForward, CameraBearing.toNativeHeading(bearing))
        val mapboxZoom = converter.altitudeToZoomLevel(altitude / cos(tiltAbsRad), target.latitude, 0.0)

        return CameraOptions
            .Builder()
            .center(target.toPoint())
            .zoom(mapboxZoom)
            .pitch(tiltAbsDeg)
            .bearing(CameraBearing.toNativeHeading(bearing))
            .padding(padding)
            .build()
    }
}

fun MapCameraPosition.toCameraState(): CameraState =
    CameraState(
        GeoPoint.from(position).toPoint(),
        EdgeInsets(0.0, 0.0, 0.0, 0.0),
        ZoomAltitudeConverter.googleZoomToMapboxZoom(zoom),
        CameraBearing.toNativeHeading(bearing),
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
                fov = cameraPosition.fov,
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

internal fun CameraState.toMapCameraPosition(
    logicalTiltHint: Double?,
    density: Float? = null,
) = toMapCameraPosition(
    center = center.toGeoPoint(),
    mapboxZoom = zoom,
    bearing = bearing,
    pitch = pitch,
    paddings = density?.let { padding.toMapPaddings(it) },
    logicalTiltHint = logicalTiltHint,
)

internal data class MapboxCameraStateSnapshot(
    val cameraState: CameraState,
    val logicalTiltHint: Double?,
    val density: Float? = null,
) {
    fun toMapCameraPosition(): MapCameraPosition = cameraState.toMapCameraPosition(logicalTiltHint, density)
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
            bearing = CameraBearing.bearingFromNativeHeading(bearing),
            tilt = pitch,
            paddings = paddings,
            visibleRegion = visibleRegion,
            fov = MAPBOX_VERTICAL_FOV,
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
        bearing = CameraBearing.bearingFromNativeHeading(bearing),
        tilt = -pitchAbsDeg,
        paddings = paddings,
        visibleRegion = visibleRegion,
        fov = MAPBOX_VERTICAL_FOV,
    )
}
