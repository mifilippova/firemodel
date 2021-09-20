import os
import geojson
from PyQt5.QtWidgets import QMessageBox

from Components.Exceptions import LayerAddingException, FileOpeningException, MapCreatingException, \
    LayerNotFoundException
from Components.Layers import RasterLayer, VectorLayer
from Components.MapEngine import DEFAULT_HTML, OSM_TILE_CREATION_SCRIPT, \
    MAP_CREATION_SCRIPT, ADD_TILE_TO_MAP_SCRIPT, RASTER_LAYER_CREATION_SCRIPT, GEOJSON_LAYER_CREATION_SCRIPT, \
    GEOJSON_LAYER_ADD_DATA_SCRIPT, SHOW_LAYER_SCRIPT, HIDE_LAYER_SCRIPT, BRING_TO_BACK_SCRIPT, BRING_TO_FRONT_SCRIPT,\
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
            else:
                try:
                    self.load(self.save_file_path)
                except LayerAddingException as ex:
                    self.ui.project_opened(False, self.window, ex.message)
                except FileOpeningException as ex:
                    self.ui.project_opened(False, self.window, ex.message)
                except MapCreatingException as ex:
                    self.ui.project_opened(False, self.window, ex.message)
                else:
                    self.ui.project_opened(True, self.window)

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
            raise LayerAddingException("Layer with this name is already added")

    def add_raster_layer(self, layer_name, file_path, upper_left_bound, lower_right_bound, data=None):
        if not self.check_layer_name(layer_name):
            raise LayerAddingException("Incorrect layer name")
        if self.has_layer(layer_name):
            raise LayerAddingException("Layer with this name is already added")
        if data is None and not os.path.exists(file_path):
            raise FileOpeningException("File not found!")
        else:
            bounds = [upper_left_bound, lower_right_bound]
            string_bounds = "[[" + str(bounds[0][0]) + ", " + str(bounds[0][1]) + "], [" + \
                            str(bounds[1][0]) + ", " + str(bounds[1][1]) + "]]"

            if data is None:
                data = image_to_data(file_path)
            self.window.page().runJavaScript("var createLayerData = '" + data + "';\n" +
                                             RASTER_LAYER_CREATION_SCRIPT % (layer_name, string_bounds, layer_name))
            self.layers.append(RasterLayer(layer_name, data, bounds))

    def add_vector_layer(self, layer_name, path, data=None):
        global geo_data
        if not self.check_layer_name(layer_name):
            raise LayerAddingException("Incorrect layer name")
        if self.has_layer(layer_name):
            raise LayerAddingException("Layer with this name is already added")
        if data is None:
            try:
                file_format = os.path.splitext(path)[-1][1:]

                if file_format == "shp":
                    path = shp_to_json(path)

                geo_file = open(path, 'r')
                geo_data = geojson.load(geo_file)
                geo_file.close()
            except Exception:
                raise FileOpeningException("File can't be read!")
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
            raise LayerNotFoundException("Layer not found")
        self.layers.remove(layer)
        self.window.page().runJavaScript(REMOVE_LAYER_SCRIPT % (layer_name, layer_name))

    def set_visible(self, layer_name, is_visible):
        layer = self.has_layer(layer_name, True)
        if layer is None:
            raise LayerNotFoundException("Layer not found")
        layer.is_visible = is_visible
        if layer.is_visible:
            self.window.page().runJavaScript(SHOW_LAYER_SCRIPT % (layer_name, layer_name))
        else:
            self.window.page().runJavaScript(HIDE_LAYER_SCRIPT % (layer_name, layer_name))

    def bring_to_back(self, layer_name):
        layer = self.has_layer(layer_name, True)
        if layer is None:
            raise LayerNotFoundException("Layer not found")
        self.window.page().runJavaScript(BRING_TO_BACK_SCRIPT % layer_name)

    def bring_to_front(self, layer_name):
        layer = self.has_layer(layer_name, True)
        if layer is None:
            raise LayerNotFoundException("Layer not found")
        self.window.page().runJavaScript(BRING_TO_FRONT_SCRIPT % layer_name)

    def save(self):
        try:
            file = open(self.save_file_path, 'w')
            lines = ['[FireMap]\n', self.map_tiles + "\n"]
            for layer in self.layers:
                lines.append(layer.to_save() + "\n")
            file.writelines(lines)
            file.close()
            self.ui.show_message("File saved!", "Success", QMessageBox.Information)
        except Exception:
            self.ui.show_message("Error occurred", "Error", QMessageBox.Critical)

    def load_splitted_data(self, blocks, variable_name):
        self.window.page().runJavaScript("""
               var %s = "";
           """ % variable_name)
        for block in blocks:
            self.window.page().runJavaScript("""
                   %s += "%s";
               """ % (variable_name, block))

    def load(self, path):
        try:
            file = open(path, 'r')
        except Exception:
            raise FileOpeningException("File can't be read!")
        try:
            lines = file.readlines()
            if lines[0] != "[FireMap]\n":
                file.close()
                raise FileOpeningException("File is not save file")
            lines[1] = lines[1].replace("\n", "")
            if lines[1] not in MapView.TILES_STRING_TO_SCRIPT.keys():
                file.close()
                raise MapCreatingException("Undefined map tiles")
            self.map_tiles = lines[1]
            self.window.page().runJavaScript(MAP_CREATION_SCRIPT + MapView.TILES_STRING_TO_SCRIPT[self.map_tiles] +
                                             ADD_TILE_TO_MAP_SCRIPT)
            for i in range(2, len(lines)):
                line = lines[i]
                splitted_line = line.split("|splitter|")
                splitted_line[-1] = splitted_line[-1].replace("\n", "")
                if splitted_line[0] == "raster":
                    self.add_raster_layer(splitted_line[1], "", [splitted_line[3], splitted_line[4]],
                                          [splitted_line[5], splitted_line[6]], splitted_line[7])
                    if splitted_line[2] == "False":
                        self.set_visible(splitted_line[1], False)
                    else:
                        self.set_visible(splitted_line[1], True)
                elif splitted_line[0] == "vector":
                    self.add_vector_layer(splitted_line[1], "", splitted_line[3])
                    if splitted_line[2] == "False":
                        self.set_visible(splitted_line[1], False)
                    else:
                        self.set_visible(splitted_line[1], True)

        except Exception:
            file.close()
            raise FileOpeningException("Damaged file!")
        file.close()
