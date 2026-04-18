# Class `MapboxMarkerOverlayRenderer`

## Description

A concrete implementation of `AbstractMarkerOverlayRenderer` for the Mapbox Maps SDK. This class is
responsible for rendering and managing marker objects on a Mapbox map.

It translates abstract `MarkerEntity` objects into a `FeatureCollection` of GeoJSON points, which
are then displayed using a `GeoJsonSource` and a `SymbolLayer`. The renderer also handles the
complex lifecycle of marker icons, including adding them as images to the map's style, caching them,
reference counting their usage, and cleaning them up after a grace period to prevent rendering
artifacts.

## Signature

```kotlin
class MapboxMarkerOverlayRenderer(
    holder: MapboxMapViewHolder,
    val markerManager: MarkerManager<MapboxActualMarker>,
    val markerLayer: MarkerLayer,
    val dragLayer: MarkerDragLayer,
    coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractMarkerOverlayRenderer<
        MapboxMapViewHolder,
        MapboxActualMarker,
    >
```

## Constructor

Initializes a new instance of the `MapboxMarkerOverlayRenderer`.

### Parameters

- `holder`
    - Type: `MapboxMapViewHolder`
    - Description: The view holder that provides access to the `MapboxMap` instance.
- `markerManager`
    - Type: `MarkerManager<MapboxActualMarker>`
    - Description: The manager that holds the state of all markers to be rendered.
- `markerLayer`
    - Type: `MarkerLayer`
    - Description: The layer responsible for drawing the markers on the map.
- `dragLayer`
    - Type: `MarkerDragLayer`
    - Description: The layer used to render a marker while it is being dragged.
- `coroutine`
    - Type: `CoroutineScope`
    - Description: The `CoroutineScope` for launching asynchronous operations, defaulting to
                   `CoroutineScope(Dispatchers.Main)`.

## Public Functions

### `onStyleImageMissing`

A handler that should be invoked when the Mapbox map reports a missing style image. This can happen
during style changes or race conditions. The method attempts to re-add the required image to the
style from an in-memory cache or by regenerating it from the original marker data.

#### Signature

```kotlin
fun onStyleImageMissing(imageId: String)
```

#### Parameters

- `imageId`
    - Type: `String`
    - Description: The ID of the missing image that Mapbox failed to find in its style.

### `ensureStyleImages`

Re-populates the given Mapbox style with all necessary marker icon images. This method is crucial
for restoring markers after the map's style has been reloaded (e.g., switching from a light to a
dark theme). It adds the default marker icon and iterates through all existing markers to re-add
their custom icons.

#### Signature

```kotlin
fun ensureStyleImages(style: com.mapbox.maps.Style)
```

#### Parameters

- `style`
    - Type: `com.mapbox.maps.Style`
    - Description: The new or reloaded `Style` instance to which the images should be added.

### `redraw`

Triggers a full redraw of all markers currently managed by the `markerManager`. This is useful for
forcing a visual update when the underlying data has changed in a way not automatically tracked by
the renderer.

#### Signature

```kotlin
fun redraw()
```

### `drawDragLayer`

Renders the dedicated layer for displaying a marker as it is being dragged by the user. This
typically involves drawing a single marker at its current drag position.

#### Signature

```kotlin
fun drawDragLayer()
```

### `setMarkerPosition`

Updates the geographical position of a specific marker on the map. It modifies the geometry of the
corresponding GeoJSON `Feature` and updates the map's data source.

#### Signature

```kotlin
override fun setMarkerPosition(
    markerEntity: MarkerEntityInterface<Feature>,
    position: GeoPoint,
)
```

#### Parameters

- `markerEntity`
    - Type: `MarkerEntityInterface<Feature>`
    - Description: The marker entity whose position is being updated.
- `position`
    - Type: `GeoPoint`
    - Description: The new `GeoPoint` coordinates for the marker.

## Example

The following conceptual example demonstrates how to instantiate and use
`MapboxMarkerOverlayRenderer` within an application.

```kotlin
// Assuming you have these components initialized from your application's context
val mapboxMap: MapboxMap = /* ... */
val mapViewHolder = MapboxMapViewHolder(mapboxMap)
val markerManager = MarkerManager<MapboxActualMarker>()
val coroutineScope = CoroutineScope(Dispatchers.Main)

// Initialize the marker and drag layers (custom classes within the framework)
val markerLayer = MarkerLayer(mapViewHolder)
val dragLayer = MarkerDragLayer(mapViewHolder)

// Create the renderer instance
val markerRenderer = MapboxMarkerOverlayRenderer(
    holder = mapViewHolder,
    markerManager = markerManager,
    markerLayer = markerLayer,
    dragLayer = dragLayer,
    coroutine = coroutineScope
)

// The renderer is now set up to listen for changes in markerManager
// and render them on the map via markerLayer.

// It's important to handle map style load events to ensure markers are visible.
mapboxMap.getStyle { style ->
    // When the style is loaded or changed, ensure the marker images are present.
    markerRenderer.ensureStyleImages(style)
}

// To handle cases where Mapbox loses an image, you would set up a listener.
// Note: The specific listener may vary based on the Mapbox SDK version.
// mapboxMap.addOnStyleImageMissingListener { event ->
//     markerRenderer.onStyleImageMissing(event.id)
// }
```
