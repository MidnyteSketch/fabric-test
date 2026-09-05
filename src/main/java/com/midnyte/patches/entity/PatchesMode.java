package com.midnyte.patches.entity;

public enum PatchesMode {
    WANDERING(0),
    FOLLOWING(1),
    SITTING(2);

    private final int id;

    PatchesMode(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static PatchesMode fromId(int id) {
        for (PatchesMode mode : values()) {
            if (mode.id == id) return mode;
        }
        return WANDERING;
    }
}
