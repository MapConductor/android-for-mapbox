# MapboxPolylineController

### Signature

```kotlin
class MapboxPolylineController(
    override val renderer: MapboxPolylineOverlayRenderer,
    polylineManager: PolylineManagerInterface<MapboxActualPolyline> = renderer.polylineManager,
) : PolylineController<MapboxActualPolyline>(polylineManager, renderer)
```

### Description

The `MapboxPolylineController` is a specialized controller class responsible for managing and
rendering polylines on a Mapbox map. It acts as a bridge between the data-handling logic of a
`PolylineManagerInterface` and the visual rendering provided by `MapboxPolylineOverlayRenderer`.

By inheriting from the generic `PolylineController`, it provides a consistent API for polyline
manipulation while being specifically tailored for the Mapbox environment. This class simplifies the
process of adding, updating, and removing polylines by coordinating the underlying data manager and
the renderer.

### Parameters

- `renderer`
    - Type: `MapboxPolylineOverlayRenderer`
    - Description: The Mapbox-specific renderer instance responsible for drawing the polylines onto
                   the map's style layer.
- `polylineManager`
    - Type: `PolylineManagerInterface<MapboxActualPolyline>`
    - Description: The manager responsible for the underlying data and state of the polylines. If
                   not provided, it defaults to the manager instance associated with the `renderer`,
                   ensuring both components operate on the same data source.

### Example

The following example demonstrates how to add a polyline to a Mapbox map. `MapboxPolylineController`
is created internally by `MapboxMapView`; polylines are added via `PolylineState` in the Compose
content lambda.

```kotlin
MapboxMapView(state = mapState) {
    Polyline(
        state = rememberPolylineState(
            id = "sf-to-la",
            points = listOf(
                GeoPoint(37.7749, -122.4194), // San Francisco
                GeoPoint(34.0522, -118.2437)  // Los Angeles
            ),
            strokeColor = Color.Blue,
            strokeWidth = 4.dp
        )
    )
}
```
