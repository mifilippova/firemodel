import input.InputData;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.ogr.Feature;
import org.gdal.ogr.Geometry;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.SpatialReference;

public class TestUrbanModel {
    public static void main(String[] args){
        //var fire = new Geometry(ogrConstants.wkbCircularString);
        int a = 30, b = 10, c = 10;
        int x = 917374, y =  3780275;

        String construction = "{\"curvePaths\": [[[0,0], {\"c\": [[3,3],[1,4]]} ]]}";

        var fire = Geometry.CreateFromJson(construction);
        var spatialReferenceUTM = new SpatialReference();
        int zone = 10;
        spatialReferenceUTM.SetProjCS(String.format("UTM %d (WGS84)", zone));
        spatialReferenceUTM.SetWellKnownGeogCS("WGS84");
        spatialReferenceUTM.SetUTM(zone);


        var sourceSRS = new SpatialReference();
        sourceSRS.ImportFromEPSG(4326);


        gdal.AllRegister();
        var driver = gdal.GetDriverByName("ESRI Shapefile");
        var dataset = driver.Create("..\\data\\temp.shp", 0, 0,
                1, gdalconst.GDT_Unknown, (String[]) null);
        var dataLayer = dataset.CreateLayer("area",
                sourceSRS, ogrConstants.wkbCurve);

        var feature = new Feature(dataLayer.GetLayerDefn());
        feature.SetGeometry(fire); // poly
        dataLayer.CreateFeature(feature);


        feature.delete();
        dataLayer.delete();
        dataset.delete();

    }



}