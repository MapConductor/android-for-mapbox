# MapboxMapViewScope

## Signature
```kotlin
class MapboxMapViewScope : MapViewScope()
```

## Description

`MapboxMapViewScope` is a specialized scope class for the Mapbox Maps implementation within the
MapConductor framework. It extends the base `MapViewScope`, inheriting all common map
functionalities.

This scope is the context for the `content` lambda of `MapboxMapView`, where overlay composables
such as `Marker`, `Polyline`, and `Polygon` are called.

## Example

`MapboxMapViewScope` is provided as the receiver within the `content` lambda of `MapboxMapView`.

```kotlin
MapboxMapView(
    state = mapState,
    modifier = Modifier.fillMaxSize(),
) {
    // 'this' is MapboxMapViewScope
    Marker(state = rememberMarkerState(id = "marker-1", position = GeoPoint(35.681236, 139.767125)))
}
```
