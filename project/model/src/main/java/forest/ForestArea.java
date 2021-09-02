package forest;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.lang3.ArrayUtils;
import org.gdal.gdal.*;
import org.gdal.gdalconst.gdalconst;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import input.InputData;
import urban.UrbanCell;

public class ForestArea {
    int side;
    int length, width;
    ForestCell[][] cells;
    InputData inputData;

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
        this.length = length;
        this.width = width;

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
                x = start[1] + i * side;
                y = start[0] + j * side;
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
            }
        }
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
                printStatistics();
            }

            for (int i = 2; i < width - 2; i++) {
                for (int j = 2; j < length - 2; j++) {

                    switch (cells[i][j].getState()) {
                        case UNBURNED:
                            if (Arrays.stream(cells[i][j].neighbours)
                                    .anyMatch(x -> x.getState().equals(ForestStates.DEVELOPING))) {

                                newState = (cells[i - 1][j - 1].getSpreadRates()[3] +
                                            cells[i + 1][j - 1].getSpreadRates()[5] +
                                            cells[i - 1][j + 1].getSpreadRates()[1] +
                                            cells[i + 1][j + 1].getSpreadRates()[7]) * cells[i][j].getFirePeriod() /
                                           Math.sqrt(2) / side + cells[i][j].getState().getValue() +
                                           (cells[i][j - 1].getSpreadRates()[4] +
                                            cells[i - 1][j].getSpreadRates()[2] +
                                            cells[i + 1][j].getSpreadRates()[6] +
                                            cells[i][j + 1].getSpreadRates()[0]) * cells[i][j].getFirePeriod() / side;

                                if (newState >= 1)
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


    private void printStatistics() {
        int ignited = 0;
        int burned = 0;
        int developing = 0;
        int unb = 0;
        int ext = 0;

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                switch(cells[i][j].getState()){
                    case UNBURNED -> {
                        unb ++;
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
                if (cells[i][j].isIgnitedByUrban())
                {
                    cells[i][j].setState(ForestStates.IGNITED);
                    cells[i][j].makeIgnitedByUrbanDefault();
                    states[i][j] = ForestStates.IGNITED;
                }
                cells[i][j].setState(states[i][j]);
            }
        }
    }

    public void presentResult() {
        String path = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\result\\result_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif";

        var driver = gdal.GetDriverByName("GTiff");

        Dataset resultData = driver.Create(path,
                width, length,
                1, gdalconst.GDT_Int32);


        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var ct = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);
        var beginning = ct.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        double[] geotransform = {beginning[0], 30, 0.0, beginning[1], 0, -30};
        resultData.SetGeoTransform(geotransform);
        resultData.SetProjection(spatialReferenceUTM.ExportToPrettyWkt());

        Band result = resultData.GetRasterBand(1);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                result.WriteRaster(i, j, 1, 1,
                        new int[]{cells[i][j].getState().getValue()});
            }
        }

        resultData.delete();
    }

    public void presentFuel() {
        String path = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\result\\dem_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif";

        var driver = gdal.GetDriverByName("GTiff");

        Dataset resultData = driver.Create(path,
                width, length,
                1, gdalconst.GDT_Float64);


        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);
        var ct = new CoordinateTransformation(sourceSRS, spatialReferenceUTM);
        var beginning = ct.TransformPoint(inputData.getStartPoint().GetX(),
                inputData.getStartPoint().GetY());

        double[] geotransform = {beginning[0], 30, 0.0, beginning[1], 0, -30};
        resultData.SetGeoTransform(geotransform);
        resultData.SetProjection(spatialReferenceUTM.ExportToPrettyWkt());

        Band result = resultData.GetRasterBand(1);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                result.WriteRaster(i, j, 1, 1,
                        new double[]{cells[i][j].getHeight()});
            }
        }

        resultData.delete();
    }


    public void setFuel(String path, String fuelCodes) {
        Map<String, Double> fuelTypesTransition = Stream.of(new Object[][]{
                {"Tree", 0.75},
                {"Shrub", 0.9},
                {"Herb", 0.6},
                {"Agriculture", 0.75},
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

    public void initWeather(String weather) {
        FileReader file = null;
        try {
            file = new FileReader(weather);

            var csvReader = new CSVReader(file);
            String[] record;
            var writer = new CSVWriter(new FileWriter("..\\data\\weather\\weather.csv"));

            var date = inputData.getStart();
            date = LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), date.getHour(), 0);

            while ((record = csvReader.readNext()) != null) {
                setHourlyWeather(date, writer, record[1], record[2], record[3]);
                date = date.plusDays(1);
                date = LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), 0, 0);
            }

            csvReader.close();
            writer.close();
            file.close();

        }
        catch (CsvValidationException | IOException e) {
            e.printStackTrace();
        }

    }

    private void setHourlyWeather(LocalDateTime date, CSVWriter writer, String windPath, String tempPath, String humPath)
            throws IOException {

        var path = "..\\data\\weather\\";
        String[] content = new String[5];

        Dataset windData = gdal.Open(windPath);
        Dataset tempData = gdal.Open(tempPath);
        Dataset humData = gdal.Open(humPath);


        var paths = generatePaths(tempPath, "temp_" + date.toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd")) + ".tif");

        tempData = changeProjection(tempData, paths[0]);
        tempData = changeResolutionAndBorders(tempData, paths[1]);

        paths = generatePaths(tempPath, "hum_" + date.toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd")) + ".tif");

        humData = changeProjection(humData, paths[0]);
        humData = changeResolutionAndBorders(humData, paths[1]);

        paths = generatePaths(windPath, "wind_" + date.toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd")) + ".tif");

        windData = changeProjection(windData, paths[0]);
        windData = changeResolutionAndBorders(windData, paths[1]);

        var driver = gdal.GetDriverByName("GTiff");

        for (int i = 1; i <= tempData.GetRasterCount(); i++) {

            content[0] = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));

            Band horizontal = windData.GetRasterBand(2 * i - 1);
            Band vertical = windData.GetRasterBand(2 * i);

            Dataset wind = driver.Create(path
                                         + "wind_"
                                         + date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
                                         + "_vel.tif",
                    windData.GetRasterXSize(), windData.GetRasterYSize(),
                    1, gdalconst.GDT_Float64);

            wind.SetGeoTransform(windData.GetGeoTransform());
            wind.SetProjection(windData.GetProjection());

            content[1] = "wind_"
                         + date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
                         + "_vel.tif";
            Dataset wind_angle = driver.Create(path
                                               + "wind_" + date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
                                               + "_ang.tif",
                    windData.GetRasterXSize(), windData.GetRasterYSize(),
                    1, gdalconst.GDT_Float64);

            wind_angle.SetGeoTransform(windData.GetGeoTransform());
            wind_angle.SetProjection(windData.GetProjection());

            content[2] = "wind_" + date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
                         + "_ang.tif";


            Dataset currentTemp = driver.Create(path
                                                + "temp_" + date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
                                                + ".tif",

                    windData.GetRasterXSize(), windData.GetRasterYSize(),
                    1, gdalconst.GDT_Float64);

            currentTemp.SetGeoTransform(windData.GetGeoTransform());
            currentTemp.SetProjection(windData.GetProjection());

            content[3] = "temp_" + date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
                         + ".tif";


            Dataset currentHum = driver.Create(path
                                               + "hum_" + date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
                                               + ".tif",

                    windData.GetRasterXSize(), windData.GetRasterYSize(),
                    1, gdalconst.GDT_Float64);

            currentHum.SetGeoTransform(windData.GetGeoTransform());
            currentHum.SetProjection(windData.GetProjection());

            content[4] = "hum_" + date.format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
                         + ".tif";

            writer.writeNext(content);

            Band velocity = wind.GetRasterBand(1);
            Band angle = wind_angle.GetRasterBand(1);


            double[] proj_x = new double[1];
            double[] proj_y = new double[1];

            double[] val_velocity = new double[1];
            double[] val_angle = new double[1];

            Band temp = tempData.GetRasterBand(i);
            Band hum = humData.GetRasterBand(i);

            Band currenttemp = currentTemp.GetRasterBand(1);
            Band currenthum = currentHum.GetRasterBand(1);

            double[] val = new double[1];

            for (int k = 0; k < width; k++) {
                for (int j = 0; j < length; j++) {

                    horizontal.ReadRaster(k, j, 1, 1, proj_x);
                    vertical.ReadRaster(k, j, 1, 1, proj_y);

                    val_velocity[0] = Math.sqrt(proj_x[0] * proj_x[0] + proj_y[0] * proj_y[0]) * 36 / 10;
                    val_angle[0]    = (int) Math.round(180 - Math.toDegrees(Math.atan(proj_y[0] / proj_x[0]))
                                                       + (proj_x[0] / Math.abs(proj_x[0]))); // куда
                    val_angle[0]    = (180 + val_angle[0]) % 360;

                    velocity.WriteRaster(k, j, 1, 1, val_velocity);
                    angle.WriteRaster(k, j, 1, 1, val_angle);

                    temp.ReadRaster(k, j, 1, 1, val);
                    val[0] -= 273.15;
                    currenttemp.WriteRaster(k, j, 1, 1, val);

                    hum.ReadRaster(k, j, 1, 1, val);
                    currenthum.WriteRaster(k, j, 1, 1, val);

                }

            }

            date = date.plusMinutes(60);
        }
        tempData.delete();
        windData.delete();
        humData.delete();
    }

    public void setWeatherData(String temperaturePath, String humidityPath) {
        Dataset temperatureData = gdal.Open(temperaturePath);
        Dataset humidityData = gdal.Open(humidityPath);

        var paths = generatePaths(temperaturePath, "temp_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif");

        temperatureData = changeProjection(temperatureData, paths[0]);
        temperatureData = changeResolutionAndBorders(temperatureData, paths[1]);

        paths = generatePaths(temperaturePath, "hum_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif");

        humidityData = changeProjection(humidityData, paths[0]);
        humidityData = changeResolutionAndBorders(humidityData, paths[1]);

        Band temperature = temperatureData.GetRasterBand(1);
        Band humidity = humidityData.GetRasterBand(1);

        double[] temp = new double[1];
        double[] hum = new double[1];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                temperature.ReadRaster(i, j, 1, 1, temp);
                humidity.ReadRaster(i, j, 1, 1, hum);

                cells[i][j].changeDefaultSpreadRate(temp[0],
                        cells[i][j].getWindVelocity(), hum[0]);

            }
        }

        System.out.println(temp[0]);
        System.out.println(hum[0]);

        temperatureData.delete();
        humidityData.delete();
    }

    public void setWindData(String velocityPath, String anglePath) {
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


        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {

                angle.ReadRaster(i, j, 1, 1, ang);
                velocity.ReadRaster(i, j, 1, 1, vel);

                cells[i][j].setWindVelocity(vel[0]);
                cells[i][j].setWindDirection(ang[0]); //(ang[0] + 10)%360);
            }

        }

        System.out.println("V = " + vel[0]);
        System.out.println("Ang = " + ang[0]);
        velocityData.delete();
        angleData.delete();

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
                if (value[0] == noDataValue[0]) value[0] = j > 1 ? (int) cells[i][j - 1].getHeight() : mean;
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

    public void findUrbanNeighbours(List<UrbanCell> urbanCells) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                Geometry areaOfInterest = cells[i][j].calculateAreaOfInterest();
                for (int k = 0; k < urbanCells.size(); k++) {
                    var urbanGeom = Geometry.CreateFromWkt(urbanCells.get(k).getGeometry());
                    if (urbanGeom.Intersect(areaOfInterest))
                        cells[i][j].addUrbanNeighbour(urbanCells.get(k));
                }
            }
        }
    }

    public void propagateInUrban(Map<Long, Double> urbanIgnitionProbabilities) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                if (cells[i][j].getState().equals(ForestStates.DEVELOPING)){
                    cells[i][j].fireSpreadOnUrban(urbanIgnitionProbabilities);

                }
            }

        }
    }
}
