package com.aoliaoaojiao.AndroidTouch;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

/**
 * 简化的触控输入处理类，专注于核心触控功能
 * 不依赖ScreenCapture，直接通过Device获取屏幕信息
 */
public class InputTouch implements Device.RotationListener {
    private static final int DEFAULT_DEVICE_ID = 0;
    private static final int POINTER_ID_MOUSE = -1;
    private static final int POINTER_ID_VIRTUAL_MOUSE = -3;
    
    private final Device device;
    private final Scanner scanner;
    private Size currentScreenSize;
    private long lastTouchDown;
    
    private final PointersState pointersState = new PointersState();
    private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[PointersState.MAX_POINTERS];
    private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[PointersState.MAX_POINTERS];
    private final HashMap<Long, Point> pointerIdLastPoint = new HashMap<>();

    public InputTouch(Device device, Scanner scanner) {
        this.device = device;
        this.scanner = scanner;
        this.currentScreenSize = device.getScreenInfo().getVideoSize();
        device.setRotationListener(this);
        initPointers();
    }

    /**
     * 处理触控命令的主循环
     * 支持命令格式：
     * - airtest down x y [pointerId] - 相对坐标按下
     * - airtest move x y [pointerId] - 相对坐标移动
     * - airtest up [pointerId] - 相对坐标释放
     * - touch down x y [pointerId] - 绝对坐标按下
     * - touch move x y [pointerId] - 绝对坐标移动
     * - touch up [pointerId] - 绝对坐标释放
     */
    public void handleEvent() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String cmd = scanner.nextLine();
                if (cmd == null || cmd.trim().isEmpty()) {
                    continue;
                }
                
                processTouchCommand(cmd);
            } catch (Exception e) {
                Ln.e("Error processing touch command: " + e.getMessage());
            }
        }
    }

    private void processTouchCommand(String cmd) {
        List<String> params = new ArrayList<>();
        for (String param : cmd.split(" ")) {
            if (!param.isEmpty()) {
                params.add(param);
            }
        }

        if (params.size() < 2) {
            Ln.e("Invalid command format: " + cmd);
            return;
        }

        String commandType = params.get(0);
        String action = params.get(1);
        
        if (!("airtest".equals(commandType) || "touch".equals(commandType))) {
            Ln.e("Unknown command type: " + commandType);
            return;
        }

        int x = 0, y = 0;
        long pointerId = 0;
        int motionAction = 0;

        switch (action) {
            case "down":
                if (params.size() < 4) {
                    Ln.e("Invalid down command: " + cmd);
                    return;
                }
                motionAction = MotionEvent.ACTION_DOWN;
                x = parseCoordinate(params.get(2), commandType, true);
                y = parseCoordinate(params.get(3), commandType, false);
                if (params.size() >= 5) {
                    pointerId = Long.parseLong(params.get(4));
                }
                break;
                
            case "move":
                if (params.size() < 4) {
                    Ln.e("Invalid move command: " + cmd);
                    return;
                }
                motionAction = MotionEvent.ACTION_MOVE;
                x = parseCoordinate(params.get(2), commandType, true);
                y = parseCoordinate(params.get(3), commandType, false);
                if (params.size() >= 5) {
                    pointerId = Long.parseLong(params.get(4));
                }
                break;
                
            case "up":
                motionAction = MotionEvent.ACTION_UP;
                if (params.size() >= 3) {
                    pointerId = Long.parseLong(params.get(2));
                }
                
                // 获取上次的位置
                if (pointerIdLastPoint.containsKey(pointerId)) {
                    Point lastPoint = pointerIdLastPoint.get(pointerId);
                    x = lastPoint.getX();
                    y = lastPoint.getY();
                }
                break;
                
            default:
                Ln.e("Unknown action: " + action);
                return;
        }

        // 更新指针位置记录
        Point point = new Point(x, y);
        if (!"up".equals(action)) {
            pointerIdLastPoint.put(pointerId, point);
        } else {
            pointerIdLastPoint.remove(pointerId);
        }

        // 创建位置对象
        Position position = new Position(point, currentScreenSize);
        
        // 设置默认参数
        float pressure = 1.0f;
        int actionButton = 1;
        int buttons = 1;

        // 注入触控事件
        if (device.supportsInputEvents()) {
            boolean result = injectTouch(motionAction, pointerId, position, pressure, actionButton, buttons);
            if (result) {
                Ln.i("Touch event succeeded: " + action + " pointerId=" + pointerId + " x=" + x + " y=" + y);
            } else {
                Ln.e("Touch event failed: " + action + " pointerId=" + pointerId);
            }
        }
    }

    private int parseCoordinate(String coord, String commandType, boolean isX) {
        if ("airtest".equals(commandType)) {
            // 相对坐标，需要转换为绝对坐标
            double relative = Double.parseDouble(coord);
            int screenSize = isX ? currentScreenSize.getWidth() : currentScreenSize.getHeight();
            return (int) (relative * screenSize);
        } else {
            // 绝对坐标
            return Integer.parseInt(coord);
        }
    }

    private boolean injectTouch(int action, long pointerId, Position position, float pressure, int actionButton, int buttons) {
        long now = SystemClock.uptimeMillis();

        Point point = device.getPhysicalPoint(position);
        if (point == null) {
            Ln.w("Ignore touch event, invalid position");
            return false;
        }

        int pointerIndex = pointersState.getPointerIndex(pointerId);
        if (pointerIndex == -1) {
            Ln.w("Too many pointers for touch event");
            return false;
        }
        
        Pointer pointer = pointersState.get(pointerIndex);
        pointer.setPoint(point);
        pointer.setPressure(pressure);

        // 设置指针属性
        if (pointerId == POINTER_ID_MOUSE || pointerId == POINTER_ID_VIRTUAL_MOUSE) {
            pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_MOUSE;
            pointer.setUp(buttons == 0);
        } else {
            pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_FINGER;
            buttons = 0; // 触控事件不需要buttons
            pointer.setUp(action == MotionEvent.ACTION_UP);
        }

        int pointerCount = pointersState.update(pointerProperties, pointerCoords);
        
        // 处理多点触控动作
        if (pointerCount > 1) {
            if (action == MotionEvent.ACTION_UP) {
                action = MotionEvent.ACTION_POINTER_UP | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            } else if (action == MotionEvent.ACTION_DOWN) {
                action = MotionEvent.ACTION_POINTER_DOWN | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }
        } else if (action == MotionEvent.ACTION_DOWN) {
            lastTouchDown = now;
        }

        // 创建并注入MotionEvent
        MotionEvent event = MotionEvent.obtain(
            lastTouchDown, now, action, pointerCount, 
            pointerProperties, pointerCoords, 0, buttons, 
            1f, 1f, DEFAULT_DEVICE_ID, 0, 
            InputDevice.SOURCE_TOUCHSCREEN, 0
        );
        
        return device.injectEvent(event, Device.INJECT_MODE_ASYNC);
    }

    private void initPointers() {
        for (int i = 0; i < PointersState.MAX_POINTERS; ++i) {
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;

            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.orientation = 0;
            coords.size = 0;

            pointerProperties[i] = props;
            pointerCoords[i] = coords;
        }
    }

    @Override
    public void onRotationChanged(int rotation) {
        // 屏幕旋转时更新尺寸信息
        this.currentScreenSize = device.getScreenInfo().getVideoSize();
        Ln.i("Screen rotation changed to " + rotation + 
             ", new size: " + currentScreenSize.getWidth() + "x" + currentScreenSize.getHeight());
    }
}