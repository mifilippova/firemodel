package urban;

import forest.ForestCell;
import forest.ForestStates;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.ogr;

import java.util.*;

public class UrbanCell {
    double material;

    public double getWeather() {
        return weather;
    }

    double weather;

    public String getGeometry() {
        return geometry;
    }

    String geometry;

    List<ForestCell> influenceOnForest;
    List<UrbanCell> influenceOnUrban;


    public UrbanCell(long id, String geometry) {
        this.id           = id;
        this.geometry     = geometry;
        this.state        = UrbanStates.UNBURNED;
        influenceOnForest = new ArrayList<>();
        influenceOnUrban  = new ArrayList<>();
    }

    public double getWindVelocity() {
        return windVelocity;
    }

    public double getSide() {
        return side;
    }

    double windVelocity;
    double windAngle;
    UrbanStates state;
    double side;
    long id;


    public double getWindAngle() {
        return windAngle;
    }

    public UrbanStates getState() {
        return state;
    }

    double[] coords;

    public long getId() {
        return id;
    }


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

    public UrbanCell(double material, double weather, double windVelocity,
                     double windAngle, UrbanStates state, Geometry geometry, long id) {
        this.material     = material;
        this.weather      = weather;
        this.windVelocity = windVelocity;
        this.windAngle    = windAngle;
        this.state        = state;
        this.id           = id;
    }


    public UrbanCell(UrbanStates state, double area, double[] coords, double houseMaterial) {
        this.state    = state;
        this.side     = Math.sqrt(area);
        this.coords   = coords;
        this.material = houseMaterial;
    }

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

    public void addInfluenceOnForest(ForestCell forestCell) {
        influenceOnForest.add(forestCell);
    }

    public Geometry calculateAreaOfInterest() {
        var a = 3 * windVelocity / 5 + 3 + side / 2;
        var pt = Geometry.CreateFromWkt(geometry).Centroid();
        return pt.Buffer(a);
    }

    public void addUrbanNeighbour(UrbanCell urbanCell) {
        influenceOnUrban.add(urbanCell);
    }

    public void fireSpreadOnUrban(double step, Map<Long, Double> urbanIgnitionProbabilities,
                                  Map<Long, UrbanStates> states, Random rand) {
        Geometry influenceArea = getInfluenceArea();

        for (int i = 0; i < influenceOnUrban.size(); i++) {
            var geometry = Geometry.CreateFromWkt(influenceOnUrban.get(i).getGeometry());
            if (influenceOnUrban.get(i).getState().equals(UrbanStates.UNBURNED) && influenceArea.Intersect(geometry)) {
                var pr = influenceOnUrban.get(i).getMaterial() * influenceOnUrban.get(i).getWeather()
                         * influenceArea.Intersection(geometry).Area() / geometry.Area();

                if (urbanIgnitionProbabilities.containsKey(influenceOnUrban.get(i).getId()))
                    urbanIgnitionProbabilities.put(influenceOnUrban.get(i).getId(),
                            urbanIgnitionProbabilities.get(influenceOnUrban.get(i).getId()) + 1 - pr);
                else
                    urbanIgnitionProbabilities.put(influenceOnUrban.get(i).getId(), 1 - pr);
            }
        }
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
                innerTime = 0;
                states.put(id, UrbanStates.values()[state.getValue() + 1]);
            }
        }

    }

    private Geometry getInfluenceArea() {
        double a = 3 * getWindVelocity() / 5 + 3 + side / 2;
        double b = -2 * getWindVelocity() / 15 + 3 + side / 2;
        double c = -1 * getWindVelocity() / 15 + 3 + side / 2;

        var center = Geometry.CreateFromWkt(getGeometry()).Centroid();
        var ring = new Geometry(ogr.wkbLinearRing);
        ring.AddPoint(center.GetX() - c, center.GetY() - b);
        ring.AddPoint(center.GetX() + a, center.GetY() - b);
        ring.AddPoint(center.GetX() + a, center.GetY() + b);
        ring.AddPoint(center.GetX() - c, center.GetY() + b);

        var influenceArea = new Geometry(ogr.wkbPolygon);
        influenceArea.AddGeometry(ring);
        return influenceArea;
    }

    public void developIgnition(double step, Map<Long, UrbanStates> states, Random rand) {

        if (innerTime == 0) {
            innerTime = (rand.nextDouble() * 2 + 4) * 60;
        } else {
            innerTime -= step;
            if (innerTime <= 0) {
                innerTime = 0;
                states.put(id, UrbanStates.SLOWDEVELOPING);
            }
        }
    }

    public void developFlashover(double step, Map<Long, UrbanStates> states, Random rand) {
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
                innerTime = 0;
                states.put(id, UrbanStates.EXTINGUISHED);
            }
        }
    }

    public void fireSpreadOnForest() {
        var influenceArea = getInfluenceArea();
        for (int i = 0; i < influenceOnForest.size(); i++) {
            if (!influenceOnForest.get(i).getState().equals(ForestStates.UNBURNED))
                continue;

            var forestCell = Geometry.CreateFromWkt(influenceOnForest.get(i).getGeometry());
            if (influenceArea.Intersect(forestCell)){
                influenceOnForest.get(i).becomeIgnited();
            }
        }
    }
}
