# MapBox SDK for MapConductor Android

## Description

MapConductor provides a unified API for Android Jetpack Compose.
You can use Mapbox view with Jetpack Compose, but you can also switch to other Maps SDKs (such as MapLibre, HERE, and so on), anytimes.

Even you use the wrapper API, but you can still access to the native Mapbox view if you want.

## Setup

https://docs-android.mapconductor.com/setup/mapbox/

## Usage

```kotlin
@Composable
fun MapView(modifier: Modififer = Modififer) {
    val state =
        rememberMapboxMapViewState(
            cameraPosition =
                MapCameraPosition(
                    position = GeoPoint.fromLatLong(0.0, 0.0),
                    zoom = 3.0,
                ),
        )

    MapboxMapView(
        modifier = modifier,
        state = state,
    ) {
        // If this displays correctly, your setup is working
    }
}
```

![](docs/images/basic-setup-mapbox.png)
