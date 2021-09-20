import PyQt5.QtWidgets

import sys

from UI.UIWindows import UIWindows


class Application():
    def __init__(self, args):
        args.append("--disable-web-security")
        self.app = PyQt5.QtWidgets.QApplication(args)
        self.window = UIWindows()

    def run(self):
        sys.exit(self.app.exec_())
