package com.mapconductor.mapbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapconductor.compose.map.MapViewBase
import com.mapconductor.core.OnCameraMoveHandler
import com.mapconductor.core.OnMapEventHandler
import com.mapconductor.core.OnMapLoadedHandler
import com.mapconductor.core.circle.CircleManager
import com.mapconductor.core.map.CameraRestriction
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapProjection
import com.mapconductor.core.map.MutableMapServiceRegistry
import com.mapconductor.core.marker.MarkerEventControllerInterface
import com.mapconductor.core.marker.MarkerManager
import com.mapconductor.core.marker.MarkerOverlayRendererInterface
import com.mapconductor.core.marker.MarkerRenderingStrategyInterface
import com.mapconductor.core.marker.MarkerRenderingSupport
import com.mapconductor.core.marker.MarkerRenderingSupportKey
import com.mapconductor.core.marker.MarkerTilingOptions
import com.mapconductor.core.marker.StrategyMarkerController
import com.mapconductor.core.polygon.PolygonManager
import com.mapconductor.core.polyline.PolylineManager
import com.mapconductor.mapbox.circle.MapboxCircleController
import com.mapconductor.mapbox.circle.MapboxCircleLayer
import com.mapconductor.mapbox.circle.MapboxCircleOverlayRenderer
import com.mapconductor.mapbox.groundimage.MapboxGroundImageController
import com.mapconductor.mapbox.groundimage.MapboxGroundImageOverlayRenderer
import com.mapconductor.mapbox.marker.MapboxMarkerController
import com.mapconductor.mapbox.marker.MarkerDragLayer
import com.mapconductor.mapbox.marker.MarkerLayer
import com.mapconductor.mapbox.polygon.MapboxPolygonConductor
import com.mapconductor.mapbox.polygon.MapboxPolygonLayer
import com.mapconductor.mapbox.polygon.MapboxPolygonOverlayRenderer
import com.mapconductor.mapbox.polyline.MapboxPolylineController
import com.mapconductor.mapbox.polyline.MapboxPolylineLayer
import com.mapconductor.mapbox.polyline.MapboxPolylineOverlayRenderer
import com.mapconductor.mapbox.raster.MapboxRasterLayerController
import com.mapconductor.mapbox.raster.MapboxRasterLayerOverlayRenderer
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup

