# MapboxPolygonController

## Signature

```kotlin
class MapboxPolygonController(
    override val renderer: MapboxPolygonCompositeRenderer,
) : PolygonController<MapboxActualPolygon>(renderer.fill.polygonManager, renderer)
```

## Description

The polygon controller of the Mapbox provider. It extends the core `PolygonController`, so diffing
(add / change / remove), `has`, `find`, the click cascade (`resolveTap`, `dispatchClick`),
`clear` and `destroy` are the core implementation shared with every other provider.

The only provider-specific part is the renderer: a fill layer can only draw a 1 px outline, so the
outline is drawn as a separate polyline by [`MapboxPolygonCompositeRenderer`](./MapboxPolygonCompositeRenderer.kt.md).

## Constructor Parameters

- `renderer`
    - Type: `MapboxPolygonCompositeRenderer`
    - Description: Draws both the fill and the outline. Its `fill.polygonManager` is used as the
      controller's polygon manager, so the controller and the fill renderer always see the same
      entities.

## Properties

- `zIndex`: `2` (unchanged from the former `MapboxPolygonConductor`).
