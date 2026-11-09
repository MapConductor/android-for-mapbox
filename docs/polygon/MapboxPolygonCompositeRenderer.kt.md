# MapboxPolygonCompositeRenderer

## Signature

```kotlin
class MapboxPolygonCompositeRenderer(
    val fill: MapboxPolygonOverlayRenderer,
    val outline: MapboxPolylineOverlayRenderer,
) : PolygonOverlayRendererInterface<MapboxActualPolygon>
```

## Description

Presents the fill renderer and the outline renderer to the core `PolygonController` as a single
renderer.

A Mapbox fill layer can only draw a 1 px outline with no width, so to honour `strokeWidth` each
polygon's outer ring is also drawn as a closed polyline with the id `outline-<polygon id>` through
`outline`. Every call the controller makes on this renderer is forwarded to `fill` and mirrored on
`outline`:

- `onAdd` / `onChange`: the fill is created or rebuilt; when that succeeds, the outline polyline is
  (re)created with the polygon's `strokeColor`, `strokeWidth`, `geodesic`, `zIndex` and `extra`.
- `onRemove`: the outline polylines are dropped and both sources are redrawn without the removed
  polygons (the controller's `clear()` does not call `onPostProcess`).
- `onPostProcess`: both sources are redrawn.

Holes are not outlined.

## Constructor Parameters

- `fill`
    - Type: `MapboxPolygonOverlayRenderer`
    - Description: Draws the polygon fill (with holes) into the fill source.
- `outline`
    - Type: `MapboxPolylineOverlayRenderer`
    - Description: Draws the outlines into the polygon outline source.
