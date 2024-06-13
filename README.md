# AndroidTouch

从scrcpy中单独摘出来的touch层，通过命令行触发。使用方式为：

1.clone项目后，构建项目

2.将构建产物通过adb push到对应设备中，例如：

```
adb push <project build path> /data/local/tmp/AndroidTouch.jar 
```

3.执行jar文件：

```
adb shell CLASSPATH=/data/local/tmp/AndroidTouch.jar app_process / com.aoliaoaojiao.AndroidTouch.Run v2.2
```

执行完毕后，命令行中输出设备信息，则代表启动成功

4.输入touch指令，一般为：

```
[action] [x] [y] <fingerID>
```

| 参数名   | 类型   | 说明                                          |
| -------- | ------ | --------------------------------------------- |
| action   | string | 代表touch动作，固定为三种类型：down、up、move |
| x        | int    | 点击的x轴位置                                 |
| y        | int    | 点击的y轴位置                                 |
| fingerID | long   | 代表touch的手指，可以不指定                   |

具体示例：

```
down 500 600
move 500 700
move 600 700
up 600 700
# 指定手指(多手指模式)
down 500 500 0
down 500 600 1
move 600 500 0
move 500 700 1
up 600 500 0
up 500 700 1
```

