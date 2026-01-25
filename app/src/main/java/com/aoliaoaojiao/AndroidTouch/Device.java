package com.aoliaoaojiao.AndroidTouch;

import com.aoliaoaojiao.AndroidTouch.wrappers.InputManager;
import com.aoliaoaojiao.AndroidTouch.wrappers.ServiceManager;

import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.view.IRotationWatcher;
import android.view.IDisplayFoldListener;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

/**
 * 简化的设备类，专注于触控功能
 */
public final class Device {

    public static final int INJECT_MODE_ASYNC = InputManager.INJECT_INPUT_EVENT_MODE_ASYNC;
    public static final int INJECT_MODE_WAIT_FOR_RESULT = InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT;
    public static final int INJECT_MODE_WAIT_FOR_FINISH = InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH;

    public static final int LOCK_VIDEO_ORIENTATION_UNLOCKED = -1;
    public static final int LOCK_VIDEO_ORIENTATION_INITIAL = -2;

    public interface RotationListener {
        void onRotationChanged(int rotation);
    }

    public interface FoldListener {
        void onFoldChanged(int displayId, boolean folded);
    }

    private final Size deviceSize;
    private final Rect crop;
    private int maxSize;
    private final int lockVideoOrientation;

    private ScreenInfo screenInfo;
    private RotationListener rotationListener;
    private FoldListener foldListener;

    /**
     * 逻辑显示标识符
     */
    private final int displayId;

    /**
     * 与此逻辑显示关联的surface flinger层堆栈
     */
    private final int layerStack;

    private final boolean supportsInputEvents;

    public Device(Options options) throws ConfigurationException {
        displayId = options.getDisplayId();
        DisplayInfo displayInfo = ServiceManager.getDisplayManager().getDisplayInfo(displayId);
        if (displayInfo == null) {
            Ln.e("Display " + displayId + " not found");
            throw new ConfigurationException("Unknown display id: " + displayId);
        }

        int displayInfoFlags = displayInfo.getFlags();

        deviceSize = displayInfo.getSize();
        crop = options.getCrop();
        maxSize = options.getMaxSize();
        lockVideoOrientation = options.getLockVideoOrientation();

        screenInfo = ScreenInfo.computeScreenInfo(displayInfo.getRotation(), deviceSize, crop, maxSize, lockVideoOrientation);
        layerStack = displayInfo.getLayerStack();

        ServiceManager.getWindowManager().registerRotationWatcher(new IRotationWatcher.Stub() {
            @Override
            public void onRotationChanged(int rotation) {
                synchronized (Device.this) {
                    screenInfo = screenInfo.withDeviceRotation(rotation);

                    // 通知监听器
                    if (rotationListener != null) {
                        rotationListener.onRotationChanged(rotation);
                    }
                }
            }
        }, displayId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceManager.getWindowManager().registerDisplayFoldListener(new IDisplayFoldListener.Stub() {
                @Override
                public void onDisplayFoldChanged(int displayId, boolean folded) {
                    if (Device.this.displayId != displayId) {
                        // 忽略与其他显示相关的事件
                        return;
                    }

                    synchronized (Device.this) {
                        DisplayInfo displayInfo = ServiceManager.getDisplayManager().getDisplayInfo(displayId);
                        if (displayInfo == null) {
                            Ln.e("Display " + displayId + " not found");
                            return;
                        }

                        screenInfo = ScreenInfo.computeScreenInfo(displayInfo.getRotation(), displayInfo.getSize(), options.getCrop(),
                                options.getMaxSize(), options.getLockVideoOrientation());
                        // 通知监听器
                        if (foldListener != null) {
                            foldListener.onFoldChanged(displayId, folded);
                        }
                    }
                }
            });
        }

        if ((displayInfoFlags & DisplayInfo.FLAG_SUPPORTS_PROTECTED_BUFFERS) == 0) {
            Ln.w("Display doesn't have FLAG_SUPPORTS_PROTECTED_BUFFERS flag, mirroring can be restricted");
        }

        // 主显示或Android >= Q上的任何显示
        supportsInputEvents = displayId == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        if (!supportsInputEvents) {
            Ln.w("Input events are not supported for secondary displays before Android 10");
        }
    }

    public synchronized void setMaxSize(int newMaxSize) {
        maxSize = newMaxSize;
        screenInfo = ScreenInfo.computeScreenInfo(screenInfo.getReverseVideoRotation(), deviceSize, crop, newMaxSize, lockVideoOrientation);
    }

    public synchronized ScreenInfo getScreenInfo() {
        return screenInfo;
    }

    public int getLayerStack() {
        return layerStack;
    }

    public Point getPhysicalPoint(Position position) {
        // 故意隐藏字段，用锁读取
        @SuppressWarnings("checkstyle:HiddenField")
        ScreenInfo screenInfo = getScreenInfo(); // 同步读取

        // 忽略锁定的视频方向，事件将应用于物理设备方向坐标
        Size unlockedVideoSize = screenInfo.getUnlockedVideoSize();

        int reverseVideoRotation = screenInfo.getReverseVideoRotation();
        // 反转视频旋转以应用事件
        Position devicePosition = position.rotate(reverseVideoRotation);

        Size clientVideoSize = devicePosition.getScreenSize();
        if (!unlockedVideoSize.equals(clientVideoSize)) {
            // 客户端发送了相对于错误尺寸视频的点击，
            // 设备可能自事件生成以来已旋转，因此忽略该事件
            return null;
        }
        Rect contentRect = screenInfo.getContentRect();
        Point point = devicePosition.getPoint();
        int convertedX = contentRect.left + point.getX() * contentRect.width() / unlockedVideoSize.getWidth();
        int convertedY = contentRect.top + point.getY() * contentRect.height() / unlockedVideoSize.getHeight();
        return new Point(convertedX, convertedY);
    }

