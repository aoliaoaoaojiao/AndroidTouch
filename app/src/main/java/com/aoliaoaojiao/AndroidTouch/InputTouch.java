package com.aoliaoaojiao.AndroidTouch;

import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.aoliaoaojiao.AndroidTouch.wrappers.InputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class InputTouch {
    private static final int DEFAULT_DEVICE_ID = 0;
    private static final int POINTER_ID_MOUSE = -1;
    private static final int POINTER_ID_VIRTUAL_MOUSE = -3;
    private long lastTouchDown;
    private Device device;
    private SurfaceCapture surfaceCapture;

    private Scanner scanner;
    private final PointersState pointersState = new PointersState();
    private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[PointersState.MAX_POINTERS];
    private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[PointersState.MAX_POINTERS];


    public InputTouch() {
    }

    public InputTouch(Device device, SurfaceCapture surfaceCapture, Scanner scanner) {
        this.device = device;
        this.surfaceCapture = surfaceCapture;
        this.scanner = scanner;
        initPointers();
        try {
            this.surfaceCapture.init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleEvent(){
        while (!Thread.currentThread().isInterrupted()){
            String cmd = scanner.nextLine();
            List<String> params = new ArrayList<>();
            for (String param:cmd.split(" ")){
                if (!"".equals(param)){
                    params.add(param);
                }
            }
            if (params.size()>=3){
                int action = 0;
                switch (params.get(0)){
                    case "down":{
                        break;
                    }
                    case "move": {
                        action = 2;
                        break;
                    } case "up":{
                        action = 1;
                        break;
                    } default:{
                        Ln.e("unable to resolve command");
                    }
                }
                try {
                    int x = Integer.parseInt(params.get(1));
                    int y = Integer.parseInt(params.get(2));
                    Point point = new Point(x,y);
                    Position position = new Position(point,surfaceCapture.getSize());

                    long pointerId = 0;
                    if (params.size()==4){
                        pointerId = Long.parseLong(params.get(3));
                    }
                    float pressure = Binary.u16FixedPointToFloat((short) 0xffff);
                    int actionButton = 1;
                    int buttons = 1;

                    ControlMessage msg = ControlMessage.createInjectTouchEvent(action, pointerId, position, pressure, actionButton, buttons);

                    if (device.supportsInputEvents()) {
                        boolean result = injectTouch(msg.getAction(), msg.getPointerId(), msg.getPosition(), msg.getPressure(), msg.getActionButton(), msg.getButtons());
                        if (!result){
                            Ln.e("touch event fail");
                        }
                    }
                }catch (RuntimeException e){
                    Ln.e(e.getMessage());
                }
            }
        }
    }

    private boolean injectTouch(int action, long pointerId, Position position, float pressure, int actionButton, int buttons) {
        long now = SystemClock.uptimeMillis();

        Point point = device.getPhysicalPoint(position);
        if (point == null) {
            Ln.w("Ignore touch event, it was generated for a different device size");
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

        int source;
        if (pointerId == POINTER_ID_MOUSE || pointerId == POINTER_ID_VIRTUAL_MOUSE) {
            // real mouse event (forced by the client when --forward-on-click)
            pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_MOUSE;
            source = InputDevice.SOURCE_MOUSE;
            pointer.setUp(buttons == 0);
        } else {
            // POINTER_ID_GENERIC_FINGER, POINTER_ID_VIRTUAL_FINGER or real touch from device
            pointerProperties[pointerIndex].toolType = MotionEvent.TOOL_TYPE_FINGER;
            source = InputDevice.SOURCE_TOUCHSCREEN;
            // Buttons must not be set for touch events
            buttons = 0;
            pointer.setUp(action == MotionEvent.ACTION_UP);
        }

        int pointerCount = pointersState.update(pointerProperties, pointerCoords);
        if (pointerCount == 1) {
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchDown = now;
            }
        } else {
            // secondary pointers must use ACTION_POINTER_* ORed with the pointerIndex
            if (action == MotionEvent.ACTION_UP) {
                action = MotionEvent.ACTION_POINTER_UP | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            } else if (action == MotionEvent.ACTION_DOWN) {
                action = MotionEvent.ACTION_POINTER_DOWN | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }
        }

        /* If the input device is a mouse (on API >= 23):
         *   - the first button pressed must first generate ACTION_DOWN;
         *   - all button pressed (including the first one) must generate ACTION_BUTTON_PRESS;
         *   - all button released (including the last one) must generate ACTION_BUTTON_RELEASE;
         *   - the last button released must in addition generate ACTION_UP.
         *
         * Otherwise, Chrome does not work properly: <https://github.com/Genymobile/scrcpy/issues/3635>
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && source == InputDevice.SOURCE_MOUSE) {
            if (action == MotionEvent.ACTION_DOWN) {
                if (actionButton == buttons) {
                    // First button pressed: ACTION_DOWN
                    MotionEvent downEvent = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_DOWN, pointerCount, pointerProperties,
                            pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0);
                    if (!device.injectEvent(downEvent, Device.INJECT_MODE_ASYNC)) {
                        return false;
                    }
                }

                // Any button pressed: ACTION_BUTTON_PRESS
                MotionEvent pressEvent = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_BUTTON_PRESS, pointerCount, pointerProperties,
                        pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0);
                if (!InputManager.setActionButton(pressEvent, actionButton)) {
                    return false;
                }
                if (!device.injectEvent(pressEvent, Device.INJECT_MODE_ASYNC)) {
                    return false;
                }

                return true;
            }

            if (action == MotionEvent.ACTION_UP) {
                // Any button released: ACTION_BUTTON_RELEASE
                MotionEvent releaseEvent = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_BUTTON_RELEASE, pointerCount, pointerProperties,
                        pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0);
                if (!InputManager.setActionButton(releaseEvent, actionButton)) {
                    return false;
                }
                if (!device.injectEvent(releaseEvent, Device.INJECT_MODE_ASYNC)) {
                    return false;
                }

                if (buttons == 0) {
                    // Last button released: ACTION_UP
                    MotionEvent upEvent = MotionEvent.obtain(lastTouchDown, now, MotionEvent.ACTION_UP, pointerCount, pointerProperties,
                            pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source, 0);
                    if (!device.injectEvent(upEvent, Device.INJECT_MODE_ASYNC)) {
                        return false;
                    }
                }

                return true;
            }
        }

        MotionEvent event = MotionEvent
                .obtain(lastTouchDown, now, action, pointerCount, pointerProperties, pointerCoords, 0, buttons, 1f, 1f, DEFAULT_DEVICE_ID, 0, source,
                        0);
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
}
