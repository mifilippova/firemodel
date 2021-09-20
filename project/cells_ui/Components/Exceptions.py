class LayerAddingException(Exception):
    def __init__(self, message):
        super().__init__(message)
        self.message = message


class LayerCreatingException(Exception):
    def __init__(self, message):
        super().__init__(message)
        self.message = message


class MapCreatingException(Exception):
    def __init__(self, message):
        super().__init__(message)
        self.message = message


class FileOpeningException(Exception):
    def __init__(self, message):
        super().__init__(message)
        self.message = message


class NotVectorLayerException(Exception):
    def __init__(self, message):
        super().__init__(message)
        self.message = message


class LayerNotFoundException(Exception):
    def __init__(self, message):
        super().__init__(message)
        self.message = message
