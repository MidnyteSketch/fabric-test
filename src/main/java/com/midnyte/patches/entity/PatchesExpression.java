package com.midnyte.patches.entity;

public enum PatchesExpression {
    DEFAULT(0),
    SURPRISED(1),
    LAUGH(2),
    MOUTH_OPEN(3),
    HURT(4),
    RESTING(5);

    private final int id;

    PatchesExpression(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static PatchesExpression fromId(int id) {
        for (PatchesExpression expression : values()) {
            if (expression.id == id) {
                return expression;
            }
        }
        return DEFAULT;
    }
}
