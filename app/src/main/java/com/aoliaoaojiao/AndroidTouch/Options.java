package com.aoliaoaojiao.AndroidTouch;

import android.graphics.Rect;

import java.util.List;
import java.util.Locale;

public class Options {

    private Ln.Level logLevel = Ln.Level.DEBUG;
    private int maxSize;
    private int lockVideoOrientation = -1;
    private Rect crop;
    private boolean control = true;
    private int displayId;
    private boolean clipboardAutosync = true;
    public Ln.Level getLogLevel() {
        return logLevel;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getLockVideoOrientation() {
        return lockVideoOrientation;
    }

    public Rect getCrop() {
        return crop;
    }

    public boolean getControl() {
        return control;
    }

    public int getDisplayId() {
        return displayId;
    }

    public boolean getClipboardAutosync() {
        return clipboardAutosync;
    }


    @SuppressWarnings("MethodLength")
    public static Options parse(String... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing client version");
        }

        String clientVersion = args[0];
        if (!clientVersion.equals(BuildConfig.VERSION_NAME)) {
            throw new IllegalArgumentException(
                    "The server version (" + BuildConfig.VERSION_NAME + ") does not match the client " + "(" + clientVersion + ")");
        }

        Options options = new Options();

        for (int i = 1; i < args.length; ++i) {
            String arg = args[i];
            int equalIndex = arg.indexOf('=');
            if (equalIndex == -1) {
                throw new IllegalArgumentException("Invalid key=value pair: \"" + arg + "\"");
            }
            String key = arg.substring(0, equalIndex);
            String value = arg.substring(equalIndex + 1);
            switch (key) {
                case "log_level":
                    options.logLevel = Ln.Level.valueOf(value.toUpperCase(Locale.ENGLISH));
                    break;
                case "crop":
                    if (!value.isEmpty()) {
                        options.crop = parseCrop(value);
                    }
                    break;
                case "display_id":
                    options.displayId = Integer.parseInt(value);
                    break;
                case "clipboard_autosync":
                    options.clipboardAutosync = Boolean.parseBoolean(value);
                    break;
                default:
                    Ln.w("Unknown server option: " + key);
                    break;
            }
        }

        return options;
    }

    private static Rect parseCrop(String crop) {
        // input format: "width:height:x:y"
        String[] tokens = crop.split(":");
        if (tokens.length != 4) {
            throw new IllegalArgumentException("Crop must contains 4 values separated by colons: \"" + crop + "\"");
        }
        int width = Integer.parseInt(tokens[0]);
        int height = Integer.parseInt(tokens[1]);
        int x = Integer.parseInt(tokens[2]);
        int y = Integer.parseInt(tokens[3]);
        return new Rect(x, y, x + width, y + height);
    }

}
