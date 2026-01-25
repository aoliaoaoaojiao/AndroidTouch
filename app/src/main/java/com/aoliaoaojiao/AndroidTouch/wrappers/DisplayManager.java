package com.aoliaoaojiao.AndroidTouch.wrappers;

import com.aoliaoaojiao.AndroidTouch.Ln;
import com.aoliaoaojiao.AndroidTouch.Size;
import com.aoliaoaojiao.AndroidTouch.DisplayInfo;

/**
 * 显示管理器包装类，提供显示信息
 */
public final class DisplayManager {

    private final Object manager;

    public DisplayManager(Object manager) {
        this.manager = manager;
    }

    public DisplayInfo getDisplayInfo(int displayId) {
        try {
            Object displayInfo = manager.getClass().getMethod("getDisplayInfo", int.class).invoke(manager, displayId);
            if (displayInfo == null) {
                return null;
            }
            
            Class<?> cls = displayInfo.getClass();
            // width and height already take the rotation into account
            int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            int rotation = cls.getDeclaredField("rotation").getInt(displayInfo);
            int layerStack = cls.getDeclaredField("layerStack").getInt(displayInfo);
            int flags = cls.getDeclaredField("flags").getInt(displayInfo);
            
            return new DisplayInfo(displayId, new Size(width, height), rotation, layerStack, flags);
        } catch (Exception e) {
            Ln.e("Could not get display info", e);
            return null;
        }
    }
}