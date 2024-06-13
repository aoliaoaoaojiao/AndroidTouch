package com.aoliaoaojiao.AndroidTouch;

import android.os.Build;

import java.util.Scanner;

public final class Run {

    private Run() {
        // not instantiable
    }

    private static void runTouch(Options options) throws ConfigurationException {
        final Device device = new Device(options);

        try {
            Scanner scanner = new Scanner(System.in);
            SurfaceCapture surfaceCapture;
            surfaceCapture = new ScreenCapture(device);
            InputTouch inputTouch = new InputTouch(device,surfaceCapture,scanner);
            inputTouch.handleEvent();
        } catch (RuntimeException e){
            Ln.e(e.getMessage());
        }
    }

    public static void main(String... args) {
        int status = 0;
        try {
            internalMain(args);
        } catch (Throwable t) {
            Ln.e(t.getMessage(), t);
            status = 1;
        } finally {
            // By default, the Java process exits when all non-daemon threads are terminated.
            // The Android SDK might start some non-daemon threads internally, preventing the scrcpy server to exit.
            // So force the process to exit explicitly.
            System.exit(status);
        }
    }

    private static void internalMain(String... args) throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Ln.e("Exception on thread " + t, e);
        });

        Options options = Options.parse(args);

        Ln.disableSystemStreams();
        Ln.initLogLevel(options.getLogLevel());

        Ln.i("Device: [" + Build.MANUFACTURER + "] " + Build.BRAND + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");

        try {
            runTouch(options);
        } catch (Exception e) {
            Ln.e("error", e);
            // Do not print stack trace, a user-friendly error-message has already been logged
        }
    }
}
