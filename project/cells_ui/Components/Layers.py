from Components.Exceptions import LayerCreatingException


class Layer:
    def __init__(self, name, layer_type):
        if layer_type not in ['raster', 'vector']:
            raise LayerCreatingException("undefined type of layer")
        self.name = name
        self.type = layer_type
        self.is_visible = True

    def to_save(self):
        return "%s|splitter|%s|splitter|%s" % (self.type, self.name, self.is_visible)


class RasterLayer(Layer):
    def __init__(self, name, data, bounds):
        super().__init__(name, "raster")
        self.data = data
        self.bounds = bounds

    def to_save(self):
        return "%s|splitter|%s|splitter|%s|splitter|%s|splitter|%s|splitter|%s|splitter|%s|splitter|%s" %\
               (self.type, self.name, self.is_visible, self.bounds[0][0], self.bounds[0][1],
                self.bounds[1][0], self.bounds[1][1], self.data)


class VectorLayer(Layer):
    def __init__(self, name, data):
        super().__init__(name, "vector")
        self.data = data

    def to_save(self):
        return "%s|splitter|%s|splitter|%s|splitter|%s" % (self.type, self.name, self.is_visible, self.data)
