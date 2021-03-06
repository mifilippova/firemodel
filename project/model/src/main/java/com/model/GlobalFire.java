package com.model;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.model.forest.ForestArea;
import com.model.input.InputData;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.WarpOptions;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import com.model.urban.UrbanArea;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Vector;

public class GlobalFire {
    InputData inputData;
    ForestArea forest;
    UrbanArea urban;
    SpatialReference spatialReferenceUTM;
    int side;
    int length;

    public SpatialReference getSpatialReferenceUTM() {
        return spatialReferenceUTM;
    }

    public int getLength() {
        return length;
    }

    public int getWidth() {
        return width;
    }

    public LocalDateTime getCurrentDate() {
        return currentDate;
    }

    int width;
    LocalDateTime currentDate;

    public GlobalFire(InputData inputData) {
        this.inputData = inputData;
        this.side      = inputData.getSide();

        initSpatialReference();
        defineAreaSize();

        currentDate = inputData.getStart();
        startDate   = inputData.getStart();
        forest      = new ForestArea(inputData, spatialReferenceUTM, length, width);
        urban       = new UrbanArea(inputData, spatialReferenceUTM, length, width);
        setWeather(inputData.getMeteodata(), 0);

    }


    /**
     * Задает значения погодных условий для ячеек.
     * @param weather Путь к файлу с перечислением названий файлов
     *                данных для каждого временного этапа.
     * @param number
     */
    private void setWeather(String weather, int number) {
        FileReader file = null;
        try {
            file = new FileReader(weather);

            var csvReader = new CSVReader(file);
            String[] record;
            csvReader.skip(number);
            int ind = weather.lastIndexOf("/");
            if (ind == -1)
                ind = weather.lastIndexOf("\\");

            String dir = weather.substring(0, ind + 1);

            record = csvReader.readNext();

            String weatherPath = mergeWeatherData(dir, record);
            forest.setWeatherData(weatherPath);
            urban.setWeatherData(weatherPath);

            csvReader.close();
        }
        catch (CsvValidationException | IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Слияния метеорологических данных.
     * @param dir Путь к директории данных.
     * @param record Название файлов данных.
     * @return
     */
    private String mergeWeatherData(String dir, String[] record) {
        String output = dir + "weather" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif";
        var dataset = gdal.GetDriverByName("GTiff").Create(output,
                width, length, 4, gdalconst.GDT_Float64);
        dataset.SetProjection(spatialReferenceUTM.ExportToPrettyWkt());
        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var transform = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);

        double[] start = transform.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        dataset.SetGeoTransform(new double[]{start[0], side, 0, start[1], 0, -side});

        String projectedName = "wind_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + "_vel.tif";
        addBandToWeatherDataset(dir + record[1], dataset, projectedName, 1);

        projectedName = "wind_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + "_ang.tif";
        addBandToWeatherDataset(dir + record[2], dataset, projectedName, 2);

        projectedName = "temp_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif";
        addBandToWeatherDataset(dir + record[3], dataset, projectedName, 3);

        projectedName = "hum_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif";
        addBandToWeatherDataset(dir + record[4], dataset, projectedName, 4);


        dataset.delete();
        return output;
    }

    /**
     * Добавление слоя погодного параметра к общему файлу.
     *
     * @param name Название файла.
     * @param dataset Общий датасет.
     * @param projectedName Имя спроецированного файла.
     * @param bandNumber Номер слоя.
     */
    private void addBandToWeatherDataset(String name, Dataset dataset, String projectedName, int bandNumber) {
        Dataset originalDataset = gdal.Open(name);

        var paths = generatePaths(name, projectedName);

        originalDataset = changeProjection(originalDataset, paths[0]);
        originalDataset = changeResolutionAndBorders(originalDataset, paths[1]);

        Band original = originalDataset.GetRasterBand(1);
        Band band = dataset.GetRasterBand(bandNumber);

        var data = new double[width];
        for (int i = length - 1; i >= 0; i--) {
            original.ReadRaster(0, i, data.length, 1, data);
            band.WriteRaster(0, i, data.length, 1, data);
        }

        original.delete();
        originalDataset.delete();
    }

    // Изменение разрешения и границ файла данных.
    private Dataset changeResolutionAndBorders(Dataset dataset, String path) {
        // Изменить размер и разрешение
        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var targetSRS = dataset.GetSpatialRef();

        var ct = new CoordinateTransformation(sourceSRS, targetSRS);

        var beginning = ct.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());
        var finish = ct.TransformPoint(inputData.getEndPoint().GetX(),
                inputData.getEndPoint().GetY());

        Vector<String> options =
                new Vector<>(Arrays.asList("-te", String.valueOf(beginning[0]),
                        String.valueOf(beginning[1]),
                        String.valueOf(finish[0]), String.valueOf(finish[1]),
                        "-tr", String.valueOf(side), String.valueOf(side)));

        var warpOptions = new WarpOptions(options);
        Dataset[] srcData = {dataset};
        Dataset modified = gdal.Warp(path, srcData, warpOptions);
        return modified;
    }

