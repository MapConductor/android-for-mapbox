package com.mapconductor.mapbox

import MapboxMapViewControllerInterface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.mapconductor.compose.map.BaseMapViewSaver
import com.mapconductor.core.map.MapCameraPosition
import com.mapconductor.core.map.MapCameraPositionInterface
import com.mapconductor.core.map.MapViewState
import com.mapconductor.core.map.MapViewStateBundle
import com.mapconductor.core.map.MapViewStateInterface
import com.mapconductor.mapbox.MapboxMapDesign.Standard
import java.util.UUID
import android.os.Bundle

interface MapboxViewStateInterface : MapViewStateInterface<MapboxDesignType>

class MapboxViewState(
    mapDesignType: MapboxDesignType,
    override val id: String,
    cameraPosition: MapCameraPosition = MapCameraPosition.Default,
) : MapViewState<MapboxDesignType>(cameraPosition),
    MapboxViewStateInterface {
    // Read through the base so that letting go of the controller there (MapViewBase
    // does it when the map is torn down) leaves nothing behind here.
    private val controller: MapboxMapViewControllerInterface?
        get() = attachedMapController as? MapboxMapViewControllerInterface

    private var _mapDesignType: MapboxDesignType = mapDesignType

    override var mapDesignType: MapboxDesignType
        set(value) {
            _mapDesignType = value
            this.controller?.setMapDesignType(value)
        }
        get() = _mapDesignType

    internal fun setController(controller: MapboxMapViewControllerInterface) {
        attachController(controller)
    }

    internal fun onMapDesignTypeChange(value: MapboxDesignType) {
        // 地図から読み戻した値はスタイル URI から作ったデザインで、attributionRules を持たない。
        // 同じスタイルなら、アプリが渡した（あるいは復元した）デザインを残す。
        if (value.getValue() == _mapDesignType.getValue()) return
        _mapDesignType = value
    }

    /** 戻り型をこのプロバイダのホルダーへ絞る（アプリが `?.map` を取れる形を保つため）。 */
    override fun getMapViewHolder(): MapboxMapViewHolder? = super.getMapViewHolder() as? MapboxMapViewHolder

    internal fun updateCameraPosition(cameraPosition: MapCameraPosition) {
        setCameraPositionInternal(cameraPosition)
    }
}

class MapboxMapViewSaver : BaseMapViewSaver<MapboxViewState>() {
    override fun saveMapDesign(
        state: MapboxViewState,
        bundle: Bundle,
    ) {
        val design = state.mapDesignType
        bundle.putString("id", design.id)
        // id だけでは Custom / StyleUri / 定義済みの区別がつかず、StyleUri（URI そのもの）が
        // mapbox://styles/mapbox/<uri> の Custom として戻ってしまう。
        val kind =
            when (design) {
                is MapboxMapDesign.StyleUri -> KIND_STYLE_URI
                is MapboxMapDesign.Custom -> KIND_CUSTOM
                is MapboxMapDesign -> if (design === MapboxMapDesign.create(design.id)) KIND_PRESET else KIND_VALUE
                else -> KIND_VALUE
            }
        bundle.putString(KEY_KIND, kind)
        bundle.putString(KEY_VALUE, design.getValue())
        MapViewStateBundle.putAttributionRules(bundle, design.attributionRules)
    }

    override fun createState(
        stateId: String,
        mapDesignBundle: Bundle?,
        cameraPosition: MapCameraPosition,
    ): MapboxViewState =
        MapboxViewState(
            id = stateId,
            mapDesignType = restoreDesign(mapDesignBundle),
            cameraPosition = cameraPosition,
        )

    private fun restoreDesign(bundle: Bundle?): MapboxMapDesign {
        val id = bundle?.getString("id") ?: return Standard
        val rules = MapViewStateBundle.getAttributionRules(bundle)
        return when (bundle.getString(KEY_KIND)) {
            KIND_STYLE_URI -> MapboxMapDesign.StyleUri(uri = id, attributionRules = rules)
            KIND_CUSTOM -> MapboxMapDesign.Custom(layerId = id, attributionRules = rules)
            KIND_VALUE -> {
                // アプリ独自の実装。地図に渡していた値（スタイル URI）から作り直す。
                val value = bundle.getString(KEY_VALUE) ?: id
                val prefix = "${MapboxMapDesign.MAPBOX_URL}/"
                if (value.startsWith(prefix)) {
                    MapboxMapDesign.Custom(layerId = value.removePrefix(prefix), attributionRules = rules)
                } else {
                    MapboxMapDesign.StyleUri(uri = value, attributionRules = rules)
                }
            }
            // KIND_PRESET と、kind を持たない以前の形式
            else -> MapboxMapDesign.create(layerId = id)
        }
    }

    override fun getStateId(state: MapboxViewState): String = state.id
}

// 公開 API の門番（javap）にコンパニオンの定数が載らないよう、ファイル private にする。
private const val KEY_KIND = "kind"
private const val KEY_VALUE = "value"
private const val KIND_PRESET = "preset"
private const val KIND_CUSTOM = "custom"
private const val KIND_STYLE_URI = "styleUri"
private const val KIND_VALUE = "value"

@Composable
fun rememberMapboxMapViewState(
    mapDesign: MapboxDesignType = Standard,
    cameraPosition: MapCameraPositionInterface = MapCameraPosition.Default,
): MapboxViewState {
    val stateId by rememberSaveable {
        val uuid = UUID.randomUUID().toString()
        mutableStateOf(uuid)
    }
    val state =
        rememberSaveable(
            stateSaver = MapboxMapViewSaver().createSaver(),
        ) {
            mutableStateOf(
                MapboxViewState(
                    id = stateId,
                    mapDesignType = mapDesign,
                    cameraPosition = MapCameraPosition.from(cameraPosition),
                ),
            )
        }

    return state.value
}
