package com.aoliaoaojiao.AndroidTouch;

import android.os.Build;

import java.util.Scanner;

/**
 * 简化的触控应用主类
 * 专注于触控操作核心功能
 */
public final class Run {

    private Run() {
        // 不可实例化
    }

    /**
     * 运行触控功能
     */
    private static void runTouch(Options options) throws ConfigurationException {
        final Device device = new Device(options);

        try {
            Scanner scanner = new Scanner(System.in);
            InputTouch inputTouch = new InputTouch(device, scanner);
            
            Ln.i("触控系统已启动");
            Ln.i("支持的命令格式:");
            Ln.i("  airtest down x y [pointerId]  - 相对坐标按下 (x,y: 0.0-1.0)");
            Ln.i("  airtest move x y [pointerId]  - 相对坐标移动 (x,y: 0.0-1.0)");
            Ln.i("  airtest up [pointerId]          - 相对坐标释放");
            Ln.i("  touch down x y [pointerId]      - 绝对坐标按下 (x,y: 像素坐标)");
            Ln.i("  touch move x y [pointerId]      - 绝对坐标移动 (x,y: 像素坐标)");
            Ln.i("  touch up [pointerId]            - 绝对坐标释放");
            Ln.i("屏幕尺寸: " + device.getScreenInfo().getVideoSize().getWidth() + 
                  "x" + device.getScreenInfo().getVideoSize().getHeight());
            
            inputTouch.handleEvent();
        } catch (RuntimeException e) {
            Ln.e("运行时错误: " + e.getMessage());
            throw e;
        }
    }

    public static void main(String... args) {
        int status = 0;
        try {
            internalMain(args);
        } catch (Throwable t) {
            Ln.e("应用错误: " + t.getMessage(), t);
            status = 1;
        } finally {
            // 强制退出，确保所有线程都被终止
            System.exit(status);
        }
    }

    private static void internalMain(String... args) throws Exception {
        // 设置异常处理器
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Ln.e("线程 " + t + " 异常: " + e.getMessage(), e);
        });

        // 解析命令行参数
        Options options = Options.parse(args);

        // 初始化日志
        Ln.disableSystemStreams();
        Ln.initLogLevel(options.getLogLevel());

        // 输出设备信息
        Ln.i("设备: [" + Build.MANUFACTURER + "] " + Build.BRAND + " " + 
             Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");

        // 启动触控功能
        runTouch(options);
    }
}