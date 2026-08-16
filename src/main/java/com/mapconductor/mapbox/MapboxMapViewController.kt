package com.mapconductor.mapbox

import MapboxMapViewControllerInterface
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.style.layers.properties.generated.ProjectionName
import com.mapbox.maps.extension.style.projection.generated.Projection
import com.mapbox.maps.extension.style.projection.generated.setProjection
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.gestures.removeOnMapClickListener
import com.mapbox.maps.plugin.gestures.removeOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.removeOnMoveListener
import com.mapconductor.core.circle.OnCircleEventHandler
import com.mapconductor.core.controller.BaseMapViewController
import com.mapconductor.core.features.GeoPointInterface
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.groundimage.OnGroundImageEventHandler
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapProjection
import com.mapconductor.core.map.MapUISettings
import com.mapconductor.core.marker.DefaultMarkerEventController
import com.mapconductor.core.marker.MarkerAnimationOverlayHost
import com.mapconductor.core.marker.MarkerEventControllerInterface
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.core.marker.MarkerRenderingStrategyInterface
import com.mapconductor.core.marker.OnMarkerEventHandler
import com.mapconductor.core.marker.StrategyMarkerController
import com.mapconductor.core.marker.dispatchGeoMarkerClick
import com.mapconductor.core.polygon.OnPolygonEventHandler
import com.mapconductor.core.polygon.PolygonState
import com.mapconductor.core.polyline.OnPolylineEventHandler
import com.mapconductor.core.polyline.PolylineState
import com.mapconductor.mapbox.circle.MapboxCircleController
import com.mapconductor.mapbox.groundimage.MapboxGroundImageController
import com.mapconductor.mapbox.marker.MapboxMarkerController
import com.mapconductor.mapbox.marker.MapboxMarkerOverlayRenderer
import com.mapconductor.mapbox.marker.MarkerDragLayer
import com.mapconductor.mapbox.marker.MarkerLayer
import com.mapconductor.mapbox.polygon.MapboxPolygonConductor
import com.mapconductor.mapbox.polyline.MapboxPolylineController
import com.mapconductor.mapbox.raster.MapboxRasterLayerController
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

typealias MapboxMapDesignTypeChangeHandler = (MapboxDesignType) -> Unit

/**
 * Mapbox のマップコントローラ。
 *
 * **型は公開、構築は非公開。** `createMapboxViewController()` の戻り型として、また
 * React Native のような非 Compose ホストが `BaseMapViewController` として受けるために
 * 型は見える必要がある（LongdoMapViewController / MapTilerMapViewController も公開）。
 * 一方コンストラクタ引数は internal なコントローラ群なので、`internal constructor` に
 * して**組み立てはファクトリ 1 か所に閉じる**。2 経路で組むと配線が片方だけ増える。
 */
