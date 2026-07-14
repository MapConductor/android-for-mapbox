package com.mapconductor.mapbox.marker

import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.extension.style.expressions.dsl.generated.switchCase
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.layers.properties.generated.IconTranslateAnchor
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapconductor.core.marker.MarkerEntityInterface

open class MarkerLayer(
    open val sourceId: String,
    open val layerId: String,
) {
    val layer =
        symbolLayer(layerId, sourceId) {
//            iconSize(Expression.get(MapboxMarkerRenderer.Prop.SCALE))
            iconImage(Expression.get(MapboxMarkerOverlayRenderer.Prop.ICON_ID))
            iconAllowOverlap(true)
            iconIgnorePlacement(true)
            symbolSortKey(Expression.get(MapboxMarkerOverlayRenderer.Prop.Z_INDEX))
            iconAnchor(IconAnchor.TOP_LEFT)
            iconTranslateAnchor(IconTranslateAnchor.MAP)
            iconOffset(
                switchCase {
                    has(MapboxMarkerOverlayRenderer.Prop.ICON_ANCHOR)
                    get(MapboxMarkerOverlayRenderer.Prop.ICON_ANCHOR)
                    literal(listOf(0.0, 0.0)) // center-middle
                },
            )
        }

    val source: GeoJsonSource = geoJsonSource(sourceId)

    // geoJsonSource(sourceId) starts out empty, so the first draw() call has nothing to clear.
    // @Volatile because callers on a different thread (e.g. MapboxMarkerOverlayRenderer.
    // onPostProcess() on its ingest thread) need to read this without hopping onto the
    // thread that writes it, to decide whether that hop is even necessary in the first place.
    @Volatile
    private var lastDrawnEmpty = true

    // Lets a caller on any thread check, before paying for a dispatcher hop onto the thread
    // that owns the style, whether draw() would actually have anything to do for this set of
    // entities. Mirrors the emptiness check draw() itself performs.
    fun wouldSkipDraw(entities: List<MarkerEntityInterface<Feature>>): Boolean =
        lastDrawnEmpty && entities.none { it.visible && it.marker != null }

    fun draw(entities: List<MarkerEntityInterface<Feature>>) {
        val visibleEntities = entities.filter { it.visible && it.marker != null }
        val features = visibleEntities.mapNotNull { it.marker }

        // featureCollection() forces the map engine to re-tile and invalidate the source's
        // render pass, even when the data is identical to what's already there. When tiling is
        // active, onPostProcess() calls draw() with an empty list on every ingest regardless of
        // whether anything actually changed, so an empty-to-empty call here is pure waste -
        // worst of all, it lands right after a large marker ingest, when the heap is already
        // under GC pressure from that ingest's allocations.
        if (features.isEmpty() && lastDrawnEmpty) return

        source.featureCollection(
            FeatureCollection.fromFeatures(features),
        )
        lastDrawnEmpty = features.isEmpty()
    }
}
