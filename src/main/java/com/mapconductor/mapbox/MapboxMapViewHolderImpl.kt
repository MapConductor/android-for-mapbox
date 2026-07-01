package com.mapconductor.mapbox

import android.graphics.PointF
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxLifecycleObserver
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.plugin.lifecycle.lifecycle
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.map.MapViewHolderInterface

typealias MapboxMapViewHolderInterface = MapViewHolderInterface<MapView, MapboxMap>

class MapboxMapViewHolder(
    override val mapView: MapView,
    override val map: MapboxMap,
) : MapViewHolderInterface<MapView, MapboxMap>,
    MapboxLifecycleObserver {
    init {
        this.mapView.lifecycle.registerLifecycleObserver(this.mapView, this)
    }

    override fun toScreenOffset(position: GeoPointInterface): PointF? {
        val pixel =
            map.pixelForCoordinate(
                coordinate = GeoPoint.from(position).toPoint(),
            )
        return PointF(pixel.x.toFloat(), pixel.y.toFloat())
    }

    override fun fromScreenOffsetSync(offset: PointF): GeoPoint? =
        map.coordinateForPixel(ScreenCoordinate(offset.x.toDouble(), offset.y.toDouble())).toGeoPoint()

    fun fromScreenOffset(coordinate: ScreenCoordinate): GeoPoint? = map.coordinateForPixel(coordinate).toGeoPoint()

    override suspend fun fromScreenOffset(offset: PointF): GeoPoint? =
        fromScreenOffset(
            ScreenCoordinate(
                offset.x.toDouble(),
                offset.y.toDouble(),
            ),
        )

    override fun onDestroy() {
    }

    override fun onLowMemory() {
    }

    override fun onStart() {
    }

    override fun onStop() {
    }
}
