public class ForestCell {
    public ForestCell() {
        state = ForestStates.UNBURNED;
    }

    public ForestStates getState() {
        return state;
    }

    public void setState(ForestStates state) {
        this.state = state;
    }

    ForestStates state;

    public void setFuel(double fuel) {
        this.fuel = fuel;
    }

    double fuel;

    public void setWindVelocity(double windVelocity) {
        this.windVelocity = windVelocity;
    }

    public void setWindDirection(double windDirection) {
        this.windDirection = windDirection;
    }

    public double getWindVelocity() {
        return windVelocity;
    }

    double windVelocity;
    double windDirection;

    public void setHeight(double height) {
        this.height = height;
    }

    double height;
    double spreadRateDefault;
    ForestCell[] neighbours; // N NE E SE S SW W NW

    public void setNeighbours(ForestCell[] neighbours) {
        this.neighbours = neighbours;
    }

    public ForestCell(double height) {
        fuel              = 0;
        state             = ForestStates.UNBURNED;
        windVelocity      = 0;
        windDirection     = 0;
        spreadRateDefault = 0;
        this.height       = height;
    }

    public static int getSide() {
        return side;
    }

    public static void setSide(int side) {
        ForestCell.side = side;
    }

    static int side;

    public ForestCell(double fuel, double temperature,
                      double humidity, double windVelocity,
                      double windDirection, double height) {
        this.fuel = fuel;
        changeDefaultSpreadRate(temperature, windVelocity, humidity);
        this.windVelocity  = windVelocity;
        this.windDirection = windDirection;
        this.height        = height;
        state              = ForestStates.UNBURNED;
    }

    public double getHeight() {
        return height;
    }


    public double getWindDirection() {
        return windDirection;
    }

    public void changeDefaultSpreadRate(double temperature,
                                        double windVelocity,
                                        double humidity) {
        this.spreadRateDefault = 0.03 * temperature + 0.05 * windVelocity +
                                 0.01 * (100 - humidity) - 0.3;

    }

    public double calculateSpreadRate(int i) {
        // N NE E SE S SW W NW
        double wind = 1;
        switch (i) {
            case 0:
                wind = Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection)));
                break;
            case 1:
                wind = Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection + 45)));
                break;
            case 2:
                wind = Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection + 90)));
                break;
            case 3:
                wind = Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(135 - windDirection)));
                break;
            case 4:
                wind = Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(180 - windDirection)));
                break;
            case 5:
                wind = Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection - 135)));
                break;
            case 6:
                wind = Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection - 90)));
                break;
            case 7:
                wind = Math.exp(0.1783 * windVelocity * Math.cos(Math.toRadians(windDirection - 45)));

        }

        double slope = 1;
        if (windDirection > 90 && windDirection < 270) {
            slope = Math.exp(-3.533 * Math.pow((getHeight() - neighbours[i].getHeight()) / side
                                               * Math.cos(Math.toRadians(180 - windDirection)), 1.2));
        } else {
            slope = Math.exp(3.533 * Math.pow((getHeight() - neighbours[i].getHeight()) / side
                                              * Math.cos(Math.toRadians(windDirection)), 1.2));
        }
        return spreadRateDefault * fuel * wind * slope;

    }

    public double calculateInternalSpreadRate() {
        var x = (neighbours[2].getHeight() - neighbours[6].getHeight()) / (2 * side);
        var y = (neighbours[0].getHeight() - neighbours[4].getHeight()) / (2 * side);

        var sl = Math.toDegrees(Math.atan(Math.sqrt(x * x + y * y)));

        double slope = 1;
        if (windDirection > 90 && windDirection < 270) {
            slope = Math.exp(-3.533 * Math.pow(sl * Math.cos(Math.toRadians(180 - windDirection)), 1.2));
        } else {
            slope = Math.exp(3.533 * Math.pow(sl * Math.cos(Math.toRadians(windDirection)), 1.2));
        }

        return spreadRateDefault * fuel * slope * Math.exp(0.1783 * windVelocity);
    }
}
