import os
import io
import base64
from osgeo import gdal
import json
from urllib.parse import urlparse, uses_netloc, uses_params, uses_relative
import shapefile
from json import dumps

_VALID_URLS = set(uses_relative + uses_netloc + uses_params)
_VALID_URLS.discard('')


def _is_url(url):
    try:
        return urlparse(url).scheme in _VALID_URLS
    except Exception:
        return False


def write_png(data, origin="upper", colormap=None):
    ds = gdal.Open(data)
    band = ds.GetRasterBand(1)

    root_ext = os.path.splitext(data)[0]
    output_path = os.path.basename(root_ext) + ".png"

    band.SetRasterColorInterpretation(gdal.GCI_GrayIndex)
    band.SetNoDataValue(0)
    stats = band.GetStatistics(True, True)

    gdal.Translate(output_path, ds, format="PNG",
                   scaleParams=[[min(stats), max(stats), [0, 255]]])
    del band
    del ds
    return output_path


def image_to_data(path, colormap=None, origin='upper'):
    if isinstance(path, str) and not _is_url(path):
        file_format = os.path.splitext(path)[-1][1:]

        if file_format == "tif":
            png_path = write_png(path, origin=origin, colormap=colormap)
            with io.open(png_path, 'rb') as f:
                img = f.read()
            b64encoded = base64.b64encode(img).decode('utf-8')
            url = 'data:image/png;base64,{}'.format(b64encoded)
        else:
            with io.open(path, 'rb') as f:
                img = f.read()
            b64encoded = base64.b64encode(img).decode('utf-8')
            url = 'data:image/{};base64,{}'.format(file_format, b64encoded)

        return url.replace('\n', ' ')
    else:
        url = json.loads(json.dumps(path))
        return url.replace('\n', ' ')

def shp_to_json(path):
    root_ext = os.path.splitext(path)[0]
    output_path = os.path.basename(root_ext) + ".geojson"

    reader = shapefile.Reader(path)
    fields = reader.fields[1:]
    field_names = [field[0] for field in fields]
    buffer = []
    for sr in reader.shapeRecords():
        atr = dict(zip(field_names, sr.record))
        geom = sr.shape.__geo_interface__
        buffer.append(dict(type="Feature",
                           geometry=geom, properties=atr))

    geojson = open(output_path, "w")
    try:
        geojson.write(dumps({"type": "FeatureCollection", "features": buffer}, indent=2, default=str) + "\n")
    except TypeError:
        print("Hi!")
    geojson.close()
    return output_path

def split_data_to_blocks(data, block_length):
    blocks = []
    tmp_block = ""
    for i in range(0, len(data)):
        tmp_block += data[i]
        if (i + 1) % block_length == 0:
            blocks.append(tmp_block)
            tmp_block = ""
        print(len(data) - i)
    if len(tmp_block) > 0:
        blocks.append(tmp_block)
    # while len(data) > 0:
    #    blocks.append(data[0:block_length])
    #    data = data[block_length:]
    #    print(len(data))
    return blocks
