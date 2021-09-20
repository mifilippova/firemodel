package com.model.urban;

import com.model.forest.ForestCell;
import com.model.forest.ForestStates;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.ogr;

import java.util.*;

public class UrbanCell {
    double weather;
    String geometry;
    double windVelocity;
    double windAngle;
    UrbanStates state;
    double side;

    public double getWeather() {
        return weather;
    }

    public String getGeometry() {
        return geometry;
    }

    public UrbanCell(double x, double y, double side) {
        this.state = UrbanStates.UNBURNED;
        this.side  = side;
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

    public double getWindVelocity() {
        return windVelocity;
    }

    public double getSide() {
        return side;
    }


    public double getIgnitionProbability() {
        return ignitionProbability;
    }

    public void setIgnitionProbability(double ignitionProbability) {
        this.ignitionProbability = ignitionProbability;
    }

    double ignitionProbability = 1.0;


    public double getWindAngle() {
        return windAngle;
    }

    public UrbanStates getState() {
        return state;
    }

    double[] coords;

    public void setState(UrbanStates state) {
        this.state = state;
    }

    public double getMaterial() {
        return material;
    }

    public double getInnerTime() {
        return innerTime;
    }

    public void setInnerTime(double innerTime) {
        this.innerTime = innerTime;
    }

    double innerTime = 0;

    public void setMaterial(double material) {
        this.material = material;
    }

    public void setWeather(double weather) {
        this.weather = weather;
    }

    public void setWindVelocity(double windVelocity) {
        this.windVelocity = windVelocity;
    }

    public void setWindAngle(double windAngle) {
        this.windAngle = windAngle;
    }


    public Geometry calculateAreaOfInterest() {
        var a = 3 * windVelocity / 5 + 3 + side / 2;
        var pt = Geometry.CreateFromWkt(geometry).Centroid();
        return pt.Buffer(a);
    }

    public void fireSpreadOnUrban(double step,
                                  UrbanStates[][] states, Random rand, int i, int j) {

        if (innerTime == 0) {
            if (state.equals(UrbanStates.SLOWDEVELOPING))
                innerTime = (rand.nextDouble() * 3 + 5) * 60;
            else {
                if (material == 1.0) {
                    innerTime = (rand.nextDouble() * 10 + 10) * 60;
                } else if (material == 0.8)
                    innerTime = (rand.nextDouble() * 10 + 20) * 60;
                else
                    innerTime = (rand.nextDouble() * 10 + 30) * 60;
            }
        } else {
            innerTime -= step;
            if (innerTime <= 0) {
                innerTime    = 0;
                states[i][j] = UrbanStates.values()[state.getValue() + 1];
            }
        }

    }

    public void developIgnition(double step, UrbanStates[][] states, Random rand, int i, int j) {
        if (innerTime == 0) {
            innerTime = (rand.nextDouble() * 2 + 4) * 60;
        } else {
            innerTime -= step;
            if (innerTime <= 0) {
                innerTime    = 0;
                states[i][j] = UrbanStates.SLOWDEVELOPING;
            }
        }
    }

    public void developFlashover(double step, UrbanStates[][] states, Random rand, int i, int j) {
        if (innerTime == 0) {
            if (material == 1.0) {
                innerTime = (rand.nextDouble() * 10 + 20) * 60;
            } else if (material == 0.8)
                innerTime = (rand.nextDouble() * 10 + 30) * 60;
            else
                innerTime = (rand.nextDouble() * 10 + 50) * 60;
        } else {
            innerTime -= step;
            if (innerTime <= 0) {
                innerTime    = 0;
                states[i][j] = UrbanStates.EXTINGUISHED;
            }
        }
    }

    static double material = 1.0;

    public void fireSpreadOnForest(ForestCell[][] cells, int i, int j, int width, int length) {
        double a = 3 * getWindVelocity() / 5 + 3 + side / 2;
        double b = -2 * getWindVelocity() / 15 + 3 + side / 2;
        double c = -1 * getWindVelocity() / 15 + 3 + side / 2;


        var t = Math.sqrt(b * (a + c) / 2.0);
        var geom = Geometry.CreateFromWkt(geometry).Centroid();
        double x = geom.GetX(), y = geom.GetY();
        var influence = new Geometry(ogr.wkbLinearRing);
        double[] f = rotatedCoords(x - t, y + c, x, y, windAngle);
        influence.AddPoint(f[0], f[1]);
        f = rotatedCoords(x + t, y + c, x, y, windAngle);
        influence.AddPoint(f[0], f[1]);
        f = rotatedCoords(x + t, y - a, x, y, windAngle);
        influence.AddPoint(f[0], f[1]);
        f = rotatedCoords(x - t, y - a, x, y, windAngle);
        influence.AddPoint(f[0], f[1]);
        f = rotatedCoords(x - t, y + c, x, y, windAngle);
        influence.AddPoint(f[0], f[1]);

        var influenceArea = new Geometry(ogr.wkbPolygon);
        influenceArea.AddGeometry(influence);


        int mini = (int) Math.max(0, i - a / side);
        int minj = (int) Math.max(0, j - a / side);
        int maxi = (int) Math.min(width, i + a / side);
        int maxj = (int) Math.min(length, j + a / side);

        for (int l = mini; l < maxi; l++) {
            for (int m = minj; m < maxj; m++) {
                if (cells[l][m].getState() != ForestStates.UNBURNED)
                    continue;
                var forestGeom = Geometry.CreateFromWkt(cells[l][m].getGeometry());
                if (influenceArea.Intersect(forestGeom))
                    cells[l][m].becomeIgnited();
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

    public void addIgnitionProbability(double v) {
        ignitionProbability *= v;
    }
}
