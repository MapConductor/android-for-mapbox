# Class `MapboxPolygonOverlayRenderer`

## Description

Manages the rendering of polygon overlays on a Mapbox map. This class extends
`AbstractPolygonOverlayRenderer` and provides a Mapbox-specific implementation for creating,
updating, and removing polygons.

A key feature of this renderer is its handling of polygons with holes. Since native Mapbox vector
polygons do not support holes, this class implements a fallback strategy. For polygons with holes,
it creates a transparent vector polygon and then overlays a dynamically generated raster tile layer
that accurately renders the polygon's shape, including its holes. For simple polygons without holes,
it creates a standard filled Mapbox polygon.

This class is typically instantiated and managed by a higher-level controller and is not intended
for direct use by application developers.

## Signature

```kotlin
class MapboxPolygonOverlayRenderer(
    val layer: MapboxPolygonLayer,
    val polygonManager: PolygonManagerInterface<MapboxActualPolygon>,
    override val holder: MapboxMapViewHolder,
    private val rasterLayerController: MapboxRasterLayerController,
    private val tileServer: LocalTileServer = TileServerRegistry.get(forceNoStoreCache = true),
    override val coroutine: CoroutineScope = CoroutineScope(Dispatchers.Main),
) : AbstractPolygonOverlayRenderer<MapboxActualPolygon>()
```

## Constructor

Creates an instance of `MapboxPolygonOverlayRenderer`.

### Parameters

- `layer`
    - Type: `MapboxPolygonLayer`
    - Description: The polygon layer responsible for drawing the polygons on the map.
- `polygonManager`
    - Type: `PolygonManagerInterface<MapboxActualPolygon>`
    - Description: The manager that holds the state of all polygon entities.
- `holder`
    - Type: `MapboxMapViewHolder`
    - Description: The view holder for the Mapbox map instance.
- `rasterLayerController`
    - Type: `MapboxRasterLayerController`
    - Description: The controller used to manage raster layers, essential for rendering polygons
                   with holes.
- `tileServer`
    - Type: `LocalTileServer`
    - Description: *(Optional)* The local tile server used to generate raster tiles for polygons
                   with holes. Defaults to a new instance from `TileServerRegistry`.
- `coroutine`
    - Type: `CoroutineScope`
    - Description: *(Optional)* The coroutine scope for launching asynchronous operations. Defaults
                   to `CoroutineScope(Dispatchers.Main)`.

---

## Methods

### `createPolygon`

Creates a new polygon on the map based on the provided state.

#### Description

If the `PolygonState` defines holes, this method employs the raster tile overlay strategy: it
creates a transparent vector polygon and overlays a raster tile layer that renders the polygon shape
with its holes. If the polygon has no holes, it creates a standard filled Mapbox polygon directly.

#### Signature

```kotlin
override suspend fun createPolygon(state: PolygonState): MapboxActualPolygon?
```

#### Parameters

- `state`
    - Type: `PolygonState`
    - Description: The state object describing the polygon to create.

#### Returns

`MapboxActualPolygon?` — The created `MapboxActualPolygon` instance, or `null` if creation failed.

---

### `updatePolygonProperties`

Updates the properties of an existing polygon.

#### Description

This method compares the `fingerPrint` of the current and previous polygon states. If the
fingerprints differ (indicating a change in properties like points, holes, or color), it triggers a
full recreation of the polygon by calling `createPolygon`. Otherwise, it returns the existing
polygon instance without modification.

#### Signature

```kotlin
override suspend fun updatePolygonProperties(
    polygon: MapboxActualPolygon,
    current: PolygonEntityInterface<MapboxActualPolygon>,
    prev: PolygonEntityInterface<MapboxActualPolygon>,
): MapboxActualPolygon?
```

#### Parameters

- `polygon`
    - Type: `MapboxActualPolygon`
    - Description: The actual Mapbox polygon object on the map.
- `current`
    - Type: `PolygonEntityInterface<MapboxActualPolygon>`
    - Description: The new state of the polygon entity.
- `prev`
    - Type: `PolygonEntityInterface<MapboxActualPolygon>`
    - Description: The previous state of the polygon entity.

#### Returns

`MapboxActualPolygon?` — The updated or newly created `MapboxActualPolygon` instance, or the
previous polygon if no changes were detected.

---

### `removePolygon`

Removes a single polygon entity from the map.

#### Description

This method handles the removal of a polygon. It also ensures that any associated raster mask layer,
which might have been created for a polygon with holes, is properly cleaned up and removed from the
map.

#### Signature

```kotlin
override suspend fun removePolygon(entity: PolygonEntityInterface<MapboxActualPolygon>)
```

#### Parameters

- `entity`
    - Type: `PolygonEntityInterface<MapboxActualPolygon>`
    - Description: The polygon entity to be removed.

---

### `onPostProcess`

Performs actions after a batch of polygon updates has been processed.

#### Description

This method is called after a series of updates. It retrieves all current polygon entities from the
`PolygonManager` and triggers a single, batched redraw of the entire `MapboxPolygonLayer` to reflect
all changes efficiently.

#### Signature

```kotlin
override suspend fun onPostProcess()
```

---

### `onRemove`

Handles the removal of a batch of polygon entities.

#### Description

This method is intended to be called when multiple polygons are removed at once.

**Note:** The current implementation of this method is empty (the logic is commented out) and does
not perform any action.

#### Signature

```kotlin
override suspend fun onRemove(data: List<PolygonEntityInterface<MapboxActualPolygon>>)
```

#### Parameters

- `data`
    - Type: `List<PolygonEntityInterface<MapboxActualPolygon>>`
    - Description: A list of polygon entities to be removed.

---

## Example

The `MapboxPolygonOverlayRenderer` is an internal component used by a `PolygonController`. Here is a
conceptual example of how it might be instantiated within a map setup.

```kotlin
// Assume these dependencies are already initialized
val mapboxMapViewHolder: MapboxMapViewHolder = ...
val polygonManager: PolygonManagerInterface<MapboxActualPolygon> = ...
val rasterLayerController: MapboxRasterLayerController = ...
val coroutineScope: CoroutineScope = ...

// 1. Create the layer that will visually contain the polygons
val polygonLayer = MapboxPolygonLayer(
    id = "my-polygon-layer",
    source = MapboxPolygonSource("my-polygon-source"),
    holder = mapboxMapViewHolder
)

// 2. Instantiate the renderer
val polygonRenderer = MapboxPolygonOverlayRenderer(
    layer = polygonLayer,
    polygonManager = polygonManager,
    holder = mapboxMapViewHolder,
    rasterLayerController = rasterLayerController,
    coroutine = coroutineScope
)

// 3. The renderer would then be used by a PolygonController to manage polygons
// For example, adding a polygon would internally call polygonRenderer.createPolygon(state)
val polygonController = PolygonController(
    polygonManager = polygonManager,
    renderer = polygonRenderer
)

// Now, using the controller will delegate rendering tasks to our renderer
// polygonController.add(polygonState)
```
