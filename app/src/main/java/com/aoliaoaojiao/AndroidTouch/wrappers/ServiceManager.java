package com.aoliaoaojiao.AndroidTouch.wrappers;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;

/**
 * 简化的服务管理器，专注于触控功能必需的服务
 */
@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class ServiceManager {

    private static final Method GET_SERVICE_METHOD;
    static {
        try {
            GET_SERVICE_METHOD = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static WindowManager windowManager;
    private static DisplayManager displayManager;
    private static InputManager inputManager;

    private ServiceManager() {
        /* 不可实例化 */
    }

    private static IInterface getService(String service, String type) {
        try {
            IBinder binder = (IBinder) GET_SERVICE_METHOD.invoke(null, service);
            Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
            return (IInterface) asInterfaceMethod.invoke(null, binder);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static WindowManager getWindowManager() {
        if (windowManager == null) {
            windowManager = new WindowManager(getService("window", "android.view.IWindowManager"));
        }
        return windowManager;
    }

    public static DisplayManager getDisplayManager() {
        if (displayManager == null) {
            try {
                Class<?> clazz = Class.forName("android.hardware.display.DisplayManagerGlobal");
                Method getInstanceMethod = clazz.getDeclaredMethod("getInstance");
                Object dmg = getInstanceMethod.invoke(null);
                displayManager = new DisplayManager(dmg);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        return displayManager;
    }

    public static Class<?> getInputManagerClass() {
        try {
            return Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException e) {
            return android.hardware.input.InputManager.class;
        }
    }

    public static InputManager getInputManager() {
        if (inputManager == null) {
            try {
                Class<?> inputManagerClass = getInputManagerClass();
                Method getInstanceMethod = inputManagerClass.getDeclaredMethod("getInstance");
                Object im = getInstanceMethod.invoke(null);
                inputManager = new InputManager(im);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        return inputManager;
    }
}