# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Taskbar 是一个 Android 应用，在屏幕顶部放置开始菜单和最近应用托盘，支持 Android 10 的桌面模式和 Android 7.0+ 的自由窗口模式。该项目同时也是 Android-x86 的系统应用。

## 常用命令

```bash
# 构建调试版本
./gradlew assembleFreeDebug

# 运行单元测试
./gradlew testFreeDebug

# 代码格式检查
./gradlew spotlessCheck

# 生成测试覆盖率报告
./gradlew jacocoTestFreeDebugUnitTestReport
```

构建产物位于 `app/build/outputs/apk/free/debug`。

## 架构概览

### 源代码结构

项目使用多个 source set 来支持不同的构建变体：

- `app/src/main/java` - 核心业务逻辑
- `app/src/playstore/java` - Google Play 版本特有功能（Tasker 集成、图标包等）
- `app/src/nonplaystore/java` - 非 Play 版本的替代实现
- `app/src/lib/java` - 作为库使用时的接口（libtaskbar）
- `app/src/nonlib/java` - 独立应用时的功能（快捷方式、收藏应用磁贴等）
- `app/src/androidx86/java` - Android-x86 系统集成的桩实现
- `app/src/compat-{28,29,30,31,34}/java` - 各 Android 版本的兼容层

### 核心包结构

```
com.farmerbb.taskbar/
├── activity/      # Activity 和 dark 主题变体
├── adapter/       # RecyclerView 适配器
├── backup/        # 备份恢复功能
├── fragment/      # 设置界面 Fragment
├── helper/        # 全局状态管理（单例 Helper 类）
├── receiver/      # BroadcastReceiver
├── service/       # 前台服务（Taskbar、StartMenu、Dashboard 等）
├── ui/            # UI 控制器（UIController 模式）
├── util/          # 工具类和常量
└── widget/        # 自定义 View
```

### UI 架构

UI 采用 Controller 模式，而非传统的 Fragment/Activity 模式：
- `UIHostService` - 承载 UI 的前台服务
- `UIController` - UI 控制器基类
- `TaskbarController` / `StartMenuController` / `DashboardController` - 具体控制器

### Helper 类

全局状态通过 Helper 单例管理：
- `GlobalHelper` - 应用全局状态
- `LauncherHelper` - 启动器状态
- `FreeformHackHelper` - 自由窗口模式状态
- `DisplayHelper` - 显示相关状态
- `MenuHelper` - 菜单状态
- `ToastHelper` - Toast 状态

## 重要约束

### AOSP 兼容性

该项目必须能从 AOSP 源码完整构建（用于 Android-x86）。因此：

1. **优先使用 Java** - 贡献代码应使用 Java。仅在同时提供 AOSP 构建逻辑时才允许使用 Kotlin
2. **谨慎添加第三方库** - 如需添加，将引用代码限制在 `app/src/playstore` 目录，并在 `app/src/nonplaystore` 提供替代实现
3. **保持 API 兼容** - 浏览器库版本固定为 1.0.0 以兼容 Android-x86

### 代码风格

- 使用 Spotless 进行 Kotlin 代码格式化（ktlint，最大行宽 100）
- 保持与现有代码风格一致
- 仅修改与功能相关的代码，避免不必要的格式变更

## 测试

测试框架：Robolectric + JUnit 4 + PowerMock

测试文件位于 `app/src/test/java`，使用 Kotlin 编写。

运行单个测试类：
```bash
./gradlew testFreeDebug --tests "com.farmerbb.taskbar.util.UTest"
```

## CI 流程

GitHub Actions 在 push/PR 到 master 时执行：
1. 构建 (`assembleFreeDebug`)
2. 格式检查 (`spotlessCheck`)
3. 单元测试 (`testFreeDebug`)
