package com.likehang.alphaforge.model.holding;

public enum HoldingFilter {
    OPEN,
    CLOSED,
    ALL;

    public boolean includes(HoldingStatus status) {
        return switch (this) {
            case OPEN -> status == HoldingStatus.OPEN;
            case CLOSED -> status == HoldingStatus.CLOSED;
            case ALL -> true;
        };
    }
}
