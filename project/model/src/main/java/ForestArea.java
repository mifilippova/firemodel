import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.lang3.ArrayUtils;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.WarpOptions;
import org.gdal.gdal.gdal;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.SpatialReference;

import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ForestArea {
    int side;
    int length, width;
    ForestCell[][] cells;
    InputData inputData;
    LocalDateTime currentDate;


    double step;
    double maxSpreadRate;

    public ForestArea(InputData inputData) {
        this.side      = inputData.side;
        this.inputData = inputData;

        currentDate = inputData.getStart();
        gdal.AllRegister();
        setElevation(inputData.getElevation());
        setFuel(inputData.getFuel(), inputData.getFuelCodes());
        setWeather(inputData.getMeteodata(), 0); // Разница в днях
        setIgnition(inputData.getIgnition());
        calculateMaxSpreadRate();
        step = 0.125 * side / maxSpreadRate;
        ForestCell.setSide(side);
    }

    private void setIgnition(String ignition) {
        DataSource ign = ogr.Open(ignition);

        for (int i = 0; i < ign.GetLayerCount(); i++) {
            System.out.println(ign.GetLayer(i).GetFeatureCount());
            for (int j = 0; j < ign.GetLayer(i).GetFeatureCount(); j++) {

                var geom = ign.GetLayer(i).GetFeature(j).GetGeometryRef();
                if (geom.GetGeometryType() == ogrConstants.wkbPoint) {

                    System.out.println(geom.GetX() + " " + geom.GetY());
                     var x = (int) Math.floor(((-geom.GetX() + inputData.getStartPoint().GetX()) * side)
                                              / (side * side));
                    var y = (int) Math.floor(((geom.GetY() - inputData.getStartPoint().GetY()) * side) /
                                              (side * side));
                    cells[x][y].setState(ForestStates.EXTINGUISHING);
                }
            }
        }
    }


    private void defineNeighbours() {
        for (int i = 1; i < width; i++) {
            for (int j = 1; j < length; j++) {
                cells[i][j].setNeighbours(new ForestCell[]{cells[i - 1][j], cells[i - 1][j + 1],
                        cells[i][j + 1], cells[i + 1][j + 1],
                        cells[i + 1][j], cells[i + 1][j - 1], cells[i][j - 1], cells[i - 1][j - 1]});
            }
        }
    }

    private void calculateMaxSpreadRate() {
        for (int i = 1; i < width; i++) {
            for (int j = 1; j < length; j++) {
                for (int k = 0; k < 8; k++) {
                    maxSpreadRate = Math.max(maxSpreadRate, cells[i][j].calculateSpreadRate(k));
                }
            }
        }
    }

    public void propagate() {

        double newState;
        for (int i = 1; i < width; i++) {
            for (int j = 1; j < length; j++) {
                // N NE E SE S SW W NW
                // 0  1 2 3  4 5  6 7
                if (cells[i][j].getState().equals(ForestStates.BURNED))
                    continue;
                // 0 -> 1
                if (cells[i][j].getState() == ForestStates.UNBURNED && Arrays.stream(cells[i][j].neighbours)
                        .anyMatch(x -> x.getState().equals(ForestStates.DEVELOPING))) {
                    newState = (cells[i - 1][j - 1].calculateSpreadRate(3) +
                                cells[i + 1][j - 1].calculateSpreadRate(1) +
                                cells[i - 1][j + 1].calculateSpreadRate(5) +
                                cells[i + 1][j + 1].calculateSpreadRate(7)) * step /
                               Math.sqrt(2) / side + cells[i][j].getState().getValue() +
                               (cells[i][j - 1].calculateSpreadRate(2) +
                                cells[i - 1][j].calculateSpreadRate(4) +
                                cells[i + 1][j].calculateSpreadRate(0) +
                                cells[i][j + 1].calculateSpreadRate(6)) * step / side;

                    if (newState >= 1)
                        cells[i][j].setState(ForestStates.IGNITED);
                    continue;
                }

                // 1 -> 2
                if (cells[i][j].getState().equals(ForestStates.IGNITED)) {
                    var time = side / (Math.sqrt(Math.PI) * cells[i][j].calculateInternalSpreadRate());
                    // КОгда пройдет промежуток времени time, (-шаг моделирования)
                    // сделать time полем cell?, потом вычесть шаг моделирования.
                    cells[i][j].setState(ForestStates.DEVELOPING);
                }

                // 2 ->3
                // 3 -> 4
                else {
                    // должен пройти промежуток времни step
                    if (cells[i][j].getState().equals(ForestStates.DEVELOPING))
                        cells[i][j].setState(ForestStates.EXTINGUISHING);

                    if (cells[i][j].getState().equals(ForestStates.EXTINGUISHING))
                        cells[i][j].setState(ForestStates.BURNED);
                }
            }
        }
    }

    public void setFuel(String path, String fuelCodes) {
        Map<String, Double> fuelTypes = Stream.of(new Object[][]{
                {"Tree", 1.0},
                {"Shrub", 1.8},
                {"Herb", 1.6},
                {"Agriculture", 1.7},
                {"Developed", 0.1},
                {"Sparse", 0.1}
        }).collect(Collectors.toMap(data -> (String) data[0], data -> (Double) data[1]));

        // read fuel Codes
        Map<Integer, Double> codes = new HashMap<>();
        try {
            var fileReader = new FileReader(fuelCodes);
            var csvReader = new CSVReader(fileReader);
            String[] record;

            // Заголовок.
            record = csvReader.readNext();
            int index = ArrayUtils.indexOf(record, "EVT_LF");

            while ((record = csvReader.readNext()) != null) {
                codes.put(Integer.valueOf(record[0]), fuelTypes.get(record[index]));
            }

            csvReader.close();
            fileReader.close();
        }
        catch (CsvValidationException | IOException e) {
            e.printStackTrace();
        }

        Dataset fuel = gdal.Open(path);


        String tmp = checkDataProjection(fuel);

        var projected = gdal.Open(tmp);

        double x = inputData.getStartPoint().GetY(), y = inputData.getStartPoint().GetX();
        var transform = projected.GetGeoTransform();

        Band ftypes = fuel.GetRasterBand(1);
        //int xPixel, yPixel;
        for (int i = 0; i < width; i++) {
            //x = inputData.getStartPoint().GetY();
            for (int j = 0; j < length; j++) {

                int[] value = new int[1];
                ftypes.ReadRaster(i, j, 1, 1, value);

                var val = codes.getOrDefault(value[0], 0.0) != null ?
                        codes.getOrDefault(value[0], 0.0) : 0.0;
                cells[i][j].setFuel(val);


                //x += side / (111321.37778 / Math.cos(inputData.getStartPoint().GetY() * Math.PI / 180));
            }
            //y -= side / 111134.861111;
        }

        projected.delete();
        fuel.delete();
    }

    public void setWeather(String weather, int i) {
        FileReader file = null;
        try {
            file = new FileReader(weather);

           /* CSVParser parser = new CSVParserBuilder().withSeparator(';').build();
            var csvReader = new CSVReaderBuilder(file).withCSVParser(parser).build();
*/
            var csvReader = new CSVReader(file);
            String[] record;

            csvReader.skip(i);

            record = csvReader.readNext();

            String wind = record[1];
            String temperature = record[2];
            String humidity = record[3];

            setWindData(wind, 1);
            setWeatherData(temperature, humidity, 1);

            file.close();
        }
        catch (CsvValidationException | IOException e) {
            e.printStackTrace();
        }

    }

    private void setWeatherData(String temperature, String humidity, int number) {

        Dataset temperatureData = gdal.Open(temperature);
        Dataset humidityData = gdal.Open(humidity);

        String projectedTemp = checkDataProjection(temperatureData);
        String projectedHumidity = checkDataProjection(humidityData);

        double x = inputData.getStartPoint().GetY(), y = inputData.getStartPoint().GetX();
        var temp = temperatureData.GetGeoTransform();
        var humtransform = humidityData.GetGeoTransform();

        Band tempband = temperatureData.GetRasterBand(number);
        Band humband = humidityData.GetRasterBand(number);

        double[] temptransform = new double[1];
        double[] hum = new double[1];
        int xPixel, yPixel;
        for (int i = 0; i < width; i++) {
            x = inputData.getStartPoint().GetY();
            for (int j = 0; j < length; j++) {
                xPixel = (int) Math.floor(((x - temp[0]) * temp[5] -
                                           (y - temp[3]) * temp[2])
                                          / (temp[1] * temp[5] - temp[4] * temp[2]));

                yPixel = (int) Math.floor(((x - temp[0]) * temp[4] -
                                           (y - temp[3]) * temp[1]) /
                                          (temp[2] * temp[4] - temp[1] * temp[5]));


                tempband.ReadRaster(xPixel, yPixel, 1, 1, temptransform); // в кельвинах.

                xPixel = (int) Math.floor(((x - humtransform[0]) * humtransform[5] -
                                           (y - humtransform[3]) * humtransform[2])
                                          / (humtransform[1] * humtransform[5] - humtransform[4] * humtransform[2]));

                yPixel = (int) Math.floor(((x - humtransform[0]) * humtransform[4] -
                                           (y - humtransform[3]) * humtransform[1]) /
                                          (humtransform[2] * humtransform[4] - humtransform[1] * humtransform[5]));

                humband.ReadRaster(xPixel, yPixel, 1, 1, hum);

                cells[i][j].changeDefaultSpreadRate(temp[0] - 273.15, cells[i][j].getWindVelocity(), hum[0]);

                x += side / (111321.37778 / Math.cos(inputData.getStartPoint().GetY() * Math.PI / 180));
            }
            y -= side / 111134.861111;
        }


    }

    private String checkDataProjection(Dataset temperatureData) {
        var targetSRS = new SpatialReference();
        targetSRS.ImportFromEPSG(4326);
        var sourceSRS = temperatureData.GetSpatialRef();

        if (sourceSRS != targetSRS) {
            // Перепроецировать в targetSRS
            Vector<String> options = new Vector<>();
            options.add("-t_srs");
            options.add("EPSG:4326");

            WarpOptions warpOptions = new WarpOptions(options);
            Dataset[] srcData = {temperatureData};
            Dataset landscape = gdal.Warp("tmp\\tmp.tif", srcData, warpOptions);

        }
        return "tmp\\tmp.tif";
    }

    private void setWindData(String path, int number) {
        Dataset dataset = gdal.Open(path);

        checkDataProjection(dataset);

        double x = inputData.getStartPoint().GetY(), y = inputData.getStartPoint().GetX();
        var transform = dataset.GetGeoTransform();

        Band horizontal = dataset.GetRasterBand(2 * number - 1);
        Band vertical = dataset.GetRasterBand(2 * number);


        double[] proj_x = new double[1];
        double[] proj_y = new double[1];

        int xPixel, yPixel;
        for (int i = 0; i < width; i++) {
            x = inputData.getStartPoint().GetY();
            for (int j = 0; j < length; j++) {
                xPixel = (int) Math.floor(((x - transform[0]) * transform[5] -
                                           (y - transform[3]) * transform[2])
                                          / (transform[1] * transform[5] - transform[4] * transform[2]));

                yPixel = (int) Math.floor(((x - transform[0]) * transform[4] -
                                           (y - transform[3]) * transform[1]) /
                                          (transform[2] * transform[4] - transform[1] * transform[5]));


                horizontal.ReadRaster(xPixel, yPixel, 1, 1, proj_x);
                vertical.ReadRaster(xPixel, yPixel, 1, 1, proj_y);

                double val_velocity = Math.sqrt(proj_x[0] * proj_x[0] + proj_y[0] * proj_y[0]) * 36 / 10;

                int val_angle = (int) Math.round(180 - Math.toDegrees(Math.atan(proj_y[0] / proj_x[0])) + (proj_x[0] / Math.abs(proj_x[0]))); // куда
                val_angle = (180 + val_angle) % 360;

                cells[i][j].setWindVelocity(val_velocity);
                cells[i][j].setWindDirection(val_angle);

                x += side / (111321.37778 / Math.cos(inputData.getStartPoint().GetY() * Math.PI / 180));
            }
            y -= side / 111134.861111;
        }


    }

    public void setElevation(String path) {
        Dataset elevation = gdal.Open(path);

        String tmppath = checkDataProjection(elevation);

        var trans = elevation.GetGeoTransform();

        width  = (int) Math.ceil(elevation.GetRasterXSize() * trans[1] / side);
        length = (int) Math.ceil(elevation.GetRasterYSize() * Math.abs(trans[5]) / side);


        cells = new ForestCell[width][length];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                cells[i][j] = new ForestCell();
            }
        }

        Dataset projected = gdal.Open(tmppath);
        var transform = projected.GetGeoTransform();

        double x = inputData.getStartPoint().GetY(), y = inputData.getStartPoint().GetX();

        Band heights = elevation.GetRasterBand(1);

        int xPixel, yPixel;
        for (int i = 0; i < width; i++) {
            x = inputData.getStartPoint().GetY();
            for (int j = 0; j < length; j++) {

                int[] value = new int[1];
                heights.ReadRaster(i, j, 1, 1, value);

                cells[i][j].setHeight(value[0]);

                // Если elevation в метрах, то проще прибавлять разницу в метрах.
                //x += side / (102667.3465 / Math.cos(Math.toRadians(inputData.getStartPoint().GetX())));
            }
            //y -= side / 111134.861111;
        }

        projected.delete();
        elevation.delete();

    }


}
