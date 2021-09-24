from os import path

from PyQt5 import uic
from PyQt5.QtWidgets import QDialog, QPushButton, QDoubleSpinBox, QLineEdit, QFileDialog, QTabWidget, QMessageBox, \
    QDateTimeEdit, QSpinBox, QComboBox
from osgeo import gdal

from Components.Exceptions import FileOpeningException, LayerAddingException, ModelSettingException
from subprocess import *


class UIElement:
    def __init__(self, elements, ui_path, element_type):
        self.element = element_type
        uic.loadUi(ui_path, self.element)
        self.elements = dict()
        for element in elements:
            self.elements[element[1]] = self.element.findChild(element[0], element[1])


class AddLayerWindow(UIElement):
    OBJECTS = [(QPushButton, "add_raster_btn"), (QPushButton, "choose_raster_file_btn"),
               (QDoubleSpinBox, "east_border"), (QDoubleSpinBox, "north_border"),
               (QDoubleSpinBox, "west_border"), (QDoubleSpinBox, "south_border"),
               (QLineEdit, "raster_layer_name"), (QLineEdit, "raster_path"),
               (QPushButton, "add_vector_btn"), (QPushButton, "choose_vector_file_btn"),
               (QLineEdit, "vector_layer_name"), (QLineEdit, "vector_path"), (QTabWidget, "layers_tab")]

    def __init__(self, ui_path, parent, ui):
        self.parent = parent
        self.ui = ui
        super().__init__(AddLayerWindow.OBJECTS, ui_path, QDialog(self.parent.element))

    def initialize(self):
        self.elements["choose_vector_file_btn"].clicked.connect(self.open_vector_file)
        self.elements["add_vector_btn"].clicked.connect(self.add_vector_layer)
        self.elements["choose_raster_file_btn"].clicked.connect(self.open_raster_file)
        self.elements["add_raster_btn"].clicked.connect(self.add_raster_layer)

    def open_vector_file(self):
        options = QFileDialog.Options()
        file_name, _ = QFileDialog.getOpenFileName(self.parent.element, "Открыть файл", "",
                                                   "GeoJSON, Shapefile (*.geojson *.shp)", options=options)
        if file_name:
            self.elements["vector_path"].setText(file_name)

    def open_raster_file(self):
        options = QFileDialog.Options()
        file_name, _ = QFileDialog.getOpenFileName(self.parent.element, "Открыть файл", "",
                                                   "IMG (*.jpeg *.jpg *.tif *.bmp *.png)", options=options)
        if file_name:
            self.elements["raster_path"].setText(file_name)

    def show(self, tab=0):
        self.elements["layers_tab"].setCurrentIndex(tab)
        self.elements["vector_layer_name"].setText("")
        self.elements["vector_path"].setText("")
        self.elements["raster_layer_name"].setText("")
        self.elements["raster_path"].setText("")
        self.elements["north_border"].setValue(0.0)
        self.elements["south_border"].setValue(0.0)
        self.elements["east_border"].setValue(0.0)
        self.elements["west_border"].setValue(0.0)
        self.element.show()

    def hide(self):
        self.element.hide()

    def add_raster_layer(self):
        try:
            self.ui.map_view.add_raster_layer(self.elements['raster_layer_name'].text(),
                                              self.elements['raster_path'].text(),
                                              (self.elements["south_border"].value(),
                                               self.elements["west_border"].value()),
                                              (self.elements["north_border"].value(),
                                               self.elements["east_border"].value()))
        except FileOpeningException as ex:
            self.ui.show_message(ex.message, "Ошибка!", QMessageBox.Critical, self.element)

        except LayerAddingException as ex:
            self.ui.show_message(ex.message, "Ошибка!", QMessageBox.Critical, self.element)

        else:
            self.hide()
            self.ui.update_layers_list()

    def add_vector_layer(self):
        try:
            self.ui.map_view.add_vector_layer(self.elements['vector_layer_name'].text(),
                                              self.elements['vector_path'].text())
        except FileOpeningException as ex:
            self.ui.show_message(ex.message, "Ошибка!", QMessageBox.Critical, self.element)
        except LayerAddingException as ex:
            self.ui.show_message(ex.message, "Ошибка!", QMessageBox.Critical, self.element)
        else:
            self.hide()
            self.ui.update_layers_list()


class ModelSettingsWindow(UIElement):
    OBJECTS = [(QPushButton, "choose_buildings_btn"),
               (QDoubleSpinBox, "east_border"), (QDoubleSpinBox, "north_border"),
               (QDoubleSpinBox, "west_border"), (QDoubleSpinBox, "south_border"),
               (QLineEdit, "buildings_path"), (QDateTimeEdit, "end"), (QDateTimeEdit, "start"),
               (QPushButton, "choose_fuel_codes_btn"), (QPushButton, "choose_fuel_data_btn"),
               (QPushButton, "choose_ignition_btn"), (QPushButton, "choose_relief_data_btn"),
               (QPushButton, "choose_weather_btn"), (QLineEdit, "fuel_codes_path"),
               (QLineEdit, "fuel_path"), (QComboBox, "house_material"), (QLineEdit, "ignition_path"),
               (QLineEdit, "relief_path"), (QSpinBox, "side"), (QPushButton, "start_model"),
               (QSpinBox, "weatherStep"), (QLineEdit, "weather_path"), (QLineEdit, "layer_name")]

    def __init__(self, ui_path, parent, ui):
        self.parent = parent
        self.ui = ui
        self.material = 1.0
        super().__init__(ModelSettingsWindow.OBJECTS, ui_path, QDialog(self.parent.element))

    def initialize(self):
        self.elements["start_model"].clicked.connect(self.launch_model)
        self.elements["choose_buildings_btn"].clicked.connect(self.open_osm_file)
        self.elements["choose_fuel_codes_btn"].clicked.connect(lambda _: self.open_text_file("fuel_codes_path"))
        self.elements["choose_fuel_data_btn"].clicked.connect(lambda _: self.open_raster_file("fuel_path"))
        self.elements["choose_ignition_btn"].clicked.connect(self.open_vector_file)
        self.elements["choose_relief_data_btn"].clicked.connect(lambda _: self.open_raster_file("relief_path"))
        self.elements["choose_weather_btn"].clicked.connect(lambda _: self.open_text_file("weather_path"))

    def show(self):
        self.elements["buildings_path"].setText("")
        self.elements["fuel_codes_path"].setText("")
        self.elements["fuel_path"].setText("")
        self.elements["ignition_path"].setText("")
        self.elements["relief_path"].setText("")
        self.elements["weather_path"].setText("")
        self.elements["layer_name"].setText("")

        self.elements["north_border"].setValue(0.0)
        self.elements["south_border"].setValue(0.0)
        self.elements["east_border"].setValue(0.0)
        self.elements["west_border"].setValue(0.0)

        self.elements["side"].setValue(30)
        self.elements["weatherStep"].setValue(60)
        self.element.show()

    def open_text_file(self, file_path):
        options = QFileDialog.Options()
        file_name, _ = QFileDialog.getOpenFileName(self.parent.element, "Открыть файл", "",
                                                   "CSV (*.csv)", options=options)
        if file_name:
            self.elements[file_path].setText(file_name)

    def open_raster_file(self, file_path):
        options = QFileDialog.Options()
        file_name, _ = QFileDialog.getOpenFileName(self.parent.element, "Открыть файл", "",
                                                   "GeoTiff (*.tif *.asc)", options=options)
        if file_name:
            self.elements[file_path].setText(file_name)

    def open_vector_file(self):
        options = QFileDialog.Options()
        file_name, _ = QFileDialog.getOpenFileName(self.parent.element, "Открыть файл", "",
                                                   "Shapefile (*.shp)", options=options)
        if file_name:
            self.elements["ignition_path"].setText(file_name)

    def open_osm_file(self):
        options = QFileDialog.Options()
        file_name, _ = QFileDialog.getOpenFileName(self.parent.element, "Открыть файл", "",
                                                   "OSM, Shapefile (*.osm *.shp)", options=options)
        if file_name:
            self.elements["buildings_path"].setText(file_name)

    @staticmethod
    def jarWrapper(*args):
        # process = Popen(['java', '-Djava.library.path=..\model\lib-gdal', '-jar'] + list(args), stdout=PIPE, stderr=PIPE)
        # ret = []
        # while process.poll() is None:
        #     line = process.stdout.readline()
        #     if line != '' and line.endswith('\n'.encode("utf-8")):
        #         ret.append(line[:-1])
        # stdout, stderr = process.communicate()
        # ret += stdout.split('\n'.encode("utf-8"))
        # if stderr != '':
        #     ret += stderr.split('\n'.encode("utf-8"))
        #
        # return ret
        process = check_output(['java', '-Djava.library.path=..\model\lib-gdal', '-jar'] + list(args), stderr=PIPE)

    def launch_model(self):
        try:
            if self.elements["house_material"].currentText() == "Смешанный":
                self.material = 0.8
            elif self.elements["house_material"].currentText() == "Огнеупорный":
                self.material = 0.6
            else:
                self.material = 1.0

            # args = ['../model/model-1.0.jar', "30",
            #         "C:/Users/admin/Documents/firemodel/project/data/elevation/US_DEM2016/US_DEM2016.tif",
            #         "C:/Users/admin/Documents/firemodel/project/data/US_200EVT/US_200EVT.tif",
            #         "C:/Users/admin/Documents/firemodel/project/data/US_200EVT/LF16_EVT_200.csv",
            #         "C:/Users/admin/Documents/firemodel/project/data/weather/weather.csv",
            #         "C:/Users/admin/Documents/firemodel/project/data/ignition/ignition.shp",
            #         "2019.10.28", "01:30", "2019.10.28", "11:30", "60", "1.0", "34.11", "-118.50",
            #         "34.07", "-118.47", "C:/Users/admin/Documents/firemodel/project/data/buildings/map.osm"]

            if not self.check_model_params():
                args = ['../model/model-1.0.jar', str(self.elements["side"].value()),
                        self.elements["relief_path"].text(), self.elements["fuel_path"].text(),
                        self.elements["fuel_codes_path"].text(), self.elements["weather_path"].text(),
                        self.elements["ignition_path"].text(),
                        str(self.elements["start"].dateTime().toString("yyyy.MM.dd HH:mm")).split()[0],
                        str(self.elements["start"].dateTime().toString("yyyy.MM.dd HH:mm")).split()[1],
                        str(self.elements["end"].dateTime().toString("yyyy.MM.dd HH:mm")).split()[0],
                        str(self.elements["end"].dateTime().toString("yyyy.MM.dd HH:mm")).split()[1],
                        str(self.elements["weatherStep"].value()),
                        str(self.material), str(self.elements["north_border"].value()),
                        str(self.elements["west_border"].value()),
                        str(self.elements["south_border"].value()), str(self.elements["east_border"].value()),
                        self.elements["buildings_path"].text()]

                self.jarWrapper(*args)

                self.ui.show_message("Моделирование прошло успешно", "Успешно", QMessageBox.Information)
        except CalledProcessError:
            self.ui.show_message("Ошибка при моделировании", "Ошибка", QMessageBox.Critical)
        except LayerAddingException as ex:
            self.ui.show_message(ex.message, "Ошибка", QMessageBox.Critical)
        except FileOpeningException as ex:
            self.ui.show_message(ex.message, "Ошибка", QMessageBox.Critical)
        except ModelSettingException as ex:
            self.ui.show_message(ex.message, "Ошибка", QMessageBox.Critical)
        else:
            self.hide()

            try:
                self.ui.map_view.add_raster_layer(self.elements["layer_name"].text(),
                                                  "../data/result/result_" + str(self.elements["end"]
                                                      .dateTime().toString(
                                                      "yyyy_MM_dd_HH_mm")) + ".tif",
                                                  (self.elements["south_border"].value(),
                                                   self.elements["west_border"].value()),
                                                  (self.elements["north_border"].value(),
                                                   self.elements["east_border"].value()),
                                                  color=gdal.GCI_RedBand)
                self.ui.update_layers_list()
            except FileOpeningException as ex:
                self.ui.show_message(ex.message, "Ошибка", QMessageBox.Critical, self.element)

    def hide(self):
        self.element.hide()

    def check_file(self, file_name):
        ds = gdal.Open(self.elements["relief_path"].text(), gdal.GA_ReadOnly)
        if not ds:
            raise FileOpeningException("Ошибка при открытии файла" + file_name + "!")
        else:
            ds = None

    def check_model_params(self):
        self.check_file(self.elements["relief_path"].text())
        self.check_file(self.elements["fuel_path"].text())

        if not path.exists(self.elements["fuel_codes_path"].text()):
            raise FileOpeningException("Файла кодов топлива по данному пути не существует")

        if not path.exists(self.elements["weather_path"].text()):
            raise FileOpeningException("Файла погоды по данному пути не существует")

        if not path.exists(self.elements["ignition_path"].text()):
            raise FileOpeningException("Файла территории начального возгорания"
                                       " по данному пути не существует")

        # Даты не должны заходить одна за другую.
        if self.elements["start"].dateTime().secsTo(self.elements["end"].dateTime()) <= 0:
            raise ModelSettingException("Дата окончания должна быть позже даты начала")

        # Расположение координат.
        if self.elements["south_border"].value() > self.elements["north_border"].value():
            raise ModelSettingException("Южная граница не может быть выше северной!")
        if self.elements["west_border"].value() > self.elements["east_border"].value():
            raise ModelSettingException("Западная граница не может быть левее восточной!")

        if not self.ui.map_view.check_layer_name(self.elements["layer_name"].text()):
            raise LayerAddingException("Некорректное название слоя!")

        if self.ui.map_view.has_layer(self.elements["layer_name"].text()):
            raise LayerAddingException("Слой с таким названием уже существует")

        return 0
