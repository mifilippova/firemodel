package com.model;

import com.model.input.InputData;
import org.gdal.gdal.gdal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    public static void main(String[] args){
        gdal.AllRegister();

        int side = Integer.parseInt(args[0]); //30; // m
        String elevation = args[1]; //"..\\data\\elevation\\US_DEM2016\\US_DEM2016.tif";
        String fuel = args[2]; //"..\\data\\US_200EVT\\US_200EVT.tif";
        String csvfueltypes = args[3];//"..\\data\\US_200EVT\\LF16_EVT_200.csv";
        String weather = args[4]; //"C:\\Users\\admin\\Documents\\firemodel\\project\\data\\weather\\weather.csv";

        String ignition = args[5]; //"C:\\Users\\admin\\Documents\\firemodel\\project\\data\\ignition\\ignition.shp";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        LocalDateTime start = LocalDateTime.parse(args[6].concat(" " + args[7]), formatter); //LocalDateTime.of(2019, 10,28, 1, 34);
        var finish = LocalDateTime.parse(args[8].concat(" " + args[9]), formatter);;//LocalDateTime.of(2019, 10, 28, 11, 34); // 11


        int weatherPeriod = Integer.parseInt(args[10]); //60; // minutes
        double houseMaterial = Double.parseDouble(args[11]); //1.0;

         double[] coords = {Double.parseDouble(args[12]), Double.parseDouble(args[13]),
                 Double.parseDouble(args[14]), Double.parseDouble(args[15])};//{34.11, -118.50, 34.07, -118.47};

        var buildings = args[16]; //"C:\\Users\\admin\\Documents\\firemodel\\project\\data\\buildings\\map.osm";

        var input = new InputData(coords, fuel, csvfueltypes, elevation, weather, weatherPeriod,
                start, finish, side, ignition, buildings, houseMaterial);

        var globalFire = new GlobalFire(input);
        globalFire.propagate();
    }
}