class MapboxMapViewController internal constructor(
    override val holder: MapboxMapViewHolder,
    internal var projection: MapProjection,
    internal val markerController: MapboxMarkerController,
    internal val polylineController: MapboxPolylineController,
    internal val polygonController: MapboxPolygonConductor,
    internal val groundImageController: MapboxGroundImageController,
    internal val circleController: MapboxCircleController,
    internal val rasterLayerController: MapboxRasterLayerController,
    override val mainCoroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
    override val defaultCoroutine: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : BaseMapViewController(),
    MapboxMapViewControllerInterface,
    OnMapClickListener,
    OnMapLongClickListener,
    OnMoveListener {
    // Track created z-indexed polygon layers to manage add/remove without enumerating style layers
    internal val polygonZLayers: MutableSet<Int> = mutableSetOf()
    internal val markerEventControllers = mutableListOf<DefaultMarkerEventController<MapboxActualMarker>>()
    internal var activeDragController: DefaultMarkerEventController<MapboxActualMarker>? = null

    internal val cameraUpdateToken = AtomicInteger(0)
    internal var lastLogicalCameraPosition: MapCameraPosition? = null

    internal var markerClickListener: OnMarkerEventHandler? = null
    internal var markerDragStartListener: OnMarkerEventHandler? = null
    internal var markerDragListener: OnMarkerEventHandler? = null
    internal var markerDragEndListener: OnMarkerEventHandler? = null
    internal var markerAnimateStartListener: OnMarkerEventHandler? = null
    internal var markerAnimateEndListener: OnMarkerEventHandler? = null
    internal var involvedMapInitializedCallback: Boolean = false

    init {
        setupListeners()
        registerOverlayController(markerController)
        registerOverlayController(polylineController)
        registerOverlayController(polygonController)
        registerOverlayController(groundImageController)
        registerOverlayController(circleController)
        registerOverlayController(rasterLayerController)
        registerMarkerEventController(DefaultMarkerEventController(markerController))

        markerController.setRasterLayerCallback { state ->
            if (state != null) {
                rasterLayerController.upsert(state)
            } else {
                val markerTileLayers =
                    rasterLayerController.rasterLayerManager
                        .allEntities()
                        .filter { it.state.id.startsWith("marker-tile-") }
                markerTileLayers.forEach { entity -> rasterLayerController.removeById(entity.state.id) }
            }
        }
    }

    override fun moveCamera(position: MapCameraPosition) = handleMoveCamera(position)

    override fun animateCamera(
        position: MapCameraPosition,
        duration: Long,
    ) = handleAnimateCamera(position, duration)

    override fun setCameraRestriction(restriction: CameraRestriction?) = handleCameraRestriction(restriction)

    override fun fitBounds(
        bounds: GeoRectBounds,
        padding: Int,
    ) = handleFitBounds(bounds, padding)

    override fun onMapLongClick(point: Point): Boolean = handleMapLongClick(point)

    override fun onMapClick(point: Point): Boolean = handleMapClick(point)

    override fun onMove(detector: MoveGestureDetector): Boolean = handleMove(detector)

    override fun onMoveBegin(detector: MoveGestureDetector) = handleMoveBegin(detector)

    override fun onMoveEnd(detector: MoveGestureDetector) = handleMoveEnd(detector)

    /**
     * マーカーのヒットテスト。クリックカスケードの先頭。
     *
     * Mapbox は地図クリックの座標からそのまま引けるので、コアの
     * [dispatchGeoMarkerClick] に委ねる（`clickable = false` の透過もそちら）。
     */
    override fun dispatchMarkerTap(position: GeoPointInterface): Boolean =
        markerEventControllers.dispatchGeoMarkerClick(position)

    // 拡張ファイル（Style / Camera / Gestures）からは基底クラスの protected へ
    // 触れないため、ここで internal の入口を用意しておく。

    internal fun emitMapInitialized() {
        mapInitializedCallback?.invoke()
    }

    internal fun emitCameraMoveStart(position: MapCameraPosition) {
        cameraMoveStartCallback?.invoke(position)
    }

    internal fun emitCameraMove(position: MapCameraPosition) {
        cameraMoveCallback?.invoke(position)
    }

    internal fun emitCameraMoveEnd(position: MapCameraPosition) {
        cameraMoveEndCallback?.invoke(position)
    }

    internal suspend fun emitCameraPosition(position: MapCameraPosition) {
        notifyMapCameraPosition(position)
    }

    fun setupListeners() {
        holder.map.subscribeCameraChanged {
            val token = cameraUpdateToken.incrementAndGet()
            defaultCoroutine.launch {
                if (token != cameraUpdateToken.get()) return@launch
                // Calculate camera position on background thread
                val mapCameraPosition = getMapCameraPositionAsync() ?: return@launch
                notifyMapCameraPosition(mapCameraPosition)
                cameraMoveCallback?.invoke(mapCameraPosition)
            }
        }
        holder.map.subscribeStyleLoaded {
            holder.map.style?.let { style ->
                applyProjection()
                // When style reloads, our runtime sources/layers/images are dropped.
                // Reattach overlays and ensure marker images exist, then redraw.
                attachOverlaySourcesAndLayers(style)
                markerEventControllers.forEach { controller ->
                    val renderer = controller.renderer as MapboxMarkerOverlayRenderer
                    renderer.ensureStyleImages(style)
                    renderer.redraw()
                }
                mainCoroutine.launch {
                    groundImageController.reapplyStyle()
                    rasterLayerController.reapplyStyle()
                }

                // After style is ready, trigger an initial camera update
                sendInitialCameraUpdate()

                style.toMapDesignType().let { mapDesign ->
                    this@MapboxMapViewController.mapDesignType = mapDesign
                    mapDesignTypeChangeListener?.invoke(mapDesign)
                }
            }
        }

        holder.map.subscribeStyleImageMissing { evt ->
            val missingId = evt.imageId
            markerEventControllers.forEach { controller ->
                (controller.renderer as MapboxMarkerOverlayRenderer).onStyleImageMissing(missingId)
            }
        }
        holder.map.subscribeMapIdle {
            mainCoroutine.launch {
                getMapCameraPosition()?.let { mapCameraPosition ->
                    defaultCoroutine.launch {
                        notifyMapCameraPosition(mapCameraPosition)
                        cameraMoveEndCallback?.invoke(mapCameraPosition)
                    }
                }
            }
        }

        holder.map.removeOnMapClickListener(this)
        holder.map.addOnMapClickListener(this)

        holder.map.removeOnMapLongClickListener(this)
        holder.map.addOnMapLongClickListener(this)

        holder.map.removeOnMoveListener(this)
        holder.map.addOnMoveListener(this)
    }

    fun setProjection(value: MapProjection) {
        if (projection == value) return
        projection = value
        mainCoroutine.launch {
            applyProjection()
        }
    }

    private fun applyProjection() {
        if (holder.map.style == null) return
        val name =
            when (projection) {
                MapProjection.Mercator -> ProjectionName.MERCATOR
                MapProjection.Globe -> ProjectionName.GLOBE
            }
        holder.map.setProjection(Projection(name))
    }

    override suspend fun clearOverlays() {
        markerController.clear()
        polylineController.clear()
        polygonController.clear()
        groundImageController.clear()
        rasterLayerController.clear()
    }

    override fun setMarkerAnimationOverlayHost(host: MarkerAnimationOverlayHost?) {
        (markerController.renderer as MapboxMarkerOverlayRenderer).animationOverlayHost = host
    }

    override suspend fun compositionPolygons(data: List<PolygonState>) {
        polygonController.add(data)
        holder.map.getStyle { ensurePolygonZLayers(it) }
    }

    override suspend fun updatePolygon(state: PolygonState) {
        polygonController.update(state)
        holder.map.getStyle { ensurePolygonZLayers(it) }
    }

    @Deprecated("Use CircleState.onClick instead.")
    override fun setOnCircleClickListener(listener: OnCircleEventHandler?) {
        this.circleController.clickListener = listener
    }

    @Deprecated("Use GroundImageState.onClick instead.")
    override fun setOnGroundImageClickListener(listener: OnGroundImageEventHandler?) {
        this.groundImageController.clickListener = listener
    }

    override fun hasPolyline(state: PolylineState): Boolean =
        this.polylineController.polylineManager
            .hasEntity(state.id)

    override fun hasPolygon(state: PolygonState): Boolean =
        this.polygonController.polygonOverlay.polygonManager
            .hasEntity(state.id)

    override fun applyUISettings(settings: MapUISettings) {
        holder.mapView.gestures.apply {
            scrollEnabled = settings.scrollGesture
            pinchToZoomEnabled = settings.zoomGesture
            doubleTapToZoomInEnabled = settings.zoomGesture
            doubleTouchToZoomOutEnabled = settings.zoomGesture
            quickZoomEnabled = settings.zoomGesture
            rotateEnabled = settings.rotateGesture
            pitchEnabled = settings.tiltGesture
        }
    }

    @Deprecated("Use MarkerState.onDragStart instead.")
    override fun setOnMarkerDragStart(listener: OnMarkerEventHandler?) {
        markerDragStartListener = listener
        markerEventControllers.forEach { it.setDragStartListener(listener) }
    }

    @Deprecated("Use MarkerState.onDrag instead.")
    override fun setOnMarkerDrag(listener: OnMarkerEventHandler?) {
        markerDragListener = listener
        markerEventControllers.forEach { it.setDragListener(listener) }
    }

    @Deprecated("Use MarkerState.onDragEnd instead.")
    override fun setOnMarkerDragEnd(listener: OnMarkerEventHandler?) {
        markerDragEndListener = listener
        markerEventControllers.forEach { it.setDragEndListener(listener) }
    }

    @Deprecated("Use MarkerState.onAnimateStart instead.")
    override fun setOnMarkerAnimateStart(listener: OnMarkerEventHandler?) {
        markerAnimateStartListener = listener
        markerEventControllers.forEach { it.setAnimateStartListener(listener) }
    }

    @Deprecated("Use MarkerState.onAnimateEnd instead.")
    override fun setOnMarkerAnimateEnd(listener: OnMarkerEventHandler?) {
        markerAnimateEndListener = listener
        markerEventControllers.forEach { it.setAnimateEndListener(listener) }
    }

    @Deprecated("Use MarkerState.onClick instead.")
    override fun setOnMarkerClickListener(listener: OnMarkerEventHandler?) {
        markerClickListener = listener
        markerEventControllers.forEach { it.setClickListener(listener) }
    }

    @Deprecated("Use PolylineState.onClick instead.")
    override fun setOnPolylineClickListener(listener: OnPolylineEventHandler?) {
        polylineController.clickListener = listener
    }

    @Deprecated("Use PolygonState.onClick instead.")
    override fun setOnPolygonClickListener(listener: OnPolygonEventHandler?) {
        polygonController.clickListener = listener
    }

    internal var mapDesignType: MapboxDesignType = MapboxMapDesign.Standard

    internal var mapDesignTypeChangeListener: MapboxMapDesignTypeChangeHandler? = null

    override fun setMapDesignType(value: MapboxDesignType) {
        mainCoroutine.launch {
            holder.mapView.mapboxMap.loadStyle(value.getValue())
        }
    }

    override fun setMapDesignTypeChangeListener(listener: MapboxMapDesignTypeChangeHandler) {
        mapDesignTypeChangeListener = listener
    }

    internal fun registerMarkerEventController(controller: DefaultMarkerEventController<MapboxActualMarker>) {
        if (markerEventControllers.contains(controller)) return
        markerEventControllers.add(controller)
        controller.setClickListener(markerClickListener)
        controller.setDragStartListener(markerDragStartListener)
        controller.setDragListener(markerDragListener)
        controller.setDragEndListener(markerDragEndListener)
        controller.setAnimateStartListener(markerAnimateStartListener)
        controller.setAnimateEndListener(markerAnimateEndListener)

        holder.map.style?.let { style ->
            val renderer = controller.renderer as MapboxMarkerOverlayRenderer
            attachMarkerLayers(style, renderer)
            renderer.ensureStyleImages(style)
            renderer.redraw()
        }
    }

    fun createMarkerRenderer(
        strategy: MarkerRenderingStrategyInterface<MapboxActualMarker>,
    ): MarkerOverlayRendererInterface<MapboxActualMarker> {
        val groupId = UUID.randomUUID().toString()
        val markerLayer =
            MarkerLayer(
                sourceId = "markers-source-$groupId",
                layerId = "markers-layer-$groupId",
            )
        val dragLayer =
            MarkerDragLayer(
                sourceId = "marker-drag-source-$groupId",
                layerId = "marker-drag-layer-$groupId",
            )
        return MapboxMarkerOverlayRenderer(
            holder = holder,
            markerManager = strategy.markerManager,
            markerLayer = markerLayer,
            dragLayer = dragLayer,
        )
    }

    fun createMarkerEventController(
        controller: StrategyMarkerController<MapboxActualMarker>,
        renderer: MarkerOverlayRendererInterface<MapboxActualMarker>,
    ): MarkerEventControllerInterface<MapboxActualMarker> = DefaultMarkerEventController(controller)

    fun registerMarkerEventController(controller: MarkerEventControllerInterface<MapboxActualMarker>) {
        @Suppress("UNCHECKED_CAST")
        val typed = controller as? DefaultMarkerEventController<MapboxActualMarker> ?: return
        registerMarkerEventController(typed)
    }
}
