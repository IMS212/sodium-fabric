package net.caffeinemc.mods.sodium.client.vk.attribute;

public enum ScalarType {
    FLOAT(4),
    INT(4),
    UNSIGNED_INT(4),
    SHORT(2),
    UNSIGNED_SHORT(2),
    BYTE(1),
    UNSIGNED_BYTE(1);

    public final int bytes;

    ScalarType(int bytes) {
        this.bytes = bytes;
    }
}