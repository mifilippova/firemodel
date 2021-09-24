from PyQt5.QtCore import QPropertyAnimation, QParallelAnimationGroup, QEvent, Qt
from PyQt5.QtWebEngineWidgets import QWebEngineView
from PyQt5.QtWidgets import QMainWindow, QFrame, QHBoxLayout, QMenuBar, QAction, QMessageBox, QMenu, QListWidget, \
    QFileDialog, QLabel

from Components.MapView import MapView
from UI.UIElements import UIElement, AddLayerWindow, ModelSettingsWindow

WINDOW_SIZE = 0


class UIWindows:
    MAIN_WINDOW_OBJECTS = [(QMenuBar, "menubar"), (QHBoxLayout, "mainContent"), (QListWidget, "listOfLayers")]

    def __init__(self):
        self.main_window = UIElement(UIWindows.MAIN_WINDOW_OBJECTS, "UI/MainWindow.ui", QMainWindow())
        self.add_layer_window = AddLayerWindow("UI/AddLayerWindow.ui", self.main_window, self)
        self.launch_model_window = ModelSettingsWindow("UI/ModelSettingsWindow.ui", self.main_window, self)

        self.loading_view = None
        self.web = QWebEngineView()
        self.main_window.elements["mainContent"].addWidget(self.web, stretch=1)
        self.map_view = MapView(self.web, ui=self)

        self.add_layer_window.initialize()
        self.launch_model_window.initialize()

        self.initialize_menubar()

        self.main_window.elements["listOfLayers"].setContextMenuPolicy(Qt.CustomContextMenu)
        self.main_window.elements["listOfLayers"].customContextMenuRequested.connect(self.show_layers_context_menu)
        self.main_window.element.show()

    def initialize_menubar(self):
        self.main_window.element.findChild(QAction, "actionNew_project").triggered.connect(self.new_project)
        # self.main_window.element.findChild(QAction, "actionOpen_project").triggered.connect(self.open_project)
        # self.main_window.element.findChild(QAction, "actionSave_project").triggered.connect(self.save_project)
        self.main_window.element.findChild(QAction, "actionExit") \
            .triggered.connect(lambda: self.main_window.element.close())

        action = self.main_window.element.findChild(QAction, "actionAdd_raster_layer")
        action.triggered.connect(self.show_add_raster_layer_window)

        action = self.main_window.element.findChild(QAction, "actionAdd_vector_layer")
        action.triggered.connect(self.show_add_vector_layer_window)

        self.main_window.element.findChild(QAction, "actionShow_list_of_layers").toggled.connect(
            lambda checked: self.show_layers() if checked else self.hide_layers())

        self.main_window.element.findChild(QAction, "actionStart_model").triggered.connect(self.show_launch_model_window)

    def show_layers_context_menu(self, point):
        if self.main_window.elements["listOfLayers"].itemAt(point):
            layer_name = self.main_window.elements["listOfLayers"].itemAt(point).text()
            context_menu = QMenu()

            info_action = QAction("Видимость", context_menu)
            info_action.setCheckable(True)
            info_action.setChecked(self.map_view.has_layer(layer_name, True).is_visible)
            info_action.toggled.connect(lambda checked: self.map_view.set_visible(layer_name, True) if checked else
            self.map_view.set_visible(layer_name, False))

            # bring_to_back_action = QAction("На задний план", context_menu)
            # bring_to_back_action.triggered.connect(lambda _: self.map_view.bring_to_back(layer_name))
            #
            # bring_to_front_action = QAction("На передний план", context_menu)
            # bring_to_front_action.triggered.connect(lambda _: self.map_view.bring_to_front(layer_name))

            remove_action = QAction("Удалить слой", context_menu)
            remove_action.triggered.connect(self.remove_layer)

            context_menu.addAction(info_action)
            # context_menu.addAction(bring_to_back_action)
            # context_menu.addAction(bring_to_front_action)
            context_menu.addAction(remove_action)

            point.setY(point.y() + 10)
            context_menu.exec(self.main_window.elements["listOfLayers"].mapToGlobal(point))

    def show_add_raster_layer_window(self):
        self.add_layer_window.show(0)

    def show_add_vector_layer_window(self):
        self.add_layer_window.show(1)

    def show_launch_model_window(self):
        self.launch_model_window.show()


    def update_layers_list(self):
        self.main_window.element.findChild(QListWidget, "listOfLayers").clear()
        for layer in self.map_view.layers:
            self.main_window.element.findChild(QListWidget, "listOfLayers").addItem(layer.name)

    def show_layers_list(self):
        width = self.main_window.element.findChild(QFrame, "left_side_content").width()

        if width == 30:
            newWidth = 300
            self.main_window.element.findChild(QLabel, "label").setText("Слои")
        else:
            newWidth = 30
            self.main_window.element.findChild(QLabel, "label").setText("")

        self.main_window.element.animation = QPropertyAnimation(self.main_window
                                                                .element.findChild(QFrame, "left_side_content"),
                                                                b"minimumWidth")
        self.main_window.element.animation.setStartValue(width)
        self.main_window.element.animation.setEndValue(newWidth)

        self.main_window.element.animation_group = QParallelAnimationGroup()
        self.main_window.element.animation_group.addAnimation(self.main_window.element.animation)
        self.main_window.element.animation_group.start()

    def show_message(self, string, caption, icon, parent=None):
        if parent is None:
            parent = self.main_window.element
        message_box = QMessageBox(parent)
        message_box.setIcon(icon)
        message_box.setText(caption)
        message_box.setInformativeText(string)
        message_box.setWindowTitle(caption)
        message_box.exec_()

    def show_layers(self):
        self.main_window.element.findChild(QAction, "actionShow_list_of_layers").setChecked(True)
        self.show_layers_list()

    def hide_layers(self):
        self.main_window.element.findChild(QAction, "actionShow_list_of_layers").setChecked(False)
        self.show_layers_list()

    def remove_layer(self):
        if self.main_window.element.findChild(QListWidget, "listOfLayers").currentItem() is not None:
            self.map_view.remove_layer(
                self.main_window.element.findChild(QListWidget, "listOfLayers").currentItem().text())
            self.update_layers_list()

    def new_project(self):
        if self.main_window.element.findChild(QFrame, "left_side_content").width() > 30:
            self.hide_layers()

        self.add_layer_window.hide()
        self.main_window.elements["mainContent"].removeWidget(self.web)
        self.web.deleteLater()

        self.web = QWebEngineView()
        self.main_window.elements["mainContent"].addWidget(self.web, stretch=1)
        self.map_view = MapView(self.web, ui=self)
        self.update_layers_list()