@SuppressLint("Lifecycle")
@Composable
fun MapboxMapView(
    state: MapboxViewState,
    modifier: Modifier = Modifier,
    markerTiling: MarkerTilingOptions? = null,
    cameraRestriction: CameraRestriction? = null,
    sdkInitialize: (suspend (Context) -> Boolean)? = null,
    onMapLoaded: OnMapLoadedHandler? = null,
    onMapClick: OnMapEventHandler? = null,
    onMapLongClick: OnMapEventHandler? = null,
    onCameraMoveStart: OnCameraMoveHandler? = null,
    onCameraMove: OnCameraMoveHandler? = null,
    onCameraMoveEnd: OnCameraMoveHandler? = null,
    projection: MapProjection = MapProjection.Mercator,
    content: (@Composable MapboxMapViewScope.() -> Unit)? = null,
) {
    val holderRef = remember { Ref<MapboxMapViewHolder>() }
    val context = LocalContext.current
    val controllerRef = remember { Ref<MapboxMapViewController>() }
    val scope = remember { MapboxMapViewScope() }
    val registry = remember { scope.buildRegistry() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cameraState = remember { mutableStateOf<MapCameraPositionInterface?>(state.cameraPosition) }

    SideEffect {
        controllerRef.value?.setProjection(projection)
    }

    MapViewBase(
        state = state,
        cameraState = cameraState,
        modifier = modifier,
        viewProvider = {
            val cameraOptions =
                state.cameraPosition.toCameraOptions()

            val styleUri = state.mapDesignType.getValue()

            val mapOptions =
                MapInitOptions(
                    context = context,
                    textureView = true,
                    styleUri = styleUri,
                    cameraOptions = cameraOptions,
                )

            MapView(context, mapOptions).also {
                it.onStart()
            }
        },
        holderProvider = { mapView -> MapboxMapViewHolder(mapView, mapView.mapboxMap) },
        controllerProvider = { holder ->
            // **組み立ては `createMapboxViewController` に一本化してある。**
            // React Native のような非 Compose ホストも同じ関数を通るので、
            // ここでコントローラを直接組み直さないこと（片方だけ配線が増えて食い違う）。
            createMapboxViewController(
                holder = holder,
                markerTiling = markerTiling ?: MarkerTilingOptions.Default,
                projection = projection,
                serviceRegistry = state.serviceRegistry,
            ).also { mapController ->
                mapController.setCameraMoveStartListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMoveStart?.invoke(it)
                }
                mapController.setCameraMoveListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMove?.invoke(it)
                }
                mapController.setCameraMoveEndListener {
                    cameraState.value = it
                    state.updateCameraPosition(it)
                    onCameraMoveEnd?.invoke(it)
                }
                mapController.setMapClickListener(onMapClick)
                mapController.setMapLongClickListener(onMapLongClick)
                mapController.setMapDesignTypeChangeListener(state::onMapDesignTypeChange)
                cameraRestriction?.let { mapController.setCameraRestriction(it) }
                state.setController(mapController)

                holderRef.value = holder
                controllerRef.value = mapController

                // Post an initial camera update once the MapView is laid out and style is ready
                holder.mapView.post { mapController.sendInitialCameraUpdate() }
            }
        },
        scope = scope,
        registry = registry,
        sdkInitialize = {
            if (sdkInitialize != null) {
                sdkInitialize(context)
            } else {
                MapboxInitSDK(context)
                true
            }
        },
        onMapLoaded = onMapLoaded,
        // Pass content if it needs to be rendered within the overlay providers in MapViewBase,
        // or handle it here if it's specific to MapboxMapView structure before calling MapViewBase.
        // For now, assuming content relates to overlay definitions.
        content = content, // This might need adjustment based on how overlays are handled
        customDisposableEffect = { _, holderRef ->

            // HERE specific DisposableEffect logic
            DisposableEffect(lifecycle) {
                val observer =
                    object : DefaultLifecycleObserver {
                        override fun onResume(owner: LifecycleOwner) {
                            holderRef.value?.mapView?.onResume()
                        }

                        override fun onPause(owner: LifecycleOwner) {
                            // Do not call here to keep the MapView instance
                            // holderRef.value?.mapView?.onPause()
                        }

                        @SuppressLint("Lifecycle")
                        override fun onDestroy(owner: LifecycleOwner) {
                            val currentHolder = holderRef.value
                            if (currentHolder != null) {
                                val activity = context.findActivity()
                                if (activity?.isChangingConfigurations == true) {
                                    (currentHolder.mapView.parent as? ViewGroup)?.removeView(currentHolder.mapView)
                                } else {
                                    // Ensure these calls are safe if mapView might be null or already destroyed
                                    currentHolder.mapView.onDestroy()
                                }
                            }
                        }
                    }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }
        },
    )
}

/**
 * 命令的なコントローラ一式を組み立てる。Compose の [MapboxMapView] と、
 * **Compose を通さないホスト（React Native / Cordova）の両方**から使う。
 *
 * ここに置くことで、UI 統合ごとにプロバイダ固有のレイヤ設定を書き写さずに済む。
 * android-for-maplibre の `createMapLibreViewController()` と同じ役割・同じ形。
 *
 * @param serviceRegistry 登録先のサービスレジストリ。Compose からは `state.serviceRegistry` を渡す
 *   （react-sdk / ios-sdk と同じく持ち主は state）。React Native のような非 Compose ホストは
 *   state を持たないので、自前のレジストリを渡す。
 *   **null にすると `MarkerRenderingSupportKey` が登録されず、クラスタリングが
 *   エラーも出さずに何も描かなくなる。**
 */
