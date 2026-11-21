package com.geofiber.geofiber_backend.enums;

public enum SplitterRatioEnum {
    RATIO_1_2(2),
    RATIO_1_4(4),
    RATIO_1_8(8),
    RATIO_1_16(16),
    RATIO_1_32(32),
    RATIO_1_64(64);

    private final int outputCount;

    SplitterRatioEnum(int outputCount) {
        this.outputCount = outputCount;
    }

    public int getOutputCount() {
        return outputCount;
    }
}
