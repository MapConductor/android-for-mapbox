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
    private var controller: MapboxMapViewControllerInterface? = null

    private var _mapDesignType: MapboxDesignType = mapDesignType

    override var mapDesignType: MapboxDesignType
        set(value) {
            _mapDesignType = value
            this.controller?.setMapDesignType(value)
        }
        get() = _mapDesignType

    internal fun setController(controller: MapboxMapViewControllerInterface) {
        this.controller = controller
        attachController(controller)
    }

    internal fun onMapDesignTypeChange(value: MapboxDesignType) {
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
        bundle.putString("id", state.mapDesignType.id)
    }

    override fun createState(
        stateId: String,
        mapDesignBundle: Bundle?,
        cameraPosition: MapCameraPosition,
    ): MapboxViewState =
        MapboxViewState(
            id = stateId,
            mapDesignType =
                MapboxMapDesign.create(
                    layerId = mapDesignBundle?.getString("id") ?: Standard.id,
                ),
            cameraPosition = cameraPosition,
        )

    override fun getStateId(state: MapboxViewState): String = state.id
}

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
