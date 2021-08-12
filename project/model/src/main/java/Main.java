import java.time.LocalDate;
import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args){
        int side = 30; // m


        String elevation = "..\\data\\elevation\\US_DEM2016\\US_DEM2016.tif";
        String fuel = "..\\data\\US_200EVT\\US_200EVT.tif";
        String csvfueltypes = "..\\data\\US_200EVT\\LF16_EVT_200.csv";
        String weather = "..\\data\\meteo\\weather.csv";

        String ignition = "..\\data\\ignition\\ignition.shp";


        var start = LocalDateTime.of(2019, 10,28, 1, 34);
        var finish = LocalDateTime.of(2019, 11, 2, 1, 34);


        int weatherPeriod = 60; // minutes



        double[] coords = {34.11, -118.50, 34.07, -118.47};

        var input = new InputData(coords, fuel, csvfueltypes, elevation, weather, weatherPeriod,
                start, finish, side, ignition);

        var forest = new ForestArea(input);
    }
}
