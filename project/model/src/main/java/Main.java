import input.InputData;
import org.gdal.gdal.gdal;

import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args){

        gdal.AllRegister();
        int side = 30; // m
        String elevation = "..\\data\\elevation\\US_DEM2016\\US_DEM2016.tif";
        String fuel = "..\\data\\US_200EVT\\US_200EVT.tif";
        String csvfueltypes = "..\\data\\US_200EVT\\LF16_EVT_200.csv";
        String weather = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\weather\\weather.csv";

        String ignition = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\ignition\\ignition.shp";


        var start = LocalDateTime.of(2019, 10,28, 1, 34);
        var finish = LocalDateTime.of(2019, 10, 28, 11, 34);


        int weatherPeriod = 60; // minutes
        double houseMaterial = 1.0;

         double[] coords = {34.11, -118.50, 34.07, -118.47};

        var buildings = "C:\\Users\\admin\\Documents\\firemodel\\project\\data\\buildings\\map.osm";

        var input = new InputData(coords, fuel, csvfueltypes, elevation, weather, weatherPeriod,
                start, finish, side, ignition, buildings, houseMaterial);

        var globalFire = new GlobalFire(input);
        globalFire.propagate();
    }
}
