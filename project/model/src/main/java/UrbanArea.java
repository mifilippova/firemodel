import org.gdal.gdal.gdal;
import org.gdal.ogr.Feature;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.ogr;
import org.gdal.osr.SpatialReference;


public class UrbanArea {
    // shapefile
    // Инициализация.


    UrbanCell[][] cells;

    public UrbanArea(InputData inputData, SpatialReference spatialReferenceUTM, int length, int width) {
        gdal.AllRegister();

        var data = ogr.Open(inputData.getBuildingsPath());
        var layer = data.GetLayerByName("multipolygons");
        Feature f;
        while((f = layer.GetNextFeature()) != null){
            for (int i = 0; i < f.GetFieldCount(); i++) {
                if ("building".equals(f.GetFieldDefnRef(i).GetNameRef())){
                    System.out.println(f.GetFieldAsString(i));
                }
            }
        }
        // Обычный массив со стандартной стороной, в клетке хранится площадь.
    }
}
