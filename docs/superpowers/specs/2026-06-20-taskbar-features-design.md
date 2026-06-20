# Taskbar 功能增强设计文档

日期: 2026-06-20
状态: 已批准

## 概述

为 Taskbar Android 应用增加四项功能增强：

1. 隐藏开始按钮开关
2. 置顶应用管理页面（添加/移除/拖拽排序）
3. 置顶应用图标定制（透明度、自定义图标、文字替代）
4. 任务栏贴边拖拽微调偏移

---

## 需求一：隐藏开始按钮

### 设计

新增 SharedPreference 键 `PREF_HIDE_START_BUTTON`（`"hide_start_button"`），默认 `false`。

在 `AppearanceFragment` 的外观设置中增加 `CheckBoxPreference`。当开关打开时，`TaskbarController.drawStartButton()` 跳过开始按钮的渲染，并触发一次任务栏重新布局。

### 数据流

```
用户切换开关 -> SharedPreference 更新 -> TaskbarController 收到偏好变更广播
-> drawStartButton() 检查 PREF_HIDE_START_BUTTON -> 跳过或执行渲染
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| `Constants.java` | 新增 `PREF_HIDE_START_BUTTON` 常量 |
| `tb_pref_appearance.xml` | 新增 `CheckBoxPreference` |
| `AppearanceFragment.java` | 可能需要设置 summary |
| `TaskbarController.java` | `drawStartButton()` 增加条件判断，注册偏好变更监听 |

---

## 需求二：置顶应用管理页面

### 设计

新建 `PinnedAppsActivity`，展示当前已置顶应用列表，支持：

- **拖拽排序**：`RecyclerView` + `ItemTouchHelper` 实现拖拽，拖动改变 `pinnedApps` 列表顺序。列表顺序决定任务栏上从左到右的显示顺序。
- **移除置顶**：每行右侧删除按钮，点击后从列表移除并持久化。
- **添加置顶**：右上角 "+" 按钮，弹出应用选择器（类似 `SelectAppActivity`），选中后加入列表末尾并持久化。

在 `GeneralFragment` 中新增入口偏好 `PREF_PINNED_APPS`，点击打开 `PinnedAppsActivity`。

底层数据操作使用现有的 `PinnedBlockedApps` 单例，修改完列表后调用 `save()` 持久化。

保留开始菜单长按置顶/取消置顶的原有方式不变。

### 数据流

```
用户打开 PinnedAppsActivity -> 加载 PinnedBlockedApps.getPinnedApps()
-> 展示列表 -> 用户拖拽/删除/添加 -> 更新 PinnedBlockedApps -> save()
-> 发送刷新广播 -> TaskbarController 重新渲染
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| `PinnedAppsActivity.java` | 新建 |
| `activity/dark/PinnedAppsActivityDark.java` | 新建 dark 主题变体 |
| `adapter/PinnedAppsAdapter.java` | 新建 RecyclerView 适配器 |
| `res/layout/tb_pinned_apps.xml` | 新建布局（RecyclerView + FAB） |
| `res/layout/tb_pinned_app_row.xml` | 新建列表项布局（拖拽手柄 + 图标 + 标签 + 删除按钮） |
| `Constants.java` | 新增 `PREF_PINNED_APPS` 常量 |
| `tb_pref_general.xml` | 新增设置入口 |
| `GeneralFragment.java` | 绑定入口点击事件 |
| `AndroidManifest.xml` | 注册 Activity |

---

## 需求三：置顶应用图标定制

### 数据模型扩展

扩展 `AppEntry` 类，新增三个可序列化字段：

```java
private byte[] customIconByteArray;  // 自定义图标压缩字节，null 表示使用默认
private String customText;           // 替代文字，null 或空表示不启用，最长 2 字符
private boolean useCustomIcon;       // 是否启用自定义图标
```

### 子功能 3a：全局透明度

- 新增 `PREF_PINNED_APP_ALPHA`（`"pinned_app_alpha"`），默认 `100`（完全不透明）
- 在 `AppearanceFragment` 中增加 `SeekBarPreference`，范围 20–100
- 在 `TaskbarController.getView()` 渲染置顶图标时，对 `ImageView` 设置 `setImageAlpha()`

### 子功能 3b：逐应用自定义图标 / 文字替代

在 `PinnedAppsActivity` 中，点击某个已置顶应用弹出定制对话框，提供：
- "选择自定义图标" — 调用系统图片选择器（`ACTION_PICK` 或 `ACTION_OPEN_DOCUMENT`）
- "输入替代文字" — `EditText`，限制 2 个字符，实时预览
- "恢复默认" — 清除自定义设置

在 `TaskbarController.getView()` 渲染时按优先级检查定制：
1. 若有 `customText`：隐藏 `ImageView`，显示 `TextView` 绘制文字
2. 若 `useCustomIcon` 且 `customIconByteArray` 非空：使用自定义图标
3. 否则：使用默认应用图标

### 数据流

```
用户在 PinnedAppsActivity 点击应用 -> 弹出定制对话框 -> 
选择图标 (序列化为 byte[]) / 输入文字 (最长2字符) / 恢复默认 ->
更新 AppEntry 字段 -> PinnedBlockedApps.save() ->
TaskbarController.getView() 渲染时检查定制字段，按优先级渲染
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| `AppEntry.java` | 新增 `customIconByteArray`、`customText`、`useCustomIcon` 字段 |
| `Constants.java` | 新增 `PREF_PINNED_APP_ALPHA` 常量 |
| `tb_pref_appearance.xml` | 新增透明度 `SeekBarPreference` |
| `AppearanceFragment.java` | 绑定透明度偏好 |
| `TaskbarController.java` | `getView()` 增加定制渲染逻辑 |
| `PinnedAppsActivity.java` | 增加点击打开定制对话框 |
| `res/layout/tb_pinned_app_customize_dialog.xml` | 新建定制对话框布局 |
| `res/layout/tb_icon.xml` | 可能需要增加 `TextView` 元素用于文字替代 |

---

## 需求四：任务栏拖拽微调偏移

### 偏好新增

新增两个 SharedPreference 键：
- `PREF_TASKBAR_OFFSET_X`（`"taskbar_offset_x"`），默认 `0`（dp）
- `PREF_TASKBAR_OFFSET_Y`（`"taskbar_offset_y"`），默认 `0`（dp）

### ViewParams 扩展

在 `ViewParams` 中增加 `offsetX`、`offsetY` 字段。在 `toWindowManagerParams()` 方法中，将 px 转换后的 offset 叠加到 `wmParams.x` / `wmParams.y`（根据 gravity 方向调整偏移的正负方向）。

### 拖拽交互

1. **触发**：长按任务栏空白区域（非图标、非按钮），约 400ms 后触发
2. **反馈**：震动反馈（`Vibrator`）+ 任务栏边框高亮 2dp 蓝色描边（表示进入可拖拽状态）
3. **拖拽**：拦截 `onTouchEvent`，`ACTION_MOVE` 时计算手指位移增量，实时更新 `WindowManager.LayoutParams.x/y`
4. **结束**：`ACTION_UP` 时保存当前偏移量到 SharedPreference，退出拖拽状态，移除高亮边框
5. **冲突处理**：长按优先判断触摸点是否在空白区域，若在图标/按钮上则不触发拖拽，保留原有长按行为

### 重置功能

在设置页面增加"重置偏移"按钮（`PREF_RESET_OFFSET`），将 `offsetX`/`offsetY` 归零并即时生效。

### 数据流

```
用户长按任务栏空白区域(>400ms) -> 震动+高亮 -> 进入拖拽状态
-> 手指移动 -> 实时更新 WindowManager.LayoutParams(x, y)
-> 手指抬起 -> 计算最终偏移(dp) -> 写入 SharedPreference -> 移除高亮

重置: 设置页点击重置 -> SharedPreference 归零 -> 发送广播 ->
TaskbarController 重新计算 LayoutParams
```

### 涉及文件

| 文件 | 变更 |
|------|------|
| `Constants.java` | 新增 `PREF_TASKBAR_OFFSET_X`、`PREF_TASKBAR_OFFSET_Y`、`PREF_RESET_OFFSET` |
| `ViewParams.java` | 新增 `offsetX`、`offsetY` 字段，修改 `toWindowManagerParams()` |
| `TaskbarController.java` | 增加长按检测、拖拽手势处理、偏移计算和窗口更新 |
| `tb_pref_general.xml` 或 `tb_pref_appearance.xml` | 新增重置偏移按钮 |

---

## 通用注意事项

- 所有新增字符串资源需添加英文默认值和中文翻译
- 所有新建 Activity 需创建对应的 dark 主题变体（`activity/dark/` 目录）
- 遵循现有代码风格：Java 编写业务代码，Kotlin 仅用于测试
- 新增测试覆盖核心逻辑（偏好读写、列表排序、偏移计算等）
