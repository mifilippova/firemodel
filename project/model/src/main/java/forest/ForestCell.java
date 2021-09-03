package forest;

import org.gdal.ogr.Geometry;
import org.gdal.ogr.ogr;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;
import urban.UrbanCell;
import urban.UrbanStates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ForestCell {
    String geometry;
    private boolean ignitedByUrban = false;
    ForestStates state;
    private double innerFireTime;
    double maxSpreadRate = 0.0;
    double firePeriod = 0.0;
    double fuel;
    double windVelocity;
    double windDirection;
    double height;
    double spreadRateDefault;
    ForestCell[] neighbours; // N NE E SE S SW W NW
    double slope;
    double[] spreadRates;
    static int side;


    public boolean isIgnitedByUrban() {
        return ignitedByUrban;
    }

    public String getGeometry() {
        return geometry;
    }

    public ForestCell(double x, double y) {
        state = ForestStates.UNBURNED;
        Geometry poly = calculateGeometry(x, y);
        this.geometry = poly.ExportToWkt();
    }

    private Geometry calculateGeometry(double x, double y) {
        var ring = new Geometry(ogr.wkbLinearRing);
        ring.AddPoint(x, y);
        ring.AddPoint(x + side, y);
        ring.AddPoint(x + side, y + side);
        ring.AddPoint(x, y + side);
        ring.AddPoint(x, y);

        var poly = new Geometry(ogr.wkbPolygon);
        poly.AddGeometry(ring);
        return poly;
    }

    public ForestStates getState() {
        return state;
    }

    public void setState(ForestStates state) {
        this.state = state;
    }

    public double getMaxSpreadRate() {
        return maxSpreadRate;
    }

    public double getFirePeriod() {
        return firePeriod;
    }

    public void setFuel(double fuel) {
        this.fuel = fuel;
    }

    public double getFuel() {
        return fuel;
    }


    public void setWindVelocity(double windVelocity) {
        this.windVelocity = windVelocity;
    }

    public void setWindDirection(double windDirection) {
        this.windDirection = windDirection;
    }

    public double getWindVelocity() {
        return windVelocity;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double[] getSpreadRates() {
        return spreadRates;
    }

    public void setNeighbours(ForestCell[] neighbours) {
        this.neighbours = neighbours;
    }

    public static int getSide() {
        return side;
    }

    public static void setSide(int side) {
        ForestCell.side = side;
    }

    public double getHeight() {
        return height;
    }

    public double getWindDirection() {
        return windDirection;
    }

    public void initSlope() {
        var x = Math.ceil((neighbours[1].getHeight() - neighbours[7].getHeight())
                          + 2 * (neighbours[2].getHeight() - neighbours[6].getHeight())
                          + (neighbours[3].getHeight() - neighbours[5].getHeight())) /
                (8 * side); //(neighbours[2].getHeight() - neighbours[6].getHeight()) / (2 * side);
        var y = Math.ceil((neighbours[7].getHeight() - neighbours[5].getHeight())
                          + 2 * (neighbours[0].getHeight() - neighbours[4].getHeight())
                          + (neighbours[1].getHeight() - neighbours[3].getHeight())) /
                (8 * side); //(neighbours[0].getHeight() - neighbours[4].getHeight()) / (2 * side);

        slope = Math.toDegrees(Math.atan(Math.sqrt(x * x + y * y)));
    }

    public void changeDefaultSpreadRate(double temperature,
                                        double windVelocity,
                                        double humidity) {
        this.spreadRateDefault = 0.03 * temperature + 0.05 * windVelocity +
                                 0.01 * (100 - humidity) - 0.3;

    }

    public void initSpreadRates() {
        if (spreadRates == null)
            spreadRates = new double[8];
        for (int i = 0; i < 8; i++) {
            spreadRates[i] = calculateSpreadRate(i);
        }
        maxSpreadRate = Arrays.stream(spreadRates).max().getAsDouble();
        if (maxSpreadRate > 0) firePeriod = 0.45 * side / maxSpreadRate;

    }


    private double calculateSpreadRate(int i) {
        // N NE E SE S SW W NW
        double wind = switch (i) {
            case 0 -> Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection - 180)));
            case 1 -> Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection - 135)));
            case 2 -> Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection - 90)));
            case 3 -> Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection - 225)));
            case 4 -> Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection)));
            case 5 -> Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection + 45)));
            case 6 -> Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection + 90)));
            case 7 -> Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection + 225)));
            default -> 1;
        };

        int sign = 1;
        //if (getHeight() > neighbours[i].getHeight()) sign = -1;
        double sl = Math.exp(sign * 3.533 * Math.pow(Math.tan(Math.toRadians(slope)
                                                              * Math.abs(Math.cos(Math.toRadians(windDirection)))), 1.2));


        return spreadRateDefault * fuel * wind * sl;

    }

    public double calculateInternalSpreadRate() {

        int sign = 1;
        double sl = Math.exp(sign * 3.533 *
                             Math.pow(Math.tan(Math.toRadians(slope)
                                               * Math.abs(Math.cos(Math.toRadians(windDirection)))), 1.2));

        return spreadRateDefault * fuel * sl * Math.exp(0.1783 * windVelocity);
    }

    public double getInnerFireTime() {
        return innerFireTime;
    }

    public void setInnerFireTime(double innerFireTime) {
        this.innerFireTime = innerFireTime;
    }


    public void fireSpreadOnUrban(UrbanCell[][] urbanCells, int i, int j, int width, int length) {
        var k = getMaxSpreadRate() < 13.1 ? 3 : 4.5;
        var a = (3 * getWindVelocity() / 5 + 3) * k + side * 1.0 / 2;
        var b = -2 * getWindVelocity() / 15 + 3 + side * 1.0 / 2;
        var c = -1 * getWindVelocity() / 15 + 3 + side * 1.0 / 2;

        var t = Math.sqrt(b * (a + c) / 2.0);
        var geom = Geometry.CreateFromWkt(geometry).Centroid();
        double x = geom.GetX(), y = geom.GetY();
        var influence = new Geometry(ogr.wkbLinearRing);
        double[] f = rotatedCoords(x - t, y + c, x, y, windDirection);
        influence.AddPoint(f[0], f[1]);
        f = rotatedCoords(x + t, y + c, x, y, windDirection);
        influence.AddPoint(f[0], f[1]);
        f = rotatedCoords(x + t, y - a, x, y, windDirection);
        influence.AddPoint(f[0], f[1]);
        f = rotatedCoords(x - t, y - a, x, y, windDirection);
        influence.AddPoint(f[0], f[1]);
        f = rotatedCoords(x - t, y + c, x, y, windDirection);
        influence.AddPoint(f[0], f[1]);

        var poly = new Geometry(ogr.wkbPolygon);
        poly.AddGeometry(influence);


        int mini = (int) Math.max(0, i - a / side);
        int minj = (int)Math.max(0, j - a / side);
        int maxi = (int) Math.min(width - 1, i + a / side);
        int maxj = (int) Math.min(length - 1, j + a / side);

        double ign;
        for (int l = mini; l <= maxi; l++) {
            for (int m = minj; m <= maxj; m++) {
                if (urbanCells[l][m] != null
                    && urbanCells[l][m].getState().equals(UrbanStates.UNBURNED)){
                    var urbanGeom = Geometry.CreateFromWkt(urbanCells[l][m].getGeometry());
                    if (urbanGeom.Intersects(poly)){
                        ign = urbanCells[l][m].getMaterial() * urbanCells[l][m].getWeather()
                              * urbanGeom.Intersection(poly).Area() / urbanGeom.Area();
                        if (k == 3)
                            ign *= 0.4;
                        urbanCells[l][m].addIgnitionProbability(1 - ign);

                    }
                }
            }
        }

    }

    private double[] rotatedCoords(double pointX, double pointY,
                                   double originX, double originY, double angle) {
        var x = Math.cos(Math.toRadians(angle)) * (pointX - originX)
                + Math.sin(Math.toRadians(angle)) * (pointY - originY) + originX;
        var y = -Math.sin(Math.toRadians(angle)) * (pointX - originX)
                + Math.cos(Math.toRadians(angle)) * (pointY - originY) + originY;
        return new double[]{x, y};

    }

    public void becomeIgnited() {
        ignitedByUrban = true;
    }

    public void makeIgnitedByUrbanDefault() {
        ignitedByUrban = false;
    }
}
