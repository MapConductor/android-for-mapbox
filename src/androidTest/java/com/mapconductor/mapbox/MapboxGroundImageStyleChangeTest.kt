package com.mapconductor.mapbox

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mapbox.common.MapboxOptions
import com.mapbox.maps.Style
import com.mapconductor.compose.groundimage.GroundImage
import com.mapconductor.core.features.GeoPoint
import com.mapconductor.core.features.GeoRectBounds
import com.mapconductor.core.groundimage.GroundImageState
import com.mapconductor.core.map.AttributionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A ground image survives the map design changing under it.
 *
 * Replacing the style throws away every source and layer added at runtime, so
 * the SDK has to put the image back into the new style. iOS kept the handle it
 * had made for the old style and skipped an unchanged image (ios-for-maplibre
 * #12); this checks the Android path — `setMapDesignType` → `loadStyle` →
 * `subscribeStyleLoaded` — on a real MapView loading two local style documents.
 *
 * The styles have no sources, so nothing is fetched and no real access token
 * is needed; a placeholder satisfies the SDK's check.
 */
@RunWith(AndroidJUnit4::class)
class MapboxGroundImageStyleChangeTest {
    @get:Rule
    val compose = createComposeRule()

    private class LocalStyle(override val id: String) : MapboxDesignType {
        override val attributionRules: List<AttributionRule> = emptyList()

        override fun getValue(): String =
            """{"version": 8, "name": "$id", "sources": {},
               "layers": [{"id": "background-$id", "type": "background"}]}"""
    }

    private val first = LocalStyle("first")
    private val second = LocalStyle("second")

    private fun groundImage(): GroundImageState {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        return GroundImageState(
            bounds = GeoRectBounds(GeoPoint(35.6, 139.6), GeoPoint(35.8, 139.9)),
            image = BitmapDrawable(resources, bitmap),
            id = "tokyo",
        )
    }

    /** The loaded style, when it is the one the given design describes. */
    private fun styleOf(state: MapboxViewState, design: LocalStyle): Style? {
        var style: Style? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            style = state.getMapViewHolder()?.map?.style
                ?.takeIf { it.isStyleLoaded() && it.styleLayerExists("background-${design.id}") }
        }
        return style
    }

    private fun hasGroundImage(style: Style?): Boolean {
        var found = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = style != null &&
                style.styleSourceExists("mc-gimg-src-tokyo") &&
                style.styleLayerExists("mc-gimg-lyr-tokyo")
        }
        return found
    }

    private var shown by mutableStateOf(true)

    private fun show(state: MapboxViewState, image: GroundImageState) {
        compose.setContent {
            MapboxMapView(
                state = state,
                modifier = Modifier.fillMaxSize(),
                sdkInitialize = {
                    MapboxOptions.accessToken = "pk.placeholder-for-local-styles"
                    true
                },
            ) {
                if (shown) GroundImage(image)
            }
        }
    }

    @Test
    fun anUnchangedGroundImageIsInTheNewStyle() {
        val state = MapboxViewState(mapDesignType = first, id = "unchanged")
        show(state, groundImage())
        compose.waitUntil(TIMEOUT) { hasGroundImage(styleOf(state, first)) }

        compose.runOnUiThread { state.mapDesignType = second }
        compose.waitUntil(TIMEOUT) { styleOf(state, second) != null }
        compose.waitUntil(TIMEOUT) { hasGroundImage(styleOf(state, second)) }
    }

    @Test
    fun theSameDesignSetAgainKeepsTheImage() {
        val state = MapboxViewState(mapDesignType = first, id = "same")
        show(state, groundImage())
        compose.waitUntil(TIMEOUT) { hasGroundImage(styleOf(state, first)) }

        compose.runOnUiThread { state.mapDesignType = first }
        Thread.sleep(SETTLE_MS)
        compose.waitUntil(TIMEOUT) { hasGroundImage(styleOf(state, first)) }
    }

    @Test
    fun aGroundImageRemovedAfterTheChangeLeavesTheNewStyle() {
        val state = MapboxViewState(mapDesignType = first, id = "removed")
        show(state, groundImage())
        compose.waitUntil(TIMEOUT) { hasGroundImage(styleOf(state, first)) }

        compose.runOnUiThread { state.mapDesignType = second }
        compose.waitUntil(TIMEOUT) { hasGroundImage(styleOf(state, second)) }

        compose.runOnUiThread { shown = false }
        compose.waitUntil(TIMEOUT) {
            val style = styleOf(state, second)
            style != null && !hasGroundImage(style)
        }
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val SETTLE_MS = 1_500L
    }
}
