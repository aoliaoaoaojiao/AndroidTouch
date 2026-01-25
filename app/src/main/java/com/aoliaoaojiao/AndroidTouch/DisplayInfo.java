package com.aoliaoaojiao.AndroidTouch;

/**
 * 显示信息类，用于存储显示相关数据
 */
public final class DisplayInfo {
    public static final int FLAG_SUPPORTS_PROTECTED_BUFFERS = 0x00000001;

    private final int displayId;
    private final Size size;
    private final int rotation;
    private final int layerStack;
    private final int flags;

    public DisplayInfo(int displayId, Size size, int rotation, int layerStack, int flags) {
        this.displayId = displayId;
        this.size = size;
        this.rotation = rotation;
        this.layerStack = layerStack;
        this.flags = flags;
    }

    public int getDisplayId() {
        return displayId;
    }

    public Size getSize() {
        return size;
    }

    public int getRotation() {
        return rotation;
    }

    public int getLayerStack() {
        return layerStack;
    }

    public int getFlags() {
        return flags;
    }
}