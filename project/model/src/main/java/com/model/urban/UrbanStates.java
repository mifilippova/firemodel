package com.model.urban;

public enum UrbanStates {
    UNBURNED(0),
    IGNITED(1),
    SLOWDEVELOPING(2),
    FULLDEVELOPMENT(3),
    FLASHOVER(4),
    EXTINGUISHED(5);

    UrbanStates(int value) {
        this.value = value;
    }

    private final int value;

    public int getValue() {
        return value;
    }
}
