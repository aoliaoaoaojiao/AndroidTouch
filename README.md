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
[model] [action] <x> <y> <fingerID>
```

| 参数名   | 类型   | 说明                                          |
| -------- | ------ | --------------------------------------------- |
| model    | string | 使用的模式，当前有两种，分别是touch和airtest  |
| action   | string | 代表touch动作，固定为三种类型：down、up、move |
| x        | int    | 点击的x轴位置，当action为up时，不需要输入     |
| y        | int    | 点击的y轴位置，当action为up时，不需要输入     |
| fingerID | long   | 代表touch的手指，可以不指定                   |

**touch模式使用示例（注意，坐标原点始终为屏幕的左上角）**：

```
touch down 500 600 # 执行成功后命令行会回显succeed
touch move 500 700
touch move 600 700
touch up
# 指定手指(多手指模式)
touch down 500 500 0
touch down 500 600 1
touch move 600 500 0
touch move 500 700 1
touch up 0
touch up 1
```

**airtest模式使用示例（注意，坐标原点始终为屏幕的左上角，和airtest官方的坐标系统保持一致）：**

```
airtest down 0.5 0.5
airtest move 0.5 0.7
airtest move 0.5 0.8
airtest up
# 指定手指(多手指模式)
airtest down 0.5 0.5 0
airtest down 0.6 0.6 1
airtest move 0.4 0.5 0
airtest move 0.7 0.6 1
airtest up 0
airtest up 1
```

