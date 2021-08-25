import java.util.Arrays;

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
    private double innerFireTime;

    public double getMaxSpreadRate() {
        return maxSpreadRate;
    }

    public double getFirePeriod() {
        return firePeriod;
    }

    double maxSpreadRate = 0.0;
    double firePeriod = 0.0;

    public void setFuel(double fuel) {
        this.fuel = fuel;
    }

    public double getFuel() {
        return fuel;
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
    double slope;

    public double[] getSpreadRates() {
        return spreadRates;
    }

    double[] spreadRates;

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

    public double getWindDirection() {
        return windDirection;
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

        if (maxSpreadRate > 0) firePeriod = 0.4 * side / maxSpreadRate;


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
}
