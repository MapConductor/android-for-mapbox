package com.mapconductor.mapbox.raster

import com.mapconductor.core.raster.RasterLayerController
import com.mapconductor.core.raster.RasterLayerManager
import com.mapconductor.core.raster.RasterLayerManagerInterface

class MapboxRasterLayerController(
    rasterLayerManager: RasterLayerManagerInterface<MapboxRasterLayerHandle> = RasterLayerManager(),
    renderer: MapboxRasterLayerOverlayRenderer,
) : RasterLayerController<MapboxRasterLayerHandle>(rasterLayerManager, renderer)
