import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import forest.ForestArea;
import input.InputData;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.ogr.Feature;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.ogr;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import urban.UrbanArea;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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
        forest      = new ForestArea(inputData, spatialReferenceUTM, length, width);
        urban       = new UrbanArea(inputData, spatialReferenceUTM, length, width);
        setWeather(inputData.getMeteodata(), 0);

        forest.findUrbanNeighbours(urban.getUrbanCells());
        urban.findForestNeighbours(forest.getCells());
        urban.findUrbanNeighbours();

    }

    private void setWeather(String weather, int number) {
        FileReader file = null;
        try {
            file = new FileReader(weather);

            var csvReader = new CSVReader(file);
            String[] record;
            csvReader.skip(number);

            int ind = weather.lastIndexOf(File.separator);
            String dir = weather.substring(0, ind + 1);

            record = csvReader.readNext();

            // TODO: merge weather data.

            forest.setWindData(dir + record[1], dir + record[2]);
            forest.setWeatherData(dir + record[3], dir + record[4]);
            urban.setWindData(dir + record[1], dir + record[2], currentDate);
            urban.setWeather(dir + record[4], currentDate);

            csvReader.close();
        }
        catch (CsvValidationException | IOException e) {
            e.printStackTrace();
        }
    }

    public void propagate() {
        int step = 60;
        double minutesLeft = 0;
        System.out.println(currentDate);
        Map<Long, Double> urbanIgnitionProbabilities = new HashMap<>();
        while (currentDate.compareTo(inputData.getFinish()) < 0) {
            if (minutesLeft == inputData.getWeatherPeriod()) {
                setWeather(inputData.getMeteodata(),
                        (int) Duration.between(inputData.getStart(), currentDate).toHours());
                forest.findUrbanNeighbours(urban.getUrbanCells());
                urban.findForestNeighbours(forest.getCells());
                urban.findUrbanNeighbours();
                minutesLeft = 0;
            }
            forest.propagate(minutesLeft, step, currentDate);
            forest.propagateInUrban(urbanIgnitionProbabilities);
            urban.propagate(step, urbanIgnitionProbabilities);
            urban.propagateInForest();

            forest.updateStates();
            urban.updateStates(urbanIgnitionProbabilities);

            urbanIgnitionProbabilities.clear();
            currentDate = currentDate.plusSeconds(step);
            minutesLeft += (step / 60);

        }

        presentResult();

    }

    private void presentResult() {
        presentUrbanResults();
        presentForestResults();
    }

    private void presentForestResults() {
        String path = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\result\\result_" + currentDate
                .format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")) + ".tif";

        Dataset resultData = gdal.GetDriverByName("GTiff").Create(path,
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
                        new int[]{forest.getCells()[i][j].getState().getValue()});
            }
        }

        result.delete();
        resultData.delete();
    }

    private void presentUrbanResults() {
        var driver = gdal.GetDriverByName("ESRI Shapefile");
        var dataset = driver.Create("C:\\Users\\admin\\Documents\\firemodel\\"
                                    + "project\\data\\result\\buildings.shp", 0, 0,
                1, gdalconst.GDT_Unknown, (String[]) null);
        var dataLayer = dataset.CreateLayer("houses",
                spatialReferenceUTM, ogr.wkbPolygon);

        var state = new FieldDefn("state", ogr.OFSTInt16);
        dataLayer.CreateField(state);
        for (int i = 0; i < urban.getUrbanCells().size(); i++) {
            var feature = new Feature(dataLayer.GetLayerDefn());
            feature.SetGeometry(Geometry.CreateFromWkt(urban.getUrbanCells().get(i).getGeometry()));
            feature.SetField(state.GetName(), urban.getUrbanCells().get(i).getState().getValue());
            dataLayer.CreateFeature(feature);
            feature.delete();
        }
        dataLayer.delete();
        dataset.delete();
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
