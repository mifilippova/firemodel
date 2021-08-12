public enum ForestStates {
    UNBURNED(0),
    IGNITED(1),
    DEVELOPING(2),
    EXTINGUISHING(3),
    BURNED(4);

    ForestStates(int value) {
        this.value = value;
    }

    private final int value;

    public int getValue() {
        return value;
    }
}
