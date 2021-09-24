import os
import geojson
from osgeo import gdal

from Components.Exceptions import LayerAddingException, FileOpeningException, MapCreatingException, \
    LayerNotFoundException
from Components.Layers import RasterLayer, VectorLayer
from Components.MapEngine import DEFAULT_HTML, OSM_TILE_CREATION_SCRIPT, \
    MAP_CREATION_SCRIPT, ADD_TILE_TO_MAP_SCRIPT, RASTER_LAYER_CREATION_SCRIPT, GEOJSON_LAYER_CREATION_SCRIPT, \
    GEOJSON_LAYER_ADD_DATA_SCRIPT, SHOW_LAYER_SCRIPT, HIDE_LAYER_SCRIPT, \
    REMOVE_LAYER_SCRIPT
from Components.Utilities import image_to_data, shp_to_json


class MapView:
    TILES_STRING_TO_SCRIPT = {"OpenStreetMap": OSM_TILE_CREATION_SCRIPT}

    def __init__(self, window, map_tiles="OpenStreetMap", save_file_path=None, ui=None):
        if map_tiles not in MapView.TILES_STRING_TO_SCRIPT.keys():  # ["OpenStreetMap", "Mapbox Bright", "Mapbox Control Room", "Stamen"]:
            raise Exception("Undefined map tiles")
        self.layers = []
        self.save_file_path = save_file_path
        self.ui = ui
        self.map_tiles = map_tiles
        self.window = window
        self.window.setHtml(DEFAULT_HTML)
        self.window.loadFinished.connect(self.on_load_finished)

    def on_load_finished(self, status):
        if status:
            if self.save_file_path is None:
                self.window.page().runJavaScript(MAP_CREATION_SCRIPT + MapView.TILES_STRING_TO_SCRIPT[self.map_tiles] +
                                                 ADD_TILE_TO_MAP_SCRIPT)

    def has_layer(self, layer_name, return_layer=False):
        index = -1
        for i in range(0, len(self.layers)):
            if self.layers[i].name == layer_name:
                index = i
        if return_layer:
            if index == -1:
                return None
            else:
                return self.layers[index]
        else:
            return index != -1

    def add_map_layer(self, layer_name, map_type):
        if self.has_layer(layer_name):
            raise LayerAddingException("Слой с таким названием уже существует!")

    def add_raster_layer(self, layer_name, file_path, upper_left_bound, lower_right_bound,
                         color=gdal.GCI_GrayIndex, data=None):
        if not self.check_layer_name(layer_name):
            raise LayerAddingException("Некорректное название слоя!")
        if self.has_layer(layer_name):
            raise LayerAddingException("Слой с таким названием уже существует")

        if lower_right_bound[0] < upper_left_bound[0]:
            raise LayerAddingException("Южная граница не может быть выше северной!")
        if lower_right_bound[1] < upper_left_bound[1]:
            raise LayerAddingException("Западная граница не может быть левее восточной!")

        if data is None and not os.path.exists(file_path):
            raise FileOpeningException("Файл не найден!")
        else:
            bounds = [upper_left_bound, lower_right_bound]
            string_bounds = "[[" + str(bounds[0][0]) + ", " + str(bounds[0][1]) + "], [" + \
                            str(bounds[1][0]) + ", " + str(bounds[1][1]) + "]]"
            if data is None:
                data = image_to_data(file_path, color)
            self.window.page().runJavaScript("var createLayerData = '" + data + "';\n" +
                                             RASTER_LAYER_CREATION_SCRIPT % (layer_name, string_bounds, layer_name))
            self.layers.append(RasterLayer(layer_name, data, bounds))

    def add_vector_layer(self, layer_name, path, data=None):
        global geo_data
        if not self.check_layer_name(layer_name):
            raise LayerAddingException("Некорректное название слоя")
        if self.has_layer(layer_name):
            raise LayerAddingException("Слой с таким названием уже существует!")
        if not os.path.exists(path):
            raise FileOpeningException("Файл не найден!")

        if data is None:
            try:
                file_format = os.path.splitext(path)[-1][1:]

                if file_format == "shp":
                    path = shp_to_json(path)

                geo_file = open(path, 'r')
                geo_data = geojson.load(geo_file)
                geo_file.close()
            except Exception:
                raise FileOpeningException("Невозможно прочитать файл!")
            else:
                self.layers.append(VectorLayer(layer_name, str(geo_data)))
                self.window.page().runJavaScript(GEOJSON_LAYER_CREATION_SCRIPT % (layer_name, layer_name))
                self.window.page().runJavaScript(GEOJSON_LAYER_ADD_DATA_SCRIPT % (layer_name, str(geo_data)))
        else:
            self.layers.append(VectorLayer(layer_name, data))
            self.window.page().runJavaScript(GEOJSON_LAYER_CREATION_SCRIPT % (layer_name, layer_name))
            self.window.page().runJavaScript(GEOJSON_LAYER_ADD_DATA_SCRIPT % (layer_name, str(data)))

    @staticmethod
    def check_layer_name(layer_name):
        layer_name = layer_name.replace(" ", "")
        return len(layer_name) > 0

    def remove_layer(self, layer_name):
        layer = self.has_layer(layer_name, True)
        if layer is None:
            raise LayerNotFoundException("Слой не найден")
        self.layers.remove(layer)
        self.window.page().runJavaScript(REMOVE_LAYER_SCRIPT % (layer_name, layer_name))

    def set_visible(self, layer_name, is_visible):
        layer = self.has_layer(layer_name, True)
        if layer is None:
            raise LayerNotFoundException("Слой не найден")
        layer.is_visible = is_visible
        if layer.is_visible:
            self.window.page().runJavaScript(SHOW_LAYER_SCRIPT % (layer_name, layer_name))
        else:
            self.window.page().runJavaScript(HIDE_LAYER_SCRIPT % (layer_name, layer_name))