    public static String getDeviceName() {
        return Build.MODEL;
    }

    public static boolean supportsInputEvents(int displayId) {
        return displayId == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public boolean supportsInputEvents() {
        return supportsInputEvents;
    }

    public static boolean injectEvent(InputEvent inputEvent, int displayId, int injectMode) {
        if (!supportsInputEvents(displayId)) {
            throw new AssertionError("Could not inject input event if !supportsInputEvents()");
        }

        if (displayId != 0 && !InputManager.setDisplayId(inputEvent, displayId)) {
            return false;
        }

        return ServiceManager.getInputManager().injectInputEvent(inputEvent, injectMode);
    }

    public boolean injectEvent(InputEvent event, int injectMode) {
        return injectEvent(event, displayId, injectMode);
    }

    public static boolean injectKeyEvent(int action, int keyCode, int repeat, int metaState, int displayId, int injectMode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, action, keyCode, repeat, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        return injectEvent(event, displayId, injectMode);
    }

    public boolean injectKeyEvent(int action, int keyCode, int repeat, int metaState, int injectMode) {
        return injectKeyEvent(action, keyCode, repeat, metaState, displayId, injectMode);
    }

    public static boolean pressReleaseKeycode(int keyCode, int displayId, int injectMode) {
        return injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0, 0, displayId, injectMode)
                && injectKeyEvent(KeyEvent.ACTION_UP, keyCode, 0, 0, displayId, injectMode);
    }

    public boolean pressReleaseKeycode(int keyCode, int injectMode) {
        return pressReleaseKeycode(keyCode, displayId, injectMode);
    }

    public synchronized void setRotationListener(RotationListener rotationListener) {
        this.rotationListener = rotationListener;
    }

    public synchronized void setFoldListener(FoldListener foldlistener) {
        this.foldListener = foldlistener;
    }

    /**
     * 简化版的ScreenInfo内部类
     */
    public static final class ScreenInfo {
        private final Rect contentRect;
        private final Size unlockedVideoSize;
        private final int deviceRotation;
        private final int lockedVideoOrientation;

        public ScreenInfo(Rect contentRect, Size unlockedVideoSize, int deviceRotation, int lockedVideoOrientation) {
            this.contentRect = contentRect;
            this.unlockedVideoSize = unlockedVideoSize;
            this.deviceRotation = deviceRotation;
            this.lockedVideoOrientation = lockedVideoOrientation;
        }

        public Rect getContentRect() {
            return contentRect;
        }

        public Size getUnlockedVideoSize() {
            return unlockedVideoSize;
        }

        public Size getVideoSize() {
            if (getVideoRotation() % 2 == 0) {
                return unlockedVideoSize;
            }
            return unlockedVideoSize.rotate();
        }

        public int getDeviceRotation() {
            return deviceRotation;
        }

        public ScreenInfo withDeviceRotation(int newDeviceRotation) {
            if (newDeviceRotation == deviceRotation) {
                return this;
            }
            boolean orientationChanged = (deviceRotation + newDeviceRotation) % 2 != 0;
            Rect newContentRect;
            Size newUnlockedVideoSize;
            if (orientationChanged) {
                newContentRect = flipRect(contentRect);
                newUnlockedVideoSize = unlockedVideoSize.rotate();
            } else {
                newContentRect = contentRect;
                newUnlockedVideoSize = unlockedVideoSize;
            }
            return new ScreenInfo(newContentRect, newUnlockedVideoSize, newDeviceRotation, lockedVideoOrientation);
        }

        public static ScreenInfo computeScreenInfo(int rotation, Size deviceSize, Rect crop, int maxSize, int lockedVideoOrientation) {
            if (lockedVideoOrientation == Device.LOCK_VIDEO_ORIENTATION_INITIAL) {
                lockedVideoOrientation = rotation;
            }

            Rect contentRect = new Rect(0, 0, deviceSize.getWidth(), deviceSize.getHeight());
            if (crop != null) {
                if (rotation % 2 != 0) {
                    crop = flipRect(crop);
                }
                if (!contentRect.intersect(crop)) {
                    Ln.w("Crop rectangle does not intersect device screen");
                    contentRect = new Rect();
                }
            }

            Size videoSize = computeVideoSize(contentRect.width(), contentRect.height(), maxSize);
            return new ScreenInfo(contentRect, videoSize, rotation, lockedVideoOrientation);
        }

        private static Size computeVideoSize(int w, int h, int maxSize) {
            w &= ~7;
            h &= ~7;
            if (maxSize > 0) {
                boolean portrait = h > w;
                int major = portrait ? h : w;
                int minor = portrait ? w : h;
                if (major > maxSize) {
                    int minorExact = minor * maxSize / major;
                    minor = (minorExact + 4) & ~7;
                    major = maxSize;
                }
                w = portrait ? minor : major;
                h = portrait ? major : minor;
            }
            return new Size(w, h);
        }

        private static Rect flipRect(Rect crop) {
            return new Rect(crop.top, crop.left, crop.bottom, crop.right);
        }

        public int getVideoRotation() {
            if (lockedVideoOrientation == -1) {
                return 0;
            }
            return (deviceRotation + 4 - lockedVideoOrientation) % 4;
        }

        public int getReverseVideoRotation() {
            if (lockedVideoOrientation == -1) {
                return 0;
            }
            return (lockedVideoOrientation + 4 - deviceRotation) % 4;
        }
    }
}