package com.model.forest;

import com.model.urban.UrbanStates;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.lang3.ArrayUtils;
import org.gdal.gdal.*;
import org.gdal.gdalconst.gdalconst;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.model.input.InputData;
import com.model.urban.UrbanCell;

public class ForestArea {
    int side;
    int length, width;
    ForestCell[][] cells;
    InputData inputData;
    String ignitionRasterPath = "../data/ignition/ignition.tif";

    public void setLength(int length) {
        this.length = length;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setSpatialReferenceUTM(SpatialReference spatialReferenceUTM) {
        this.spatialReferenceUTM = spatialReferenceUTM;
    }

    LocalDateTime currentDate;

    ForestStates[][] states;

    private SpatialReference spatialReferenceUTM;

    public ForestArea(InputData inputData, SpatialReference spatialReferenceUTM, int length, int width) {

        this.side      = inputData.getSide();
        this.inputData = inputData;
        currentDate    = inputData.getStart();
        ForestCell.setSide(side);
        this.spatialReferenceUTM = spatialReferenceUTM;
        this.length              = length;
        this.width               = width;

        defineArea(inputData);
        defineNeighbours();

        gdal.AllRegister();
        setElevation(inputData.getElevation());
        setSlopes();
        setFuel(inputData.getFuel(), inputData.getFuelCodes());
        setIgnition(inputData.getIgnition());

    }

    private void setSpreadRates() {
        for (int i = 1; i < width - 1; i++) {
            for (int j = 1; j < length - 1; j++) {
                cells[i][j].initSpreadRates();
            }
        }
    }


    private void defineArea(InputData inputData) {
        cells = new ForestCell[width][length];

        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var transform = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);

        double[] start = transform.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        double x, y;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                x           = start[1] + i * side;
                y           = start[0] + j * side;
                cells[i][j] = new ForestCell(x, y);
            }
        }

        states = new ForestStates[width][length];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                states[i][j] = ForestStates.UNBURNED;
            }
        }
    }

    private void setIgnition(String ignition) {
        DataSource ign = ogr.Open(ignition);

        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var transform = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);

        double[] start = transform.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        System.out.println(ign.GetLayer(0).GetFeatureCount());
        for (int i = 0; i < ign.GetLayerCount(); i++) {
            for (int j = 0; j < ign.GetLayer(i).GetFeatureCount(); j++) {

                var geom = ign.GetLayer(i).GetFeature(j).GetGeometryRef();
                var source = geom.GetSpatialReference();
                var trans = new CoordinateTransformation(source, spatialReferenceUTM);
                double[] point;

                if (geom.GetGeometryType() == ogrConstants.wkbPoint) {
                    point = trans.TransformPoint(geom.GetX(), geom.GetY());
                    int y = (int) Math.round(Math.abs(point[1] - start[1]) / side),
                            x = (int) Math.round(Math.abs(point[0] - start[0]) / side);

                    cells[x][y].setState(ForestStates.DEVELOPING);
                    states[x][y] = ForestStates.DEVELOPING;
                }

                if (geom.GetGeometryType() == ogrConstants.wkbPolygon) {
                    rasterizeIgnition(ignition);
                    var dataset = gdal.Open(ignitionRasterPath);
                    var band = dataset.GetRasterBand(1);
                    int[] presence = new int[1];

                    for (int k = 0; i < width; i++) {
                        for (int l = 0; j < length; j++) {
                            band.ReadRaster(k, length - 1 - l, 1, 1, presence);
                            if (presence[0] > 0) {
                                states[k][l] = ForestStates.DEVELOPING;
                            }
                        }
                    }

                    band.delete();
                    dataset.delete();
                }
            }
        }
    }

    private void rasterizeIgnition(String ignition) {
        var ignitionData = ogr.Open(ignition);
        var ignitionLayer = ignitionData.GetLayer(0);

        SpatialReference sourceSrs = ignitionLayer.GetSpatialRef();
        double[] extent = ignitionLayer.GetExtent();

        double x_res = ((extent[1] - extent[0]) / side);
        double y_res = ((extent[3] - extent[2]) / side);

        int xCor = (int) x_res;
        int yCor = (int) y_res;

        Dataset target_ds = gdal.GetDriverByName("GTiff")
                .Create(ignitionRasterPath, xCor, yCor, 1, gdalconst.GDT_Byte);
        target_ds.SetProjection(sourceSrs.ExportToPrettyWkt());
        target_ds.SetGeoTransform(new double[]{extent[0], side, 0, extent[3], 0, -side});
        Band band = target_ds.GetRasterBand(1);


        int[] intArr = {1};

        // Rasterize
        gdal.RasterizeLayer(target_ds, intArr, ignitionLayer, null);

        ignitionLayer.delete();
        ignitionData.delete();
        target_ds.delete();
        band.delete();
    }

    private void defineNeighbours() {
        for (int i = 1; i < width - 1; i++) {
            for (int j = 1; j < length - 1; j++) {
                cells[i][j].setNeighbours(new ForestCell[]{cells[i - 1][j], cells[i - 1][j + 1],
                        cells[i][j + 1], cells[i + 1][j + 1],
                        cells[i + 1][j], cells[i + 1][j - 1], cells[i][j - 1], cells[i - 1][j - 1]});
            }
        }
    }

    public void propagate(double minutesLeft, double step, LocalDateTime localDateTime) {
        double newState = 0;
        currentDate = localDateTime;
        // поменять погоду
        if (minutesLeft == 0) {
            setSpreadRates();
        }

        for (int i = 2; i < width - 2; i++) {
            for (int j = 2; j < length - 2; j++) {

                switch (cells[i][j].getState()) {
                    case UNBURNED:
                        if (Arrays.stream(cells[i][j].neighbours)
                                .anyMatch(x -> x.getState().equals(ForestStates.DEVELOPING))) {

                            newState = cells[i][j].getCombustion() + (cells[i - 1][j - 1].getSpreadRates()[3] +
                                        cells[i + 1][j - 1].getSpreadRates()[5] +
                                        cells[i - 1][j + 1].getSpreadRates()[1] +
                                        cells[i + 1][j + 1].getSpreadRates()[7]) * cells[i][j].getFirePeriod() /
                                       Math.sqrt(2) / side + cells[i][j].getState().getValue() +
                                       (cells[i][j - 1].getSpreadRates()[4] +
                                        cells[i - 1][j].getSpreadRates()[2] +
                                        cells[i + 1][j].getSpreadRates()[6] +
                                        cells[i][j + 1].getSpreadRates()[0]) * cells[i][j].getFirePeriod() / side;

                            cells[i][j].setCombustion(cells[i][j].getCombustion() + newState);
                            if (cells[i][j].getCombustion() >= 1)
                                states[i][j] = ForestStates.IGNITED;
                        }

                        break;
                    case IGNITED:
                        if (cells[i][j].getInnerFireTime() == 0) {
                            var time = side / (Math.sqrt(Math.PI) * cells[i][j].calculateInternalSpreadRate());
                            cells[i][j].setInnerFireTime(time * 60);
                        } else {
                            cells[i][j].setInnerFireTime(Math.max(0.0, cells[i][j].getInnerFireTime() - step));
                            if (cells[i][j].getInnerFireTime() == 0)
                                states[i][j] = ForestStates.DEVELOPING;
                        }
                        break;
                    case DEVELOPING:
                        if (cells[i][j].getInnerFireTime() == 0) {
                            cells[i][j].setInnerFireTime(cells[i][j].getFirePeriod() * 60);
                        } else {
                            cells[i][j].setInnerFireTime(Math.max(0, cells[i][j].getInnerFireTime() - step));
                            if (cells[i][j].getInnerFireTime() == 0)
                                states[i][j] = ForestStates.EXTINGUISHING;
                        }
                        break;
                    case EXTINGUISHING:
                        if (cells[i][j].getInnerFireTime() == 0) {
                            cells[i][j].setInnerFireTime(cells[i][j].getFirePeriod() * 60);
                        } else {
                            cells[i][j].setInnerFireTime(Math.max(0, cells[i][j].getInnerFireTime() - step));
                            if (cells[i][j].getInnerFireTime() == 0)
                                states[i][j] = ForestStates.BURNED;
                        }
                        break;
                    case BURNED:
                        break;
                }
            }
        }
    }


    public void printStatistics() {
        int ignited = 0;
        int burned = 0;
        int developing = 0;
        int unb = 0;
        int ext = 0;

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                switch (cells[i][j].getState()) {
                    case UNBURNED -> {
                        unb++;
                    }
                    case IGNITED -> {
                        ignited++;
                    }
                    case DEVELOPING -> {
                        developing++;
                    }
                    case EXTINGUISHING -> {
                        ext++;
                    }
                    case BURNED -> {
                        burned++;
                    }
                }
            }
        }

        System.out.println("========" + currentDate.toString() + "========");
        System.out.println("IGNITED = " + ignited);
        System.out.println("BURNED = " + burned);
        System.out.println("DEVELOPING = " + developing);
        System.out.println("UNBURNED = " + unb);
        System.out.println("EXTINGUISHING = " + ext);
    }

    public void updateStates() {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                if (cells[i][j].isIgnitedByUrban()) {
                    cells[i][j].setState(ForestStates.IGNITED);
                    cells[i][j].makeIgnitedByUrbanDefault();
                    states[i][j] = ForestStates.IGNITED;
                }
                cells[i][j].setState(states[i][j]);
            }
        }
    }

    public void setFuel(String path, String fuelCodes) {
        Map<String, Double> fuelTypesTransition = Stream.of(new Object[][]{
                {"Tree", 0.6},
                {"Shrub", 0.6},
                {"Herb", 0.3},
                {"Agriculture", 1.3},
                {"Sparse", 0.1} // Barren, Water, Snow-Ice, NA -> 0
        }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));

        // read fuel Codes
        Map<Integer, Double> codes = readFuelCodes(fuelCodes, fuelTypesTransition);

        Dataset fuel = gdal.Open(path);

        var paths = generatePaths(path, "fuel.tif");

        fuel = changeProjection(fuel, paths[0]);
        Dataset modified = changeResolutionAndBorders(fuel, paths[1]);

        Band fuelTypes = modified.GetRasterBand(1);
        int[] value = new int[1];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                fuelTypes.ReadRaster(i, length - 1 - j, 1, 1, value);

                var val = codes.getOrDefault(value[0], 0.0) != null ?
                        codes.getOrDefault(value[0], 0.0) : 0.0;
                cells[i][j].setFuel(val);
            }
        }

        fuel.delete();
        modified.delete();
    }

    private Map<Integer, Double> readFuelCodes(String fuelCodes, Map<String, Double> fuelTypesTransition) {
        Map<Integer, Double> codes = new HashMap<>();
        try {
            var fileReader = new FileReader(fuelCodes);
            var csvReader = new CSVReader(fileReader);
            String[] record;

            // Заголовок.
            record = csvReader.readNext();
            int index = ArrayUtils.indexOf(record, "EVT_LF");

            while ((record = csvReader.readNext()) != null) {
                codes.put(Integer.valueOf(record[0]), fuelTypesTransition.get(record[index]));
            }

            csvReader.close();
            fileReader.close();
        }
        catch (CsvValidationException | IOException e) {
            e.printStackTrace();
        }
        return codes;
    }

    public void setWeatherData(String weatherDataPath) {
        var dataset = gdal.Open(weatherDataPath);
        Band velocity = dataset.GetRasterBand(1);
        Band angle = dataset.GetRasterBand(2);
        Band temperature = dataset.GetRasterBand(3);
        Band humidity = dataset.GetRasterBand(4);

        var temp = new double[1];
        var hum = new double[1];
        var vel = new double[1];
        var ang = new double[1];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                temperature.ReadRaster(i, j, 1, 1, temp);
                humidity.ReadRaster(i, j, 1, 1, hum);
                velocity.ReadRaster(i, j, 1, 1, vel);
                angle.ReadRaster(i, j, 1, 1, ang);

                cells[i][j].changeDefaultSpreadRate(temp[0],
                        vel[0], hum[0]);
                cells[i][j].setWindDirection(ang[0]);
                cells[i][j].setWindVelocity(vel[0]);

            }
        }

        temperature.delete();
        humidity.delete();
        velocity.delete();
        angle.delete();
        dataset.delete();
    }

    private String[] generatePaths(String path, String name) {
        var ind = path.lastIndexOf(File.separator);
        var projectedPath = path.substring(0, ind + 1) + "projected_" + name;
        var modifiedPath = path.substring(0, ind + 1) + "modified_" + name;
        return new String[]{projectedPath, modifiedPath};

    }

    public void setElevation(String path) {
        Dataset elevation = gdal.Open(path);

        var paths = generatePaths(path, "elevation.tif");

        elevation = changeProjection(elevation, paths[0]);
        Dataset modified = changeResolutionAndBorders(elevation, paths[1]);

        Band heights = modified.GetRasterBand(1);

        double[] v = new double[2];
        heights.ComputeRasterMinMax(v);
        int mean = (int) Math.round((v[0] + v[1]) / 2);

        Double[] noDataValue = new Double[1];
        heights.GetNoDataValue(noDataValue);

        int[] value = new int[1];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                heights.ReadRaster(i, length - 1 - j, 1, 1, value);
                if (value[0] == noDataValue[0]) value[0] = j > 1 ?
                        (int) cells[i][j - 1].getHeight() : mean;
                cells[i][j].setHeight(value[0]);

            }
        }
        elevation.delete();
        modified.delete();
    }

    private void setSlopes() {
        for (int i = 1; i < width - 1; i++) {
            for (int j = 1; j < length - 1; j++) {
                cells[i][j].initSlope();
            }
        }
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
                        "-tr", String.valueOf(side), String.valueOf(side)));

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


    public ForestCell[][] getCells() {
        return cells;
    }

    public void propagateInUrban(UrbanCell[][] urbanCells) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                if (cells[i][j].getState().equals(ForestStates.DEVELOPING)) {
                    cells[i][j].fireSpreadOnUrban(urbanCells, i, j, width, length);

                }
            }

        }
    }
}
