package urban;

import forest.ForestCell;
import input.InputData;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.WarpOptions;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.ogr.*;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class UrbanArea {
    int width, length;
    int side;
    InputData inputData;
    SpatialReference spatialReferenceUTM;
    String areaVectorPath = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\buildings\\urban_area.shp";
    String areaRasterPath = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\buildings\\buildings.tif";
    UrbanCell[][] urbanCells;
    UrbanStates[][] states;
    Random random = new Random();

    public UrbanCell[][] getUrbanCells() {
        return urbanCells;
    }

  /*  public List<UrbanCell> getUrbanCells() {
        return urbanCells;
    }*/

    public UrbanArea(InputData inputData, SpatialReference spatialReferenceUTM, int length, int width) {
        this.width               = width;
        this.length              = length;
        this.inputData           = inputData;
        this.spatialReferenceUTM = spatialReferenceUTM;
        urbanCells               = new UrbanCell[width][length];
        states                   = new UrbanStates[width][length];
        this.side                = inputData.getSide();
        UrbanCell.material       = inputData.getHouseMaterial();

        //extractBuildings(inputData, spatialReferenceUTM);
        rasterizeBuildingMap();
        initUrbanCells();

    }

    private void initUrbanCells() {
        var dataset = gdal.Open(areaRasterPath);
        var paths = generatePaths(areaRasterPath, "urban.tif");
        dataset = changeProjection(dataset, paths[0]);
        dataset = changeResolutionAndBorders(dataset, paths[1]);

        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var transform = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);

        double[] start = transform.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        double x, y;

        var band = dataset.GetRasterBand(1);
        int[] presence = new int[1];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                band.ReadRaster(i, length - 1 - j, 1, 1, presence);
                x = start[1] + i * side;
                y = start[0] + j * side;

                if (presence[0] > 0) {
                    urbanCells[i][j] = new UrbanCell(x, y, side);
                    states[i][j]     = UrbanStates.UNBURNED;
                }

            }
        }

        band.delete();
        dataset.delete();
    }

    private void rasterizeBuildingMap() {
        var urbanData = ogr.Open(areaVectorPath);
        var urbanLayer = urbanData.GetLayer(0);

        SpatialReference sourceSrs = urbanLayer.GetSpatialRef();
        double[] extent = urbanLayer.GetExtent();

        double x_res = ((extent[1] - extent[0]) / side);
        double y_res = ((extent[3] - extent[2]) / side);

        int xCor = (int) x_res;
        int yCor = (int) y_res;

        Dataset target_ds = gdal.GetDriverByName("GTiff")
                .Create(areaRasterPath, xCor, yCor, 1, gdalconst.GDT_Byte);
        target_ds.SetProjection(sourceSrs.ExportToPrettyWkt());
        target_ds.SetGeoTransform(new double[]{extent[0], side, 0, extent[3], 0, -side});
        Band band = target_ds.GetRasterBand(1);


        int[] intArr = {1};

        // Rasterize
        gdal.RasterizeLayer(target_ds, intArr, urbanLayer, null);

        urbanLayer.delete();
        urbanData.delete();
        target_ds.delete();
        band.delete();
    }

   /* public void initIgnition(String path) {

    }*/


    public void propagate(double step) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                if (urbanCells[i][j] == null)
                    continue;

                switch (urbanCells[i][j].getState()) {
                    case IGNITED -> {
                        urbanCells[i][j].developIgnition(step, states, random, i, j);
                    }
                    case SLOWDEVELOPING, FULLDEVELOPMENT -> {
                        double a = 3 * urbanCells[i][j].getWindVelocity() / 5 + 3 + side / 2.0;
                        double b = -2 * urbanCells[i][j].getWindVelocity() / 15 + 3 + side / 2.0;
                        double c = -1 * urbanCells[i][j].getWindVelocity() / 15 + 3 + side / 2.0;
                        var t = Math.sqrt(b * (a + c) / 2.0);
                        var geom = Geometry.CreateFromWkt(urbanCells[i][j].getGeometry()).Centroid();
                        double x = geom.GetX(), y = geom.GetY();
                        var influence = new Geometry(ogr.wkbLinearRing);
                        double[] f = rotatedCoords(x - t, y + c, x, y, urbanCells[i][j].getWindAngle());
                        influence.AddPoint(f[0], f[1]);
                        f = rotatedCoords(x + t, y + c, x, y, urbanCells[i][j].getWindAngle());
                        influence.AddPoint(f[0], f[1]);
                        f = rotatedCoords(x + t, y - a, x, y, urbanCells[i][j].getWindAngle());
                        influence.AddPoint(f[0], f[1]);
                        f = rotatedCoords(x - t, y - a, x, y, urbanCells[i][j].getWindAngle());
                        influence.AddPoint(f[0], f[1]);
                        f = rotatedCoords(x - t, y + c, x, y, urbanCells[i][j].getWindAngle());
                        influence.AddPoint(f[0], f[1]);

                        var influenceArea = new Geometry(ogr.wkbPolygon);
                        influenceArea.AddGeometry(influence);


                        int mini = (int) Math.max(0, i - a / side);
                        int minj = (int)Math.max(0, j - a / side);
                        int maxi = (int) Math.min(width, i + a / side);
                        int maxj = (int) Math.min(length, j + a / side);


                        double ign;
                        for (int l = mini; l < maxi; l++) {
                            for (int m = minj; m < maxj; m++) {
                                if (urbanCells[l][m] != null
                                    && urbanCells[l][m].getState().equals(UrbanStates.UNBURNED)){
                                    var urbanGeom = Geometry.CreateFromWkt(urbanCells[l][m].getGeometry());
                                    if (urbanGeom.Intersection(influenceArea) != null){
                                        ign = urbanCells[l][m].getMaterial() * urbanCells[l][m].getWeather()
                                              * urbanGeom.Intersection(influenceArea).Area() / urbanGeom.Area();

                                        if (urbanCells[i][j].getState().equals(UrbanStates.SLOWDEVELOPING))
                                            ign *= 0.4;
                                        urbanCells[l][m].addIgnitionProbability(1 - ign);
                                    }
                                }
                            }
                        }

                        urbanCells[i][j]
                                .fireSpreadOnUrban(step, states, random, i, j);
                    }

                    case FLASHOVER -> urbanCells[i][j].developFlashover(step, states, random, i, j);
                    case UNBURNED, EXTINGUISHED -> {
                    }
                }
            }
        }
    }

    private double[] rotatedCoords(double pointX, double pointY,
                                   double originX, double originY, double angle) {
        var x = Math.cos(Math.toRadians(angle)) * (pointX - originX)
                + Math.sin(Math.toRadians(angle)) * (pointY - originY) + originX;
        var y = -Math.sin(Math.toRadians(angle)) * (pointX - originX)
                + Math.cos(Math.toRadians(angle)) * (pointY - originY) + originY;
        return new double[]{x, y};

    }



    private void extractBuildings(InputData inputData, SpatialReference spatialReferenceUTM) {
        gdal.AllRegister();
        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var transform = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);
        double[] start = transform.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        double[] point;
        var data = ogr.Open(inputData.getBuildingsPath());
        var layer = data.GetLayerByName("multipolygons");
        var source = layer.GetSpatialRef();

        var trans = new CoordinateTransformation(source, spatialReferenceUTM);

        var driver = gdal.GetDriverByName("ESRI Shapefile");
        var dataset = driver.Create(areaVectorPath, 0, 0,
                1, gdalconst.GDT_Unknown, (String[]) null);
        var dataLayer = dataset.CreateLayer("houses",
                spatialReferenceUTM, ogrConstants.wkbPolygon);

        var id = new FieldDefn("id", ogr.OFTInteger);
        dataLayer.CreateField(id);

        Feature f;
        while ((f = layer.GetNextFeature()) != null) {
            for (int i = 0; i < f.GetFieldCount(); i++) {
                if ("house".equals(f.GetFieldAsString("building"))) {
                    var feature = new Feature(dataLayer.GetLayerDefn());

                    var geom = f.GetGeometryRef();
                    geom.TransformTo(spatialReferenceUTM);

                    feature.SetGeometry(f.GetGeometryRef());
                    feature.SetField("id", f.GetFID());

                    dataLayer.CreateFeature(feature);
                    feature.delete();
                }
            }
        }

        layer.delete();
        data.delete();
        dataset.delete();
        dataLayer.delete();

    }


    private String[] generatePaths(String path, String name) {
        var ind = path.lastIndexOf(File.separator);
        var projectedPath = path.substring(0, ind + 1) + "projected_" + name;
        var modifiedPath = path.substring(0, ind + 1) + "modified_" + name;
        return new String[]{projectedPath, modifiedPath};

    }

    private Dataset changeResolutionAndBorders(Dataset dataset, String path) {
        // Изменить размер и разрешение
        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var targetSRS = dataset.GetSpatialRef();

        var ct = new CoordinateTransformation(sourceSRS, targetSRS);

        var beginning = ct.TransformPoint(inputData.getStartPoint().GetX(), inputData.getStartPoint().GetY());
        var finish = ct.TransformPoint(inputData.getEndPoint().GetX(), inputData.getEndPoint().GetY());

        Vector<String> options =
                new Vector<>(Arrays.asList("-te", String.valueOf(beginning[0]), String.valueOf(beginning[1]),
                        String.valueOf(finish[0]), String.valueOf(finish[1]),
                        "-tr", String.valueOf(inputData.getSide()), String.valueOf(inputData.getSide())));

        var warpOptions = new WarpOptions(options);
        Dataset[] srcData = {dataset};
        Dataset modified = gdal.Warp(path, srcData, warpOptions);
        return modified;
    }

    private Dataset changeProjection(Dataset dataset, String path) {
        Vector<String> options = new Vector<>();
        options.add("-t_srs");
        options.add(spatialReferenceUTM.ExportToPrettyWkt());
        WarpOptions warpOptions = new WarpOptions(options);
        Dataset[] srcData = {dataset};
        Dataset projected = gdal.Warp(path, srcData, warpOptions);
        dataset = gdal.Open(path);

        return dataset;
    }

    /*  public void findForestNeighbours(ForestCell[][] cells) {
          for (int i = 0; i < urbanCells.size(); i++) {
              var urbanGeometry = Geometry
                      .CreateFromWkt(urbanCells.get(i).getGeometry());

              var a = 3 * urbanCells.get(i).getWindVelocity() + 3
                      + Math.sqrt(urbanGeometry.Area()) / 2;

              // index -- urbanGeometry centroid
              var sourceSRS = new SpatialReference();
              sourceSRS.ImportFromEPSG(4326);
              var transform = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);
              double[] start = transform.TransformPoint(inputData.getStartPoint().GetX(),
                      inputData.getStartPoint().GetY());

              double[] point = {urbanGeometry.Centroid().GetX(),
                      urbanGeometry.Centroid().GetY()};

              int y = (int) Math.round(Math.abs(point[1] - start[1]) / inputData.getSide()),
                      x = (int) Math.round(Math.abs(point[0] - start[0]) / inputData.getSide());

              // i, j +- a/side -- forest cells in each direction,
              for (int j = x - (int) a; j < x + a; j++) {
                  for (int k = y - (int) a; k < y + a; k++) {
                      urbanCells.get(i).addInfluenceOnForest(cells[j][k]);
                  }
              }
          }
      }

      public void findUrbanNeighbours() {
          for (int i = 0; i < urbanCells.size(); i++) {
              Geometry areaOfInterest = urbanCells.get(i).calculateAreaOfInterest();
              for (int k = 0; k < urbanCells.size(); k++) {
                  var urbanGeom = Geometry.CreateFromWkt(urbanCells.get(k).getGeometry());
                  if (urbanGeom.Intersect(areaOfInterest))
                      urbanCells.get(i).addUrbanNeighbour(urbanCells.get(k));
              }
          }

      }
  */
    public void propagateInForest(ForestCell[][] cells) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                if (urbanCells[i][j] == null)
                    continue;

                if (urbanCells[i][j].getState().equals(UrbanStates.SLOWDEVELOPING) ||
                    urbanCells[i][j].getState().equals(UrbanStates.FULLDEVELOPMENT))
                    urbanCells[i][j].fireSpreadOnForest(cells, i, j, width, length);
            }
        }

    }

    public void updateStates() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                if (urbanCells[i][j] != null) {
                    urbanCells[i][j].setState(states[i][j]);

                    if (urbanCells[i][j].getState().equals(UrbanStates.UNBURNED)) {
                        if (urbanCells[i][j].getIgnitionProbability() > 0) {

                            if (random.nextDouble() <= (1 - urbanCells[i][j].getIgnitionProbability())) {
                                urbanCells[i][j].setState(UrbanStates.IGNITED);
                                System.out.println("Ignited!");
                                states[i][j] = UrbanStates.IGNITED;
                            }
                            urbanCells[i][j].setIgnitionProbability(1.0);
                        }
                    }
                }
            }
        }

    }

    public void printUrbanStatistics() {
        int ignited = 0, unburned = 0, sldevelop = 0, fulldevelop = 0,
                exting = 0, flash = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                if (states[i][j] == null)
                    continue;
                switch(states[i][j]){
                    case UNBURNED -> {
                        unburned++;
                    }
                    case IGNITED -> {
                        ignited++;
                    }
                    case SLOWDEVELOPING -> {
                        sldevelop++;
                    }
                    case FULLDEVELOPMENT -> {
                        fulldevelop++;
                    }
                    case FLASHOVER -> {
                        flash++;
                    }
                    case EXTINGUISHED -> {
                        exting++;
                    }
                }
            }
        }

        System.out.println("++++URBAN CELLS+++++");
        System.out.println("UNBURNED = " + unburned);
        System.out.println("IGNITED = " + ignited);
        System.out.println("SLOW DEVELOP = " + sldevelop);
        System.out.println("FULL DEVELOP = " + fulldevelop);
        System.out.println("FLASHOVER = " + flash);
        System.out.println("EXTINGUISHED = " + exting);
    }

    public void setWeatherData(String weatherDataPath) {
        var dataset = gdal.Open(weatherDataPath);
        Band velocity = dataset.GetRasterBand(1);
        Band angle = dataset.GetRasterBand(2);
        Band humidity = dataset.GetRasterBand(4);

        double h;
        var hum = new double[1];
        var vel = new double[1];
        var ang = new double[1];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                if (urbanCells[i][j] != null) {
                    humidity.ReadRaster(i, j, 1, 1, hum);
                    velocity.ReadRaster(i, j, 1, 1, vel);
                    angle.ReadRaster(i, j, 1, 1, ang);

                    urbanCells[i][j].setWindAngle(ang[0]);
                    urbanCells[i][j].setWindVelocity(vel[0]);

                    if (hum[0] < 30) {
                        h = 1.0;
                    } else if (hum[0] < 60) {
                        h = 0.8;
                    } else h = 0.4;

                    if (urbanCells[i][j] != null)
                        urbanCells[i][j].setWeather(h);

                }
            }
        }

        humidity.delete();
        velocity.delete();
        angle.delete();
        dataset.delete();
    }
}
