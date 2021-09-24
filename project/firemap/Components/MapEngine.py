DEFAULT_HTML = """
    <!DOCTYPE html>
<head>    
    <meta http-equiv="content-type" content="text/html; charset=UTF-8" />

        <script>
            L_NO_TOUCH = false;
            L_DISABLE_3D = false;
        </script>

    <script src="https://cdn.jsdelivr.net/npm/leaflet@1.5.1/dist/leaflet.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.4.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/Leaflet.awesome-markers/2.0.2/leaflet.awesome-markers.js"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/leaflet@1.5.1/dist/leaflet.css"/>
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css"/>
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap-theme.min.css"/>
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.6.3/css/font-awesome.min.css"/>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/Leaflet.awesome-markers/2.0.2/leaflet.awesome-markers.css"/>
    <link rel="stylesheet" href="https://rawcdn.githack.com/python-visualization/folium/master/folium/templates/leaflet.awesome.rotate.css"/>
    <style>html, body {width: 100%;height: 100%;margin: 0;padding: 0;}</style>
    <style>#map {position:absolute;top:0;bottom:0;right:0;left:0;}</style>

            <meta name="viewport" content="width=device-width,
                initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                #main-map {
                    position: relative;
                    width: 100.0%;
                    height: 100.0%;
                    left: 0.0%;
                    top: 0.0%;
                }
            </style>

</head>
<body>    

            <div class="folium-map" id="main-map"></div>

</body>
<script>
    var layers = {};
</script>
"""

MAP_CREATION_SCRIPT = """
    var mainMap = L.map("main-map",
        {
            center: [34.11, -118.5],
            crs: L.CRS.EPSG3857,
            zoom: 10,
            zoomControl: true,
            preferCanvas: false,
        }
    );
"""

OSM_TILE_CREATION_SCRIPT = """
    var mapTileLayer = L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        {
            "attribution": "Data by &copy; <a href='http://openstreetmap.org'>OpenStreetMap</a>, under <a href='http://www.openstreetmap.org/copyright'>ODbL</a>.",
            "detectRetina": false,
            "maxNativeZoom": 18,
            "maxZoom": 18,
            "minZoom": 0,
            "noWrap": false,
            "opacity": 1,
            "subdomains": "abc",
            "tms": false
        }
    );
"""

ADD_TILE_TO_MAP_SCRIPT = """
    mapTileLayer.addTo(mainMap);
"""
GEOJSON_LAYER_CREATION_SCRIPT = """
    layers["%s"] = L.geoJson(null, { onEachFeature: (feature, layer) => { layer.on({ click: function(e) { mainMap.fitBounds(e.target.getBounds()); }}); }});
    layers["%s"].addTo(mainMap);
"""

GEOJSON_LAYER_ADD_DATA_SCRIPT = """
    layers["%s"].addData(%s);
"""

RASTER_LAYER_CREATION_SCRIPT = """
    layers["%s"] = L.imageOverlay(createLayerData, %s);
    layers["%s"].addTo(mainMap);
"""

REMOVE_LAYER_SCRIPT = """
    mainMap.removeLayer(layers["%s"]);
    delete layers["%s"];
"""

SHOW_LAYER_SCRIPT = """
    if (!mainMap.hasLayer(layers["%s"])) {
        layers["%s"].addTo(mainMap);
    }
"""

HIDE_LAYER_SCRIPT = """
    if (mainMap.hasLayer(layers["%s"])) {
        mainMap.removeLayer(layers["%s"]);
    }
"""

