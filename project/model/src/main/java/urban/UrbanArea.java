package urban;

import com.google.protobuf.GeneratedMessage;
import forest.ForestCell;
import input.InputData;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.WarpOptions;
import org.gdal.gdal.gdal;
import org.gdal.ogr.*;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class UrbanArea {
    int width, length;
    InputData inputData;
    SpatialReference spatialReferenceUTM;
    String area = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\buildings\\urban_area.shp";
    List<UrbanCell> urbanCells;
    Map<Long, UrbanStates> states = new HashMap<>();
    Random random = new Random();

    public List<UrbanCell> getUrbanCells() {
        return urbanCells;
    }

    public UrbanArea(InputData inputData, SpatialReference spatialReferenceUTM, int length, int width) {
        this.width               = width;
        this.length              = length;
        this.inputData           = inputData;
        this.spatialReferenceUTM = spatialReferenceUTM;
        urbanCells               = new ArrayList<>();

        initUrbanCells(inputData, spatialReferenceUTM);

        /*var urbanData = ogr.Open(area);
        var urbanLayer = urbanData.GetLayer(0);

        SpatialReference sourceSrs = urbanLayer.GetSpatialRef();
        double[] extent = urbanLayer.GetExtent();

        //# Create the destination data source
        double x_res = ((extent[1] - extent[0]) / 30);
        double y_res = ((extent[3] - extent[2]) / 30);
        System.out.println("x_res -------" + x_res +  "y " + y_res );
        int xCor = (int)x_res;
        int yCor = (int)y_res;

        System.out.println(Arrays.toString(extent));
        System.out.println("xCor -------" + xCor);
        System.out.println("yCor -------" + yCor);

        int NoData_value = 255;

        String output = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\myAttrOp2.tif";
        org.gdal.gdal.Dataset target_ds = gdal.GetDriverByName("GTiff")
                .Create(output, xCor, yCor, 1, gdalconst.GDT_Byte);
        target_ds.SetProjection(sourceSrs.ExportToPrettyWkt());
        target_ds.SetGeoTransform(new double[]{extent[0], 30, 0, extent[3], 0, -30});
        Band band = target_ds.GetRasterBand(1);
        //band.SetNoDataValue(NoData_value);
        //band.FlushCache();

        int[] intArr = {1};

        // Rasterize
        gdal.RasterizeLayer(target_ds, intArr, urbanLayer, null);
*/

    }

    public void initIgnition(String path) {

    }


    public void propagate(double step, Map<Long, Double> urbanIgnitionProbabilities) {
        for (int i = 0; i < urbanCells.size(); i++) {
            switch (urbanCells.get(i).getState()) {
                case IGNITED -> urbanCells.get(i).developIgnition(step, states, random);
                case SLOWDEVELOPING, FULLDEVELOPMENT -> urbanCells.get(i)
                        .fireSpreadOnUrban(step, urbanIgnitionProbabilities,
                        states, random);
                case FLASHOVER -> urbanCells.get(i).developFlashover(step, states, random);
            }
        }
    }


    private void initUrbanCells(InputData inputData, SpatialReference spatialReferenceUTM) {
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

       /* var driver = gdal.GetDriverByName("ESRI Shapefile");
        var dataset = driver.Create(area, 0, 0,
                1, gdalconst.GDT_Unknown, (String[]) null);
        var dataLayer = dataset.CreateLayer("houses",
                spatialReferenceUTM, ogrConstants.wkbPolygon);

        var material = new FieldDefn("material", ogrConstants.OFSTFloat32);
        var weather = new FieldDefn("weather", ogrConstants.OFSTFloat32);
        var velocity = new FieldDefn("velocity", ogrConstants.OFSTFloat32);
        var angle = new FieldDefn("angle", ogrConstants.OFSTInt16);
        var state = new FieldDefn("state", ogrConstants.OFSTInt16);

        dataLayer.CreateField(material);
        dataLayer.CreateField(weather);
        dataLayer.CreateField(velocity);
        dataLayer.CreateField(angle);
        dataLayer.CreateField(state);
        */
        Feature f;
        while ((f = layer.GetNextFeature()) != null) {
            for (int i = 0; i < f.GetFieldCount(); i++) {
                if ("house".equals(f.GetFieldAsString("building"))) {
                    /*var feature = new Feature(dataLayer.GetLayerDefn());

                    var geom = f.GetGeometryRef();
                    geom.TransformTo(spatialReferenceUTM);

                    feature.SetGeometry(f.GetGeometryRef());
                    feature.SetField(material.GetName(), inputData.getHouseMaterial());
                    feature.SetField(state.GetName(),
                            UrbanStates.UNBURNED.getValue());
                    dataLayer.CreateFeature(feature);
                    feature.delete();*/
                    urbanCells.add(new UrbanCell(f.GetFID(), f.GetGeometryRef().ExportToWkt()));
                    states.put(f.GetFID(), UrbanStates.UNBURNED);
                }
            }
        }


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

    public void setWindData(String velocityPath, String anglePath, LocalDateTime currentDate) {
        Dataset velocityData = gdal.Open(velocityPath);

        var paths = generatePaths(velocityPath, "wind_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + "_vel.tif");

        velocityData = changeProjection(velocityData, paths[0]);
        velocityData = changeResolutionAndBorders(velocityData, paths[1]);

        Dataset angleData = gdal.Open(anglePath);
        paths = generatePaths(velocityPath, "wind_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + "_ang.tif");

        angleData = changeProjection(angleData, paths[0]);
        angleData = changeResolutionAndBorders(angleData, paths[1]);

        Band velocity = velocityData.GetRasterBand(1);
        Band angle = angleData.GetRasterBand(1);

        double[] vel = new double[1];
        double[] ang = new double[1];

        int i, j;
        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var transform = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);

        double[] start = transform.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        for (int k = 0; k < urbanCells.size(); k++){
            var center = Geometry.CreateFromWkt(urbanCells.get(k).getGeometry()).Centroid();
           // System.out.println(center.GetX() + " " + center.GetY());
            double[] point = transform.TransformPoint(center.GetY(), center.GetX());
            j = Math.min((int) Math.round(Math.abs(point[1] - start[1]) / inputData.getSide()), 143);
            i = Math.min((int) Math.round(Math.abs(point[0] - start[0]) / inputData.getSide()), 98);

            velocity.ReadRaster(i, j, 1, 1, vel);
            angle.ReadRaster(i, j, 1, 1, ang);
            urbanCells.get(k).setWindVelocity(vel[0]);
            urbanCells.get(k).setWindAngle(ang[0]);
        }

        velocityData.delete();
        angleData.delete();
    }

    public void setWeather(String humidityPath, LocalDateTime currentDate) {

        Dataset humidityData = gdal.Open(humidityPath);
        var paths = generatePaths(humidityPath, "hum_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif");

        humidityData = changeProjection(humidityData, paths[0]);
        humidityData = changeResolutionAndBorders(humidityData, paths[1]);

        Band humidity = humidityData.GetRasterBand(1);
        double[] hum = new double[1];

        int i, j;
        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var transform = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);
        double h;

        double[] start = transform.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());
        for (int k = 0;  k < urbanCells.size() ; k++) {
            var center = Geometry.CreateFromWkt(urbanCells.get(k).getGeometry()).Centroid();
            double[] point = transform.TransformPoint(center.GetY(), center.GetX());

            j = Math.min((int) Math.round(Math.abs(point[1] - start[1]) / inputData.getSide()), 143);
            i = Math.min((int) Math.round(Math.abs(point[0] - start[0]) / inputData.getSide()), 98);

            humidity.ReadRaster(i, j, 1, 1, hum);
            if (hum[0] < 30) {
                h = 1.0;
            } else if (hum[0] < 60) {
                h = 0.8;
            } else h = 0.4;
            urbanCells.get(k).setWeather(h);
        }
        humidityData.delete();
    }

    public void findForestNeighbours(ForestCell[][] cells) {
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

    public void propagateInForest() {
        for (int i = 0; i < urbanCells.size(); i++) {
            if (urbanCells.get(i).getState().equals(UrbanStates.SLOWDEVELOPING) ||
                urbanCells.get(i).getState().equals(UrbanStates.FULLDEVELOPMENT))
                urbanCells.get(i).fireSpreadOnForest();
        }
    }

    public void updateStates(Map<Long, Double> urbanIgnitionProbabilities) {
        for (int i = 0; i < urbanCells.size(); i++) {
            if (urbanIgnitionProbabilities.containsKey(urbanCells.get(i).getId())){
                if (random.nextDouble() <= 1 - urbanIgnitionProbabilities.get(urbanCells.get(i).getId()))
                    urbanCells.get(i).setState(UrbanStates.IGNITED);
            }
            else{
                if (states.containsKey(urbanCells.get(i).getId()))
                    urbanCells.get(i).setState(states.get(urbanCells.get(i).getId()));
            }
        }
    }
}
