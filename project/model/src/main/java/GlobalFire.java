import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import java.time.LocalDateTime;

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
        this.side = inputData.getSide();

        initSpatialReference();
        defineAreaSize();

        currentDate = inputData.getStart();
        forest = new ForestArea(inputData, spatialReferenceUTM, length, width);
        //urban = new UrbanArea(inputData, spatialReferenceUTM, length, width);

    }

    public void propagate() {
        double newState = 0;
        int step = 60;
        double minutesLeft = 0;
        while (currentDate.compareTo(inputData.getFinish()) < 0) {
            if (minutesLeft == inputData.weatherPeriod) {
                minutesLeft = 0;
            }
            forest.propagate(minutesLeft, step, currentDate);
            currentDate = currentDate.plusSeconds(step);
            minutesLeft += (step / 60);

        }

        forest.presentResult();

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
