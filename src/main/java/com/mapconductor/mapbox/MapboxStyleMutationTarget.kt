package com.mapconductor.mapbox

import com.mapbox.bindgen.Expected
import com.mapbox.bindgen.None
import com.mapbox.bindgen.Value
import com.mapbox.maps.LayerPosition
import com.mapbox.maps.Style
import com.mapconductor.core.InternalMapConductorApi
import com.mapconductor.core.map.StyleMutationTarget

/**
 * Changes a loaded Mapbox style in place.
 *
 * The seven calls [StyleMutationTarget] asks for, against Mapbox's style
 * API. Everything above them — what a mutation means, which can be skipped,
 * keeping one bad property from costing the rest — is
 * `applyStyleMutations` in core, shared with every other provider.
 *
 * ## Why this is shorter than the MapLibre one
 *
 * Mapbox v11 keeps a generic door open: `setStyleLayerProperty` takes the
 * spec's own property name and a `Value`, and `Value.fromJson` builds one
 * straight from text. Paint keys, layout keys, `filter`, `minzoom` and
 * `maxzoom` all go through that one call, and a whole layer document goes
 * through `addStyleLayer`. There is no typed-constructor detour and no
 * expression builder, so the compiler's JSON reaches the renderer unread.
 *
 * MapLibre on Android has no such door, which is why
 * `MapLibreStyleMutationTarget` is three times the length for the same seven
 * calls. The adapters differ; the decisions above them do not.
 */
@InternalMapConductorApi
internal class MapboxStyleMutationTarget(
    private val style: () -> Style?,
    /**
     * Whether the style on screen is the document these deltas are for.
     *
     * Not `style() != null`: a vector style hands the map a new document
     * and applies the deltas compiled for it straight after, and in between
     * the map still has the one being replaced. See
     * `MapboxMapViewController.isShowingRequestedStyle`.
     */
    private val ready: () -> Boolean,
) : StyleMutationTarget {
    override val isReady: Boolean get() = ready()

    override fun hasLayer(layerId: String): Boolean = style()?.styleLayerExists(layerId) == true

    override fun setPaint(
        layerId: String,
        key: String,
        valueJson: String,
    ) = setProperty(layerId, key, valueJson)

    // Mapbox does not split paint from layout on this call: both are the
    // spec's property name on the layer.
    override fun setLayout(
        layerId: String,
        key: String,
        valueJson: String,
    ) = setProperty(layerId, key, valueJson)

    override fun setFilter(
        layerId: String,
        filterJson: String,
    ) = setProperty(layerId, "filter", filterJson)

    override fun setZoomRange(
        layerId: String,
        minZoom: Float,
        maxZoom: Float,
    ) {
        val target = style() ?: return
        // Both ends, because the caller always sends both -- a mutation that
        // moves one has to restate the other to be undoable.
        orThrow("minzoom", target.setStyleLayerProperty(layerId, "minzoom", Value(minZoom.toDouble())))
        orThrow("maxzoom", target.setStyleLayerProperty(layerId, "maxzoom", Value(maxZoom.toDouble())))
    }

    override fun addLayer(
        layerId: String,
        layerJson: String,
        beforeId: String?,
    ) {
        val target = style() ?: return
        // Already there: the style was reloaded and the whole set is going
        // back on.
        if (target.styleLayerExists(layerId)) return
        val layer = valueOf(layerJson)
        // `beforeId` is the layer to go *under*, matching the style
        // document's own order -- earlier in the list is drawn first.
        val position =
            beforeId
                ?.takeIf { target.styleLayerExists(it) }
                ?.let { LayerPosition(null, it, null) }
        orThrow("addLayer", target.addStyleLayer(layer, position))
    }

    override fun removeLayer(layerId: String) {
        val target = style() ?: return
        // Mapbox calls removing an absent layer an error; the contract says
        // it is not one.
        if (!target.styleLayerExists(layerId)) return
        orThrow("removeLayer", target.removeStyleLayer(layerId))
    }

    private fun setProperty(
        layerId: String,
        key: String,
        valueJson: String,
    ) {
        val target = style() ?: return
        orThrow(key, target.setStyleLayerProperty(layerId, key, valueOf(valueJson)))
    }

    /**
     * A JSON text as the `Value` Mapbox wants.
     *
     * Literals, expression arrays and whole layer objects all arrive the same
     * way, so there is nothing here to decide — unlike MapLibre, which needs
     * the value's shape chosen for it.
     */
    private fun valueOf(json: String): Value {
        val parsed = Value.fromJson(json)
        if (parsed.isError) throw IllegalArgumentException("not a style value: ${parsed.error}")
        return parsed.value ?: Value.nullValue()
    }

    /**
     * Mapbox answers with an error rather than throwing, and a refused
     * property that went unnoticed would leave the map looking unlike what
     * the rules asked for with nothing said. Throwing puts it back on the
     * path core already has for this: the mutation comes back unapplied and
     * the caller reports it.
     */
    private fun orThrow(
        what: String,
        result: Expected<String, None>,
    ) {
        if (result.isError) throw UnsupportedOperationException("$what: ${result.error}")
    }
}
