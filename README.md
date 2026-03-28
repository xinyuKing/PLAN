# 计划提醒

一个基于原生 Android 技术栈实现的本地计划提醒应用。  
它支持手动创建计划，也支持先语音输入、再确认保存的录入方式，适合记录日程、训练安排、待办事项和短期提醒。

## 项目定位

这个项目的目标很明确：

- 不依赖后端服务，计划数据直接保存在本地
- 录入方式尽量轻量，既能手填，也能先说再确认
- 提醒触发要贴近真实使用场景，支持提前提醒、声音、震动和重启恢复
- 页面交互尽量适合移动端，减少重复输入和繁琐操作

## 当前功能

### 计划管理

- 新增计划、编辑计划、删除计划
- 地点为可选项，不填写地点也可以直接保存
- 每条计划都可以单独设置提前提醒分钟数
- 每条计划都可以单独设置是否开启闹钟提示音
- 每条计划都可以单独设置是否开启震动提醒

### 开始时间录入

- 新增计划弹窗支持滚动表单
- 开始时间区域放在表单最上方
- 时间选择改为 `年 / 月 / 日 / 时 / 分` 滚轮形式
- 日期和时间拆成两排滚轮，避免分钟被挤出可视区域
- 弹窗右侧带蓝色滚动指示条，方便识别当前区域仍可继续下滑

### 提醒能力

- 使用 `AlarmManager` 触发本地提醒
- 提醒时间点是“计划开始前 N 分钟”
- 如果计划距离开始太近，会尽快触发提醒，而不是直接跳过
- 支持以下提醒组合：
  - 静默
  - 仅震动
  - 仅闹钟声
  - 闹钟声 + 震动
- 设备重启、应用更新、精确提醒权限变化后，会重新恢复未来计划的提醒

### 状态展示

计划卡片会根据当前时间显示三种状态，并使用不同颜色区分：

- 未到提前提醒时间
- 已进入提前提醒时间到计划开始之间的时间段
- 已过计划时间

### 语音助手

语音录入采用“先说内容，再确认”的流程：

1. 打开语音计划助手
2. 直接说出计划内容
3. 应用实时识别语音
4. 模型将语音整理成预填充草稿
5. 弹出确认弹窗，提供三个操作：
   - `继续添加语音`
   - `手动调整`
   - `直接确认`

语音编辑已有计划时，会保留当前计划的提醒分钟数、闹钟声和震动设置。

### 多语言

应用设置中支持切换以下语言：

- 简体中文
- 繁体中文
- 英文

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- AlarmManager
- BroadcastReceiver
- 本地通知
- DashScope 实时语音识别
- Qwen 对话式计划整理

## 项目结构

### 入口与页面

- `app/src/main/java/com/example/planreminder/MainActivity.kt`
  - 应用入口
  - 权限申请
  - 语音录音与页面交互衔接

- `app/src/main/java/com/example/planreminder/ui/PlanReminderScreen.kt`
  - 计划列表页面
  - 新增与编辑弹窗
  - 语音对话弹窗
  - 语音草稿确认弹窗

### 状态与业务

- `app/src/main/java/com/example/planreminder/PlanViewModel.kt`
  - 计划保存与删除
  - 页面状态管理
  - 语音助手状态流转
  - 提醒重排与恢复协调

### 数据层

- `app/src/main/java/com/example/planreminder/data/PlanItem.kt`
  - 计划实体

- `app/src/main/java/com/example/planreminder/data/PlanDao.kt`
  - Room 查询接口

- `app/src/main/java/com/example/planreminder/data/PlanDatabase.kt`
  - Room 数据库与迁移

- `app/src/main/java/com/example/planreminder/data/PlanRepository.kt`
  - 数据访问封装

### 提醒层

- `app/src/main/java/com/example/planreminder/reminder/ReminderScheduler.kt`
  - 提醒注册与取消

- `app/src/main/java/com/example/planreminder/reminder/ReminderReceiver.kt`
  - 本地通知展示
  - 提示音与震动组合控制

- `app/src/main/java/com/example/planreminder/reminder/BootReceiver.kt`
  - 重启和权限变化后的提醒恢复

### 语音与模型

- `app/src/main/java/com/example/planreminder/agent/DashScopeRealtimeSpeechClient.kt`
  - 实时语音识别客户端

- `app/src/main/java/com/example/planreminder/agent/QwenPlanAgentClient.kt`
  - 对话式计划整理客户端

- `app/src/main/java/com/example/planreminder/agent/AgentModels.kt`
  - 语音草稿与会话状态模型

- `app/src/main/java/com/example/planreminder/agent/AgentSettingsStore.kt`
  - 模型配置持久化

- `app/src/main/java/com/example/planreminder/agent/ReminderSettingsStore.kt`
  - 默认提醒分钟数持久化

## 运行环境

- Android Studio
- JDK 17
- Android SDK 34
- 最低支持 Android 8.0（`minSdk = 26`）

## 本地运行

1. 使用 Android Studio 打开项目目录
2. 等待 Gradle 同步完成
3. 连接真机或启动模拟器
4. 运行 `app` 模块

## 配置说明

如果要使用语音录入，需要在应用设置中填写：

- 接口地址
- 接口密钥
- 模型名称

设置页还提供以下能力：

- 设置新计划默认的提前提醒分钟数
- 切换应用显示语言

## 权限说明

应用涉及以下权限：

- 麦克风权限
  - 用于语音录入

- 通知权限
  - Android 13 及以上需要手动授予

- 精确提醒权限
  - Android 12 及以上建议开启
  - 未开启时仍可提醒，但触发时间可能略有延后

- 开机广播权限
  - 用于系统重启后恢复未来计划的提醒

## 数据与提醒说明

- 所有计划默认保存在本地数据库中
- 项目没有引入额外服务端存储
- 每条计划的提醒设置互相独立
- 计划时间必须晚于当前时间
- 语音草稿中地点可以留空，但事项、日期、时间必须完整

## 构建命令

常用构建命令如下：

```bash
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat assembleDebug
```

调试构建产物默认位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```