fun createMapboxViewController(
    holder: MapboxMapViewHolder,
    markerTiling: MarkerTilingOptions = MarkerTilingOptions.Default,
    projection: MapProjection = MapProjection.Mercator,
    serviceRegistry: MutableMapServiceRegistry? = null,
): MapboxMapViewController {
    val mapController =
        MapboxMapViewController(
            holder = holder,
            projection = projection,
            markerController = getMarkerController(holder = holder, markerTiling = markerTiling),
            polylineController = getPolylineController(holder),
            polygonController = getPolygonController(holder),
            groundImageController = getGroundImageController(holder),
            circleController = getCircleController(holder),
            rasterLayerController = getRasterLayerController(holder),
        )

    serviceRegistry?.put(
        MarkerRenderingSupportKey,
        object : MarkerRenderingSupport<MapboxActualMarker> {
            override fun createMarkerRenderer(
                strategy: MarkerRenderingStrategyInterface<MapboxActualMarker>,
            ): MarkerOverlayRendererInterface<MapboxActualMarker> = mapController.createMarkerRenderer(strategy)

            override fun createMarkerEventController(
                controller: StrategyMarkerController<MapboxActualMarker>,
                renderer: MarkerOverlayRendererInterface<MapboxActualMarker>,
            ): MarkerEventControllerInterface<MapboxActualMarker> =
                mapController.createMarkerEventController(controller = controller, renderer = renderer)

            override fun registerMarkerEventController(
                controller: MarkerEventControllerInterface<MapboxActualMarker>,
            ) {
                mapController.registerMarkerEventController(controller)
            }

            override fun onMarkerRenderingReady() {
                mapController.sendInitialCameraUpdate()
            }
        },
    )

    return mapController
}

internal fun getPolygonController(holder: MapboxMapViewHolder): MapboxPolygonConductor {
    val polylineLayer =
        MapboxPolylineLayer(
            sourceId = "polygon-outline-source",
            layerId = "polygon-outline-layer",
        )
    val polylineManager = PolylineManager<MapboxActualPolyline>()
    val polylineOverlayRenderer =
        MapboxPolylineOverlayRenderer(
            layer = polylineLayer,
            polylineManager = polylineManager,
            holder = holder,
        )

    val polygonManager = PolygonManager<MapboxActualPolygon>()
    val polygonLayer =
        MapboxPolygonLayer(
            sourceId = "polygon-fill-source",
            layerId = "polygon-fill-layer",
        )
    val polygonOverlayRenderer =
        MapboxPolygonOverlayRenderer(
            layer = polygonLayer,
            polygonManager = polygonManager,
            holder = holder,
        )

    val conductor =
        MapboxPolygonConductor(
            polygonOverlay = polygonOverlayRenderer,
            polylineOverlay = polylineOverlayRenderer,
        )
    return conductor
}

internal fun getCircleController(holder: MapboxMapViewHolder): MapboxCircleController {
    val circleLayer =
        MapboxCircleLayer(
            sourceId = "circle-source",
            layerId = "circle-layer",
        )
    val circleManager = CircleManager<MapboxActualCircle>()

    val renderer =
        MapboxCircleOverlayRenderer(
            layer = circleLayer,
            circleManager = circleManager,
            holder = holder,
        )

    val controller =
        MapboxCircleController(
            renderer = renderer,
        )
    return controller
}

internal fun getPolylineController(holder: MapboxMapViewHolder): MapboxPolylineController {
    val polylineLayer =
        MapboxPolylineLayer(
            sourceId = "polyline-source",
            layerId = "polyline-layer",
        )
    val polylineManager = PolylineManager<MapboxActualPolyline>()

    val renderer =
        MapboxPolylineOverlayRenderer(
            layer = polylineLayer,
            polylineManager = polylineManager,
            holder = holder,
        )

    val controller =
        MapboxPolylineController(
            renderer = renderer,
        )
    return controller
}

internal fun getMarkerController(
    holder: MapboxMapViewHolder,
    markerTiling: MarkerTilingOptions,
): MapboxMarkerController {
    val manager =
        MarkerManager.defaultManager<MapboxActualMarker>(
            minMarkerCount = markerTiling.minMarkerCount,
        )
    val markerLayer =
        MarkerLayer(
            sourceId = "markers-source",
            layerId = "markers-layer",
        )
    val dragLayer =
        MarkerDragLayer(
            sourceId = "marker-drag-source",
            layerId = "marker-drag-layer",
        )
    return MapboxMarkerController.create(
        holder = holder,
        markerManager = manager,
        markerLayer = markerLayer,
        dragLayer = dragLayer,
        markerTiling = markerTiling,
    )
}

internal fun getRasterLayerController(holder: MapboxMapViewHolder): MapboxRasterLayerController {
    val renderer =
        MapboxRasterLayerOverlayRenderer(
            holder = holder,
        )
    return MapboxRasterLayerController(
        renderer = renderer,
    )
}

internal fun getGroundImageController(holder: MapboxMapViewHolder): MapboxGroundImageController {
    val renderer =
        MapboxGroundImageOverlayRenderer(
            holder = holder,
        )
    return MapboxGroundImageController(renderer = renderer)
}

internal fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
