# MapboxCircleController

### Signature

```kotlin
class MapboxCircleController(
    override val renderer: MapboxCircleOverlayRenderer,
    circleManager: CircleManagerInterface<MapboxActualCircle> = renderer.circleManager,
) : CircleController<MapboxActualCircle>(circleManager, renderer)
```

### Description

The `MapboxCircleController` is a specialized controller for managing and rendering circle overlays
on a Mapbox map. It serves as a concrete implementation of the generic `CircleController`, bridging
the core circle management logic with the Mapbox-specific rendering engine.

This controller coordinates the state of circle objects (`MapboxActualCircle`) with their visual
representation on the map, which is handled by the `MapboxCircleOverlayRenderer`.

### Parameters

This documentation describes the parameters for the `MapboxCircleController` constructor.

- `renderer`
    - Type: `MapboxCircleOverlayRenderer`
    - Description: The Mapbox-specific renderer responsible for drawing the circles on the map.
- `circleManager`
    - Type: `CircleManagerInterface<MapboxActualCircle>`
    - Description: **Optional**. The manager responsible for the underlying data and state of the
                   circles. If not provided, it defaults to the `circleManager` instance from the
                   supplied `renderer`.

### Example

The following example demonstrates how to add a circle to a Mapbox map. `MapboxCircleController`
is created internally by `MapboxMapView`; circles are added via `CircleState` in the Compose
content lambda.

```kotlin
MapboxMapView(state = mapState) {
    Circle(
        state = rememberCircleState(
            id = "la-circle",
            center = GeoPoint(34.0522, -118.2437),
            radius = 1000.0,
            fillColor = Color.Blue.copy(alpha = 0.3f),
            strokeColor = Color.Blue
        )
    )
}
```
