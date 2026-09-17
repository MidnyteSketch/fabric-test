package com.midnyte.patches.entity.ai;

/** Broad significance bands used by Patches' shared curiosity controller. */
public enum PatchesCuriosityPriority {
    LOW,
    MEDIUM,
    HIGH;

    public boolean outranks(PatchesCuriosityPriority other) {
        return this.ordinal() > other.ordinal();
    }
}