    // Изменить проекцию данных.
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

    private String[] generatePaths(String path, String name) {
        var ind = path.lastIndexOf(File.separator);
        var projectedPath = path.substring(0, ind + 1) + "projected_" + name;
        var modifiedPath = path.substring(0, ind + 1) + "modified_" + name;
        return new String[]{projectedPath, modifiedPath};

    }

    LocalDateTime startDate;

    // Распространение пожара на глобальном уровне.
    public void propagate() {
        int step = 180;
        double minutesLeft = 0;
        System.out.println(currentDate);

        forest.printStatistics();
        urban.printUrbanStatistics();

        while (currentDate.compareTo(inputData.getFinish()) < 0) {
            forest.propagate(minutesLeft, step, currentDate);
            forest.propagateInUrban(urban.getUrbanCells());
            urban.propagate(step);
            urban.propagateInForest(forest.getCells());

            forest.updateStates();
            urban.updateStates();

            currentDate = currentDate.plusSeconds(step);
            minutesLeft = startDate.until(currentDate, ChronoUnit.MINUTES);
            if (startDate.until(currentDate, ChronoUnit.MINUTES)
                >= inputData.getWeatherPeriod()) {
                setWeather(inputData.getMeteodata(),
                        (int) Duration.between(inputData.getStart(), currentDate).toHours());

                forest.printStatistics();
                urban.printUrbanStatistics();
                presentResult();
                minutesLeft = startDate.until(currentDate, ChronoUnit.MINUTES) - inputData.getWeatherPeriod();
                startDate = currentDate;
            }
        }
        presentResult();

    }

    private void presentResult() {
        presentForestResults();
    }

    // Представление результатов
    private void presentForestResults() {
        String path = "../data/result/result_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif";

        Dataset resultData = gdal.GetDriverByName("GTiff").Create(path,
                width, length,
                1, gdalconst.GDT_Byte);


        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var ct = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);
        var beginning = ct.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        double[] geotransform = {beginning[0], 30, 0.0, beginning[1], 0, -30};
        resultData.SetGeoTransform(geotransform);
        resultData.SetProjection(spatialReferenceUTM.ExportToPrettyWkt());

        Band result = resultData.GetRasterBand(1);

        byte value = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                result.WriteRaster(i, j, 1, 1,
                        new byte[]{(byte) forest.getCells()[i][j].getState().getValue()});
                if (urban.getUrbanCells()[i][j] != null) {
                    value = (byte) urban.getUrbanCells()[i][j].getState().getValue();
                    if (value > 0)
                        result.WriteRaster(i, j, 1, 1, new byte[]{value});
                }

            }
        }

        result.delete();
        resultData.delete();
    }

    private void initSpatialReference() {
        this.spatialReferenceUTM = new SpatialReference();
        int zone = (int) Math.round(30 + inputData.getStartPoint().GetY() / 6);
        spatialReferenceUTM.SetProjCS(String.format("UTM %d (WGS84)", zone));
        spatialReferenceUTM.SetWellKnownGeogCS("WGS84");
        spatialReferenceUTM.SetUTM(zone);

    }

    private void defineAreaSize() {
        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);

        var ct = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);
        var beginning = ct.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());
        var finish = ct.TransformPoint(inputData.getEndPoint().GetX(),
                inputData.getEndPoint().GetY());


        width  = (int) Math.round(Math.abs(beginning[0] - finish[0]) / side);
        length = (int) Math.round(Math.abs(beginning[1] - finish[1]) / side);
    }
}
