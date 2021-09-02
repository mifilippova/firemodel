package input;

import org.gdal.ogr.Geometry;
import org.gdal.ogr.ogrConstants;

import java.time.LocalDateTime;


public class InputData {

    int side;

    public String getFuel() {
        return fuel;
    }

    public String getFuelCodes() {
        return fuelCodes;
    }

    public String getElevation() {
        return elevation;
    }

    public int getWeatherPeriod() {
        return weatherPeriod;
    }

    public String getIgnition() {
        return ignition;
    }

    String ignition;

    String fuel;
    String fuelCodes;
    String elevation;

    public String getBuildingsPath() {
        return buildingsPath;
    }

    String buildingsPath;
    int weatherPeriod;

    public double getHouseMaterial() {
        return houseMaterial;
    }

    double houseMaterial;

    public InputData(double[] coords, String fuel, String fuelCodes, String elevation, String meteodata, int meteoDataChange,
                     LocalDateTime start, LocalDateTime finish, int side, String ignition, String buildingsPath, double houseMaterial) {
        this.meteodata = meteodata;
        this.start     = start;
        this.finish    = finish;
        this.fuel = fuel;
        this.fuelCodes = fuelCodes;
        this.elevation = elevation;
        this.weatherPeriod = meteoDataChange;
        this.buildingsPath = buildingsPath;
        this.houseMaterial = houseMaterial;

        startPoint = new Geometry(ogrConstants.wkbPoint);
        endPoint = new Geometry(ogrConstants.wkbPoint);
        startPoint.AddPoint(coords[0], coords[1]);
        endPoint.AddPoint(coords[2], coords[3]);

        this.ignition = ignition;
        this.side = side;
    }


    public Geometry getStartPoint() {
        return startPoint;
    }

    // coordinates
    private Geometry startPoint;

    public Geometry getEndPoint() {
        return endPoint;
    }

    private Geometry endPoint;

    public String getMeteodata() {
        return meteodata;
    }

    private final String meteodata; // files per time

    public int getSide() {
        return side;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getFinish() {
        return finish;
    }

    // start and end of simulation
    private final LocalDateTime start;
    private final LocalDateTime finish;


}
