# Taskbar 功能增强实现计划

> **对自动化执行者的说明：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 来按任务逐步实现此计划。步骤使用 checkbox (`- [ ]`) 语法进行跟踪。

**目标：** 为 Taskbar Android 应用实现四项功能增强：隐藏开始按钮、置顶应用管理页面、置顶应用图标定制（透明度/自定义图标/文字替代）、任务栏拖拽微调偏移。

**架构：** 基于现有的 SharedPreferences + 广播 架构，遵循 Controller 模式和 Helper 单例模式。新增 Activity 和 Adapter 用于置顶管理，扩展 AppEntry 数据模型支持定制字段，在 TaskbarController 中增加拖拽手势和条件渲染逻辑。

**技术栈：** Java（生产代码）、Kotlin（测试代码）、Android SDK 34、RecyclerView + ItemTouchHelper、WindowManager 悬浮窗

---

### Task 1: 扩展数据模型和常量定义

**文件：**
- 修改：`app/src/main/java/com/farmerbb/taskbar/util/AppEntry.java`
- 修改：`app/src/main/java/com/farmerbb/taskbar/util/Constants.java`

- [ ] **步骤 1：扩展 AppEntry 类，新增三个可序列化字段**

```java
// AppEntry.java - 在 private byte[] iconByteArray; 之后新增

private byte[] customIconByteArray;  // 自定义图标压缩字节，null 表示使用默认
private String customText;           // 替代文字，最长 2 字符，null/空表示不启用

// 新增 getter 方法
public byte[] getCustomIconByteArray() {
    return customIconByteArray;
}

public void setCustomIconByteArray(byte[] customIconByteArray) {
    this.customIconByteArray = customIconByteArray;
}

public String getCustomText() {
    return customText;
}

public void setCustomText(String customText) {
    if(customText != null && customText.length() > 2)
        customText = customText.substring(0, 2);
    this.customText = customText;
}

public boolean hasCustomIcon() {
    return customIconByteArray != null && customIconByteArray.length > 0;
}

public boolean hasCustomText() {
    return customText != null && !customText.isEmpty();
}

// 新增构造器重载，支持 shouldCompress 标志用于自定义图标
public void setCustomIconFromDrawable(Drawable icon) {
    if(icon instanceof BitmapDrawable) {
        Bitmap bitmap = ((BitmapDrawable) icon).getBitmap();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream);
        customIconByteArray = stream.toByteArray();
    }
}

public Drawable getCustomIcon(Context context) {
    if(customIconByteArray != null)
        return new BitmapDrawable(context.getResources(),
                BitmapFactory.decodeByteArray(customIconByteArray, 0, customIconByteArray.length));
    return null;
}
```

- [ ] **步骤 2：在 Constants.java 中新增所有偏好键常量**

在 `Constants.java` 的 SharedPreference keys 区域按字母序插入：

```java
public static final String PREF_HIDE_START_BUTTON = "hide_start_button";
public static final String PREF_PINNED_APPS = "pinned_apps";
public static final String PREF_PINNED_APP_ALPHA = "pinned_app_alpha";
public static final String PREF_PINNED_APP_ALPHA_PREF = "pinned_app_alpha_pref";
public static final String PREF_RESET_OFFSET = "reset_offset";
public static final String PREF_TASKBAR_OFFSET_X = "taskbar_offset_x";
public static final String PREF_TASKBAR_OFFSET_Y = "taskbar_offset_y";
```

- [ ] **步骤 3：在 PinnedBlockedApps 中添加 forceSave 公开方法**

在 `PinnedBlockedApps.java` 的 `clear()` 方法之后添加：

```java
public void forceSave(Context context) {
    save(context);
}
```

- [ ] **步骤 4：编译验证**

```bash
./gradlew assembleFreeDebug
```

确认编译通过，`AppEntry` 新字段兼容 Java 序列化（`Serializable` 自动处理）。

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/farmerbb/taskbar/util/AppEntry.java \
        app/src/main/java/com/farmerbb/taskbar/util/Constants.java \
        app/src/main/java/com/farmerbb/taskbar/util/PinnedBlockedApps.java
git commit -m "feat: 扩展 AppEntry 数据模型，新增 Constants 偏好键

- AppEntry 新增 customIconByteArray、customText 字段及 getter/setter
- 新增 hasCustomIcon/hasCustomText 判断方法
- PinnedBlockedApps 新增 forceSave 公开方法
- 新增 PREF_HIDE_START_BUTTON、PREF_PINNED_APPS 等常量"
```

---

### Task 2: 需求一 — 隐藏开始按钮

**文件：**
- 修改：`app/src/main/res/xml/tb_pref_appearance.xml`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/java/com/farmerbb/taskbar/fragment/AppearanceFragment.java`
- 修改：`app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java`

- [ ] **步骤 1：在 tb_pref_appearance.xml 添加 CheckBoxPreference**

在 `tb_pref_appearance.xml` 的 `PREF_TRANSPARENT_START_MENU` 之后添加：

```xml
<CheckBoxPreference
    android:defaultValue="false"
    android:key="hide_start_button"
    android:title="@string/tb_pref_title_hide_start_button"/>
```

- [ ] **步骤 2：添加中英文字符串资源**

在 `strings.xml` 添加：
```xml
<string name="tb_pref_title_hide_start_button">隐藏开始按钮</string>
```

在 `values-zh-rCN/strings.xml` 中确认已有对应的中文条目（若没有则添加）。

- [ ] **步骤 3：在 AppearanceFragment 中绑定偏好**

在 `AppearanceFragment.loadPrefs()` 的 `bindPreferenceSummaryToValue` 调用列表末尾添加：

```java
bindPreferenceSummaryToValue(findPreference(PREF_HIDE_START_BUTTON));
```

- [ ] **步骤 4：修改 TaskbarController.drawStartButton() 增加隐藏逻辑**

在 `drawStartButton()` 方法开头增加判断：

```java
@VisibleForTesting
void drawStartButton(Context context, ImageView startButton, SharedPreferences pref) {
    if(pref.getBoolean(PREF_HIDE_START_BUTTON, false)) {
        startButton.setVisibility(View.GONE);
        return;
    }

    // 原有逻辑...
    Drawable allAppsIcon = ContextCompat.getDrawable(context, R.drawable.tb_all_apps_button_icon);
    // ...
}
```

同时在 `drawTaskbar()` 中注册偏好变更监听，当 `PREF_HIDE_START_BUTTON` 变更后触发重启：

在 `drawTaskbar()` 方法末尾（`host.addView(layout, params);` 之前）添加：

```java
pref.registerOnSharedPreferenceChangeListener((sharedPreferences, key) -> {
    if(PREF_HIDE_START_BUTTON.equals(key)) {
        U.restartTaskbar(context);
    }
});
```

- [ ] **步骤 5：编译验证并运行测试**

```bash
./gradlew assembleFreeDebug
./gradlew testFreeDebug --tests "com.farmerbb.taskbar.ui.TaskbarControllerTest"
```

- [ ] **步骤 6：提交**

```bash
git add app/src/main/res/xml/tb_pref_appearance.xml app/src/main/res/values/strings.xml \
        app/src/main/java/com/farmerbb/taskbar/fragment/AppearanceFragment.java \
        app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java
git commit -m "feat: 支持隐藏开始按钮

- 在外观设置添加"隐藏开始按钮"开关
- drawStartButton 检查 PREF_HIDE_START_BUTTON 偏好
- 偏好变更时自动重启任务栏生效"
```

---

### Task 3: 需求二 — 置顶应用管理页面（布局和 Activity）

**文件：**
- 新建：`app/src/main/res/layout/tb_pinned_apps.xml`
- 新建：`app/src/main/res/layout/tb_pinned_app_row.xml`
- 新建：`app/src/main/java/com/farmerbb/taskbar/activity/PinnedAppsActivity.java`
- 新建：`app/src/main/java/com/farmerbb/taskbar/activity/dark/PinnedAppsActivityDark.java`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/playstore/AndroidManifest.xml`

- [ ] **步骤 1：创建列表项布局 tb_pinned_app_row.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="16dp"
    android:paddingEnd="8dp"
    android:paddingTop="8dp"
    android:paddingBottom="8dp"
    android:background="?attr/selectableItemBackground"
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <ImageView
        android:id="@+id/drag_handle"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:src="@drawable/tb_drag_handle"
        android:contentDescription="@string/tb_action_drag_to_reorder"
        android:padding="4dp"/>

    <ImageView
        android:id="@+id/icon"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:layout_marginStart="8dp"
        tools:src="@drawable/tb_allapps_pressed"/>

    <TextView
        android:id="@+id/label"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="12dp"
        android:textAppearance="?android:attr/textAppearanceMedium"
        tools:text="App Name"/>

    <ImageButton
        android:id="@+id/delete_button"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:src="@drawable/tb_ic_delete"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/tb_action_remove"/>

</LinearLayout>
```

- [ ] **步骤 2：创建 Activity 布局 tb_pinned_apps.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <androidx.appcompat.widget.Toolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="?attr/colorPrimary"
        android:theme="?attr/actionBarTheme"
        android:elevation="4dp">

        <TextView
            android:id="@+id/toolbar_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:text="@string/tb_pref_title_pinned_apps"
            android:textColor="?attr/colorOnPrimary"
            android:textSize="18sp"/>

    </androidx.appcompat.widget.Toolbar>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recycler_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:clipToPadding="false"
        android:scrollbars="vertical"/>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_add"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end|bottom"
        android:layout_margin="16dp"
        android:src="@drawable/tb_ic_add"
        android:contentDescription="@string/tb_action_add_pinned_app"/>

</LinearLayout>
```

- [ ] **步骤 3：创建 PinnedAppsActivity**

```java
package com.farmerbb.taskbar.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.farmerbb.taskbar.R;
import com.farmerbb.taskbar.adapter.PinnedAppsAdapter;
import com.farmerbb.taskbar.util.AppEntry;
import com.farmerbb.taskbar.util.IconCache;
import com.farmerbb.taskbar.util.PinnedBlockedApps;
import com.farmerbb.taskbar.util.U;

import java.util.ArrayList;
import java.util.List;

import static com.farmerbb.taskbar.util.Constants.ACTION_UPDATE_HOME_SCREEN_MARGINS;
import static com.farmerbb.taskbar.util.Constants.PREF_PINNED_APP_ALPHA;

public class PinnedAppsActivity extends AppCompatActivity {

    private PinnedBlockedApps pba;
    private List<AppEntry> pinnedApps;
    private PinnedAppsAdapter adapter;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tb_pinned_apps);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        pba = PinnedBlockedApps.getInstance(this);
        pinnedApps = new ArrayList<>(pba.getPinnedApps());

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PinnedAppsAdapter(this, pinnedApps, entry -> {
            // 点击事件：打开定制对话框（需求三实现时填充）
            showCustomizeDialog(entry);
        });
        recyclerView.setAdapter(adapter);

        // 拖拽排序
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder source,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = source.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                adapter.onItemMove(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public boolean isLongPressDragEnabled() {
                // 只在拖拽手柄上启用长按拖拽
                return false;
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // 设置拖拽手柄监听器
        adapter.setDragStartListener(viewHolder ->
            itemTouchHelper.startDrag(viewHolder));

        // 添加按钮
        findViewById(R.id.fab_add).setOnClickListener(v -> showAddAppDialog());
    }

    // 弹出应用添加选择对话框
    private void showAddAppDialog() {
        // 使用类似 SelectAppFragment 的应用列表弹窗
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tb_action_add_pinned_app);

        // 获取所有启动器应用列表
        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        List<LauncherActivityInfo> allApps = launcherApps.getActivityList(null,
                Process.myUserHandle());

        // 过滤出未置顶的应用
        List<LauncherActivityInfo> availableApps = new ArrayList<>();
        for(LauncherActivityInfo info : allApps) {
            String componentName = info.getComponentName().flattenToString();
            if(!PinnedBlockedApps.getInstance(this).isPinned(componentName)) {
                availableApps.add(info);
            }
        }

        // 按标签排序
        Collections.sort(availableApps, (a, b) ->
                Collator.getInstance().compare(a.getLabel().toString(), b.getLabel().toString()));

        String[] appLabels = new String[availableApps.size()];
        for(int i = 0; i < availableApps.size(); i++) {
            appLabels[i] = availableApps.get(i).getLabel().toString();
        }

        builder.setItems(appLabels, (dialog, which) -> {
            LauncherActivityInfo selected = availableApps.get(which);
            String componentName = selected.getComponentName().flattenToString();
            String label = selected.getLabel().toString();
            String packageName = selected.getComponentName().getPackageName();

            Drawable icon = IconCache.getInstance(PinnedAppsActivity.this)
                    .getIcon(PinnedAppsActivity.this, getPackageManager(), selected);

            AppEntry entry = new AppEntry(packageName, componentName, label, icon, true);
            PinnedBlockedApps.getInstance(PinnedAppsActivity.this).addPinnedApp(PinnedAppsActivity.this, entry);
            reloadPinnedApps();
            U.restartTaskbar(PinnedAppsActivity.this);
        });

        builder.setNegativeButton(R.string.tb_action_cancel, null);
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 2 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                currentEditingEntry.setCustomIconFromDrawable(drawable);
                // 保存到 PinnedBlockedApps
                syncEntryCustomization(currentEditingEntry);
                reloadPinnedApps();
            } catch (IOException e) {
                Toast.makeText(this, R.string.tb_error_loading_image, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void syncEntryCustomization(AppEntry sourceEntry) {
        List<AppEntry> current = PinnedBlockedApps.getInstance(this).getPinnedApps();
        for(AppEntry e : current) {
            if(e.getComponentName().equals(sourceEntry.getComponentName())) {
                e.setCustomText(sourceEntry.getCustomText());
                e.setCustomIconByteArray(sourceEntry.getCustomIconByteArray());
                break;
            }
        }
        // 强制重新序列化：清空后重新添加
        saveSortedList();
    }

    private void saveSortedList() {
        // 清空并批量重新添加以保持顺序
        List<AppEntry> snapshot = new ArrayList<>(pinnedApps);
        pba.getPinnedApps().clear();
        for(AppEntry e : snapshot) {
            pba.getPinnedApps().add(e);
        }
        pba.forceSave(this);
    }

    private void reloadPinnedApps() {
        pba = PinnedBlockedApps.getInstance(this);
        pinnedApps.clear();
        pinnedApps.addAll(pba.getPinnedApps());
        adapter.notifyDataSetChanged();
    }

    // 弹出定制对话框（需求三填充）
    private void showCustomizeDialog(AppEntry entry) {
        // 占位：需求三实现
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSortedList();
        U.restartTaskbar(this);
    }
}
```

- [ ] **步骤 4：创建 dark 变体**

```java
// PinnedAppsActivityDark.java
package com.farmerbb.taskbar.activity.dark;

import com.farmerbb.taskbar.activity.PinnedAppsActivity;

public class PinnedAppsActivityDark extends PinnedAppsActivity {}
```

- [ ] **步骤 5：在 AndroidManifest.xml 注册 Activity**

在 manifest 的 `<application>` 块中，`SelectAppActivity` 附近添加：

```xml
<activity
    android:name=".activity.PinnedAppsActivity"
    android:theme="@style/Taskbar"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:taskAffinity="com.farmerbb.taskbar.pinnedapps"
    android:configChanges="uiMode|screenSize"/>

<activity
    android:name=".activity.dark.PinnedAppsActivityDark"
    android:theme="@style/Taskbar.Dark"
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:taskAffinity="com.farmerbb.taskbar.pinnedapps"
    android:configChanges="uiMode|screenSize"/>
```

- [ ] **步骤 6：添加字符串资源**

```xml
<string name="tb_pref_title_pinned_apps">管理置顶应用</string>
<string name="tb_action_add_pinned_app">添加置顶应用</string>
<string name="tb_action_remove">移除</string>
<string name="tb_action_drag_to_reorder">拖拽排序</string>
<string name="tb_no_pinned_apps">暂设置顶应用</string>
```

- [ ] **步骤 7：编译验证**

```bash
./gradlew assembleFreeDebug
```

- [ ] **步骤 8：提交**

```bash
git add app/src/main/res/layout/tb_pinned_apps.xml \
        app/src/main/res/layout/tb_pinned_app_row.xml \
        app/src/main/java/com/farmerbb/taskbar/activity/PinnedAppsActivity.java \
        app/src/main/java/com/farmerbb/taskbar/activity/dark/PinnedAppsActivityDark.java \
        app/src/main/res/values/strings.xml \
        app/src/playstore/AndroidManifest.xml
git commit -m "feat: 创建置顶应用管理页面

- 新建 PinnedAppsActivity 支持拖拽排序和删除
- RecyclerView + ItemTouchHelper 实现拖拽重排
- 新建 dark 主题变体
- 注册 Activity 到 Manifest"
```

---

### Task 4: 需求二 — 置顶应用管理页面（适配器和设置入口）

**文件：**
- 新建：`app/src/main/java/com/farmerbb/taskbar/adapter/PinnedAppsAdapter.java`
- 修改：`app/src/main/res/xml/tb_pref_general.xml`
- 修改：`app/src/main/java/com/farmerbb/taskbar/fragment/GeneralFragment.java`
- 新建：`app/src/main/res/drawable/tb_ic_delete.xml`
- 新建：`app/src/main/res/drawable/tb_ic_add.xml`
- 新建：`app/src/main/res/drawable/tb_drag_handle.xml`

- [ ] **步骤 1：创建 PinnedAppsAdapter**

```java
package com.farmerbb.taskbar.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.farmerbb.taskbar.R;
import com.farmerbb.taskbar.util.AppEntry;

import java.util.Collections;
import java.util.List;

public class PinnedAppsAdapter extends RecyclerView.Adapter<PinnedAppsAdapter.ViewHolder> {

    private final Context context;
    private final List<AppEntry> entries;
    private final OnItemClickListener listener;
    private OnStartDragListener dragStartListener;

    public interface OnItemClickListener {
        void onItemClick(AppEntry entry);
    }

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public void setDragStartListener(OnStartDragListener dragStartListener) {
        this.dragStartListener = dragStartListener;
    }

    public PinnedAppsAdapter(Context context, List<AppEntry> entries, OnItemClickListener listener) {
        this.context = context;
        this.entries = entries;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.tb_pinned_app_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppEntry entry = entries.get(position);
        holder.label.setText(entry.getLabel());
        holder.icon.setImageDrawable(entry.getIcon(context));

        holder.itemView.setOnClickListener(v -> listener.onItemClick(entry));

        holder.deleteButton.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if(pos != RecyclerView.NO_POSITION) {
                entries.remove(pos);
                notifyItemRemoved(pos);
            }
        });

        // 拖拽手柄长按触发 ItemTouchHelper 的 startDrag
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if(event.getAction() == MotionEvent.ACTION_DOWN && dragStartListener != null) {
                dragStartListener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if(fromPosition < toPosition) {
            for(int i = fromPosition; i < toPosition; i++) {
                Collections.swap(entries, i, i + 1);
            }
        } else {
            for(int i = fromPosition; i > toPosition; i--) {
                Collections.swap(entries, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView dragHandle;
        ImageView icon;
        TextView label;
        ImageButton deleteButton;

        ViewHolder(View itemView) {
            super(itemView);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }
    }
}
```

- [ ] **步骤 2：创建矢量图标资源**

`tb_ic_delete.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"/>
</vector>
```

`tb_ic_add.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@android:color/white">
    <path android:fillColor="@android:color/white"
        android:pathData="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
</vector>
```

`tb_drag_handle.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M11,18c0,1.1 -0.9,2 -2,2s-2,-0.9 -2,-2 0.9,-2 2,-2 2,0.9 2,2zM9,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM9,4c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM15,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM15,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM15,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"/>
</vector>
```

- [ ] **步骤 3：在 tb_pref_general.xml 添加设置入口**

在 `tb_pref_general.xml` 的 `PREF_BLACKLIST` 之后添加：

```xml
<Preference
    android:key="pinned_apps"
    android:title="@string/tb_pref_title_pinned_apps" />
```

- [ ] **步骤 4：在 GeneralFragment 绑定入口点击事件**

在 `GeneralFragment.loadPrefs()` 中，紧跟 `findPreference(PREF_BLACKLIST).setOnPreferenceClickListener(this);` 之后添加：

```java
findPreference(PREF_PINNED_APPS).setOnPreferenceClickListener(this);
```

在 `onPreferenceClick()` 的 switch 中添加：

```java
case PREF_PINNED_APPS:
    Intent pinnedIntent = U.getThemedIntent(getActivity(), PinnedAppsActivity.class);
    startActivity(pinnedIntent);
    break;
```

在 `onResume()` 中添加 summary 更新：

```java
int pinnedCount = PinnedBlockedApps.getInstance(getActivity()).getPinnedApps().size();
Preference pinnedPref = findPreference(PREF_PINNED_APPS);
if(pinnedPref != null) {
    pinnedPref.setSummary(getString(R.string.tb_pinned_apps_count, pinnedCount));
}
```

字符串资源：

```xml
<string name="tb_pinned_apps_count">已置顶 %d 个应用</string>
```

- [ ] **步骤 5：编译验证**

```bash
./gradlew assembleFreeDebug
```

- [ ] **步骤 6：提交**

```bash
git add app/src/main/java/com/farmerbb/taskbar/adapter/PinnedAppsAdapter.java \
        app/src/main/res/xml/tb_pref_general.xml \
        app/src/main/java/com/farmerbb/taskbar/fragment/GeneralFragment.java \
        app/src/main/res/drawable/tb_ic_delete.xml \
        app/src/main/res/drawable/tb_ic_add.xml \
        app/src/main/res/drawable/tb_drag_handle.xml \
        app/src/main/res/values/strings.xml
git commit -m "feat: 创建 PinnedAppsAdapter 并连接设置入口

- PinnedAppsAdapter 支持列表展示和删除操作
- GeneralFragment 新增"管理置顶应用"入口
- 新建删除/添加/拖拽手柄矢量图标
- onResume 更新置顶应用数量 summary"
```

---

### Task 5: 需求三 — 全局透明度和图标定制渲染

**文件：**
- 修改：`app/src/main/res/xml/tb_pref_appearance.xml`
- 修改：`app/src/main/java/com/farmerbb/taskbar/fragment/AppearanceFragment.java`
- 修改：`app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java`（getView 方法）

- [ ] **步骤 1：在 tb_pref_appearance.xml 添加透明度设置入口**

在 `tb_pref_appearance.xml` 中 `PREF_TRANSPARENT_START_MENU` 之后添加：

```xml
<Preference
    android:key="pinned_app_alpha_pref"
    android:title="@string/tb_pref_title_pinned_app_alpha"
    android:summary="@string/tb_pref_description_pinned_app_alpha"/>
```

- [ ] **步骤 2：添加字符串**

```xml
<string name="tb_pref_title_pinned_app_alpha">置顶应用透明度</string>
<string name="tb_pref_description_pinned_app_alpha">已置顶应用图标的透明度，20 为最透明，100 为完全不透明</string>
```

- [ ] **步骤 3：在 AppearanceFragment 绑定透明度偏好点击**

在 `AppearanceFragment.loadPrefs()` 中，`findPreference(PREF_INVISIBLE_BUTTON)` 附近添加绑定：

```java
findPreference(PREF_PINNED_APP_ALPHA_PREF).setOnPreferenceClickListener(this);
bindPreferenceSummaryToValue(findPreference(PREF_PINNED_APP_ALPHA_PREF));
```

在 `onPreferenceClick` 的 switch 中添加透明度滑块弹窗（使用现有 `showColorPicker` 风格的 AlertDialog + SeekBar 模式）：

```java
case PREF_PINNED_APP_ALPHA_PREF:
    showPinnedAlphaPicker();
    break;
```

添加 `showPinnedAlphaPicker` 方法：

```java
private void showPinnedAlphaPicker() {
    SharedPreferences pref = U.getSharedPreferences(getActivity());
    int currentAlpha = pref.getInt(PREF_PINNED_APP_ALPHA, 100);

    LinearLayout dialogLayout = new LinearLayout(getActivity());
    dialogLayout.setOrientation(LinearLayout.VERTICAL);
    dialogLayout.setPadding(48, 24, 48, 24);

    final TextView alphaValue = new TextView(getActivity());
    alphaValue.setText(String.valueOf(currentAlpha));
    alphaValue.setTextSize(24);
    alphaValue.setGravity(Gravity.CENTER);
    dialogLayout.addView(alphaValue);

    final SeekBar seekBar = new SeekBar(getActivity());
    seekBar.setMax(80); // 100 - 20 = 80 range
    seekBar.setProgress(currentAlpha - 20);
    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            alphaValue.setText(String.valueOf(progress + 20));
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    });
    dialogLayout.addView(seekBar);

    new AlertDialog.Builder(getActivity())
            .setTitle(R.string.tb_pref_title_pinned_app_alpha)
            .setView(dialogLayout)
            .setPositiveButton(R.string.tb_action_ok, (dialog, which) -> {
                int newAlpha = seekBar.getProgress() + 20;
                pref.edit().putInt(PREF_PINNED_APP_ALPHA, newAlpha).apply();
                findPreference(PREF_PINNED_APP_ALPHA_PREF)
                        .setSummary(String.valueOf(newAlpha));
                U.restartTaskbar(getActivity());
            })
            .setNegativeButton(R.string.tb_action_cancel, null)
            .show();
}
```

在 Constants.java 添加：

```java
public static final String PREF_PINNED_APP_ALPHA_PREF = "pinned_app_alpha_pref";
```

- [ ] **步骤 4：修改 TaskbarController.getView() 渲染逻辑**

在 `getView()` 方法中，`imageView.setImageDrawable(entry.getIcon(context));` 之后，`imageView2.setBackgroundColor(U.getAccentColor(context));` 之前添加：

```java
// 检查置顶应用定制
boolean isPinned = position < numOfPinnedApps && !needToReverseOrder(context, sortOrder);
if(!isPinned) {
    // 检查反向排列中的置顶判定
    String tp = TaskbarPosition.getTaskbarPosition(context);
    if(tp.contains("vertical"))
        isPinned = position >= list.size() - numOfPinnedApps;
    else
        isPinned = position < numOfPinnedApps;
}

if(isPinned) {
    int alpha = pref.getInt(PREF_PINNED_APP_ALPHA, 100);
    float alphaFloat = alpha / 100f;

    if(entry.hasCustomText()) {
        // 显示文字替代
        imageView.setVisibility(View.GONE);
        TextView textView = convertView.findViewById(R.id.custom_text);
        if(textView == null) {
            textView = new TextView(context);
            textView.setId(R.id.custom_text);
            textView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            textView.setGravity(Gravity.CENTER);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            textView.setTextColor(U.getAccentColor(context));
            ((FrameLayout) convertView).addView(textView, 1);
        }
        textView.setText(entry.getCustomText());
        textView.setAlpha(alphaFloat);
        textView.setVisibility(View.VISIBLE);
    } else {
        // 隐藏文字视图
        TextView textView = convertView.findViewById(R.id.custom_text);
        if(textView != null) textView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);

        if(entry.hasCustomIcon()) {
            imageView.setImageDrawable(entry.getCustomIcon(context));
        }
        imageView.setImageAlpha((int)(alphaFloat * 255));
    }
} else {
    imageView.setImageAlpha(255);
}
```

- [ ] **步骤 5：编译验证**

```bash
./gradlew assembleFreeDebug
```

- [ ] **步骤 6：提交**

```bash
git add app/src/main/res/xml/tb_pref_appearance.xml \
        app/src/main/java/com/farmerbb/taskbar/fragment/AppearanceFragment.java \
        app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java \
        app/src/main/res/values/strings.xml
git commit -m "feat: 支持置顶应用全局透明度和图标定制渲染

- 新增 PREF_PINNED_APP_ALPHA SeekBarPreference (20-100)
- getView 渲染时检查 AppEntry 定制字段
- 支持文字替代和自定义图标渲染
- 置顶应用按 alpha 值设置图标透明度"
```

---

### Task 6: 需求三 — 定制对话框

**文件：**
- 新建：`app/src/main/res/layout/tb_pinned_app_customize_dialog.xml`
- 修改：`app/src/main/java/com/farmerbb/taskbar/activity/PinnedAppsActivity.java`
- 修改：`app/src/main/res/values/strings.xml`

- [ ] **步骤 1：创建定制对话框布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/tb_customize_icon"
        android:textAppearance="?android:attr/textAppearanceMedium"
        android:layout_marginBottom="8dp"/>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="16dp">

        <ImageView
            android:id="@+id/custom_icon_preview"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="@drawable/tb_allapps_pressed"/>

        <Button
            android:id="@+id/btn_select_icon"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:text="@string/tb_select_custom_icon"/>

        <Button
            android:id="@+id/btn_reset_icon"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:text="@string/tb_reset_to_default"/>

    </LinearLayout>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/tb_customize_text"
        android:textAppearance="?android:attr/textAppearanceMedium"
        android:layout_marginBottom="8dp"/>

    <EditText
        android:id="@+id/custom_text_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxLength="2"
        android:hint="@string/tb_custom_text_hint"
        android:inputType="text"
        android:singleLine="true"/>

</LinearLayout>
```

- [ ] **步骤 2：添加字符串资源**

```xml
<string name="tb_customize_icon">自定义图标</string>
<string name="tb_customize_text">替代文字（最多2个字符）</string>
<string name="tb_custom_text_hint">输入文字...</string>
<string name="tb_select_custom_icon">选择图标</string>
<string name="tb_reset_to_default">恢复默认</string>
<string name="tb_no_custom_icon_set">未设置自定义图标</string>
```

- [ ] **步骤 3：在 PinnedAppsActivity 中填充 showCustomizeDialog**

替换 `PinnedAppsActivity` 中的 `showCustomizeDialog` 占位方法：

```java
private AppEntry currentEditingEntry;

private void showCustomizeDialog(AppEntry entry) {
    currentEditingEntry = entry;
    View dialogView = LayoutInflater.from(this).inflate(R.layout.tb_pinned_app_customize_dialog, null);

    ImageView iconPreview = dialogView.findViewById(R.id.custom_icon_preview);
    EditText customTextInput = dialogView.findViewById(R.id.custom_text_input);

    // 初始化现有值
    if(entry.hasCustomIcon()) {
        iconPreview.setImageDrawable(entry.getCustomIcon(this));
    } else {
        iconPreview.setImageDrawable(entry.getIcon(this));
    }

    if(entry.hasCustomText()) {
        customTextInput.setText(entry.getCustomText());
    }

    dialogView.findViewById(R.id.btn_select_icon).setOnClickListener(v -> {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent,
                getString(R.string.tb_select_custom_icon)), 2);
    });

    dialogView.findViewById(R.id.btn_reset_icon).setOnClickListener(v -> {
        currentEditingEntry.setCustomIconByteArray(null);
        iconPreview.setImageDrawable(currentEditingEntry.getIcon(this));
    });

    new AlertDialog.Builder(this)
            .setTitle(entry.getLabel())
            .setView(dialogView)
            .setPositiveButton(R.string.tb_action_ok, (dialog, which) -> {
                String customText = customTextInput.getText().toString().trim();
                if(customText.isEmpty()) {
                    currentEditingEntry.setCustomText(null);
                } else {
                    if(customText.length() > 2) customText = customText.substring(0, 2);
                    currentEditingEntry.setCustomText(customText);
                }
                // 保存
                PinnedBlockedApps pba = PinnedBlockedApps.getInstance(this);
                // 更新列表中的 entry
                for(int i = 0; i < pba.getPinnedApps().size(); i++) {
                    if(pba.getPinnedApps().get(i).getComponentName()
                            .equals(currentEditingEntry.getComponentName())) {
                        // 更新定制字段
                        AppEntry e = pba.getPinnedApps().get(i);
                        e.setCustomText(currentEditingEntry.getCustomText());
                        e.setCustomIconByteArray(currentEditingEntry.getCustomIconByteArray());
                        break;
                    }
                }
                // 触发重新保存通过 remove/add 实现
                reloadPinnedApps();
                U.restartTaskbar(this);
            })
            .setNegativeButton(R.string.tb_action_cancel, null)
            .show();
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if(requestCode == 2 && resultCode == RESULT_OK && data != null && data.getData() != null) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
            BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
            currentEditingEntry.setCustomIconFromDrawable(drawable);
            // 重新显示对话框的内容预览
        } catch (IOException e) {
            Toast.makeText(this, R.string.tb_error_loading_image, Toast.LENGTH_SHORT).show();
        }
    }
    if(requestCode == 1 && resultCode == RESULT_OK) {
        reloadPinnedApps();
    }
}
```

- [ ] **步骤 4：编译验证**

```bash
./gradlew assembleFreeDebug
```

- [ ] **步骤 5：提交**

```bash
git add app/src/main/res/layout/tb_pinned_app_customize_dialog.xml \
        app/src/main/java/com/farmerbb/taskbar/activity/PinnedAppsActivity.java \
        app/src/main/res/values/strings.xml
git commit -m "feat: 实现置顶应用图标定制对话框

- 支持选择自定义图标（系统图片选择器）
- 支持输入最多2字符替代文字
- 支持恢复默认图标
- 保存后自动重启任务栏生效"
```

---

### Task 7: 需求四 — ViewParams 扩展和窗口偏移

**文件：**
- 修改：`app/src/main/java/com/farmerbb/taskbar/ui/ViewParams.java`
- 修改：`app/src/main/java/com/farmerbb/taskbar/ui/UIController.java`

- [ ] **步骤 1：扩展 ViewParams 类**

修改 `ViewParams.java`，新增 `offsetX`、`offsetY` 字段并更新所有构造/更新方法：

```java
public class ViewParams {
    public int width;
    public int height;
    public int gravity;
    public int flags;
    public int bottomMargin;
    public int offsetX;    // 新增：水平偏移（px）
    public int offsetY;    // 新增：垂直偏移（px）

    public ViewParams(int width, int height, int gravity, int flags, int bottomMargin) {
        this(width, height, gravity, flags, bottomMargin, 0, 0);
    }

    public ViewParams(int width, int height, int gravity, int flags,
                       int bottomMargin, int offsetX, int offsetY) {
        this.width = width;
        this.height = height;
        this.gravity = gravity;
        this.flags = flags;
        this.bottomMargin = bottomMargin;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public WindowManager.LayoutParams toWindowManagerParams() {
        final WindowManager.LayoutParams wmParams = new WindowManager.LayoutParams(
                width,
                height,
                U.getOverlayType(),
                flags,
                PixelFormat.TRANSLUCENT
        );

        if(gravity > -1)
            wmParams.gravity = gravity;

        if(bottomMargin > -1)
            wmParams.y = bottomMargin;

        // 应用偏移：叠加到当前 x/y（bottomMargin 已在上方设置）
        if(offsetX != 0)
            wmParams.x = offsetX;

        if(offsetY != 0) {
            wmParams.y = (bottomMargin > -1 ? bottomMargin : 0) + offsetY;
        }

        U.allowReflection();
        try {
            Class<?> layoutParamsClass = Class.forName("android.view.WindowManager$LayoutParams");

            Field privateFlags = layoutParamsClass.getField("privateFlags");
            Field noAnim = layoutParamsClass.getField("PRIVATE_FLAG_NO_MOVE_ANIMATION");

            int privateFlagsValue = privateFlags.getInt(wmParams);
            int noAnimFlag = noAnim.getInt(wmParams);
            privateFlagsValue |= noAnimFlag;

            privateFlags.setInt(wmParams, privateFlagsValue);
        } catch (Exception ignored) {}

        return wmParams;
    }

    public ViewParams updateWidth(int width) {
        return new ViewParams(width, height, gravity, flags, bottomMargin, offsetX, offsetY);
    }

    public ViewParams updateHeight(int height) {
        return new ViewParams(width, height, gravity, flags, bottomMargin, offsetX, offsetY);
    }

    public ViewParams updateBottomMargin(int bottomMargin) {
        return new ViewParams(width, height, gravity, flags, bottomMargin, offsetX, offsetY);
    }

    public ViewParams updateOffset(int offsetX, int offsetY) {
        return new ViewParams(width, height, gravity, flags, bottomMargin, offsetX, offsetY);
    }
}
```

- [ ] **步骤 2：在 UIController 中添加 dp 转 px 的偏移加载方法**

在 `UIController.java` 中添加：

```java
@VisibleForTesting
static int getOffsetX(Context context) {
    SharedPreferences pref = U.getSharedPreferences(context);
    int offsetDp = pref.getInt(PREF_TASKBAR_OFFSET_X, 0);
    return (int) (offsetDp * context.getResources().getDisplayMetrics().density);
}

@VisibleForTesting
static int getOffsetY(Context context) {
    SharedPreferences pref = U.getSharedPreferences(context);
    int offsetDp = pref.getInt(PREF_TASKBAR_OFFSET_Y, 0);
    return (int) (offsetDp * context.getResources().getDisplayMetrics().density);
}
```

- [ ] **步骤 3：编译验证**

```bash
./gradlew assembleFreeDebug
```

- [ ] **步骤 4：提交**

```bash
git add app/src/main/java/com/farmerbb/taskbar/ui/ViewParams.java \
        app/src/main/java/com/farmerbb/taskbar/ui/UIController.java
git commit -m "feat: 扩展 ViewParams 支持水平/垂直偏移

- ViewParams 新增 offsetX/offsetY 字段
- toWindowManagerParams 应用偏移到 wmParams
- UIController 新增 dp 转 px 的偏移加载方法"
```

---

### Task 8: 需求四 — 任务栏拖拽交互

**文件：**
- 修改：`app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java`
- 修改：`app/src/main/res/xml/tb_pref_general.xml`
- 修改：`app/src/main/java/com/farmerbb/taskbar/fragment/GeneralFragment.java`

- [ ] **步骤 1：在 TaskbarController.drawTaskbar() 中加载偏移**

在 `drawTaskbar()` 方法中创建 `ViewParams` 时，从 SharedPreferences 加载偏移：

```java
int offsetX = UIController.getOffsetX(context);
int offsetY = UIController.getOffsetY(context);

final ViewParams params = new ViewParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        -1,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        getBottomMargin(context),
        offsetX,
        offsetY
);
```

- [ ] **步骤 2：实现拖拽交互逻辑**

在 `TaskbarController` 中新增拖拽相关的字段和方法：

```java
// 拖拽相关字段（在类的字段声明区域添加）
private boolean isDragging = false;
private float dragStartX, dragStartY;
private int initialOffsetX, initialOffsetY;
private View dragHighlightBorder;
private Vibrator vibrator;

// 在 drawTaskbar() 中初始化 vibrator 和设置触摸监听
vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

// 对 layout 设置触摸监听
layout.setOnTouchListener(new TaskbarDragTouchListener());
```

添加内部类 `TaskbarDragTouchListener`：

```java
private class TaskbarDragTouchListener implements View.OnTouchListener {
    private static final int LONG_PRESS_TIMEOUT = 400;
    private Handler dragHandler = new Handler();
    private Runnable longPressRunnable;
    private boolean longPressTriggered = false;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 检查触摸点是否在空白区域（非图标、非按钮区域）
                if(isTouchOnEmptyArea(v, event)) {
                    dragStartX = event.getRawX();
                    dragStartY = event.getRawY();

                    SharedPreferences pref = U.getSharedPreferences(context);
                    initialOffsetX = UIController.getOffsetX(context);
                    initialOffsetY = UIController.getOffsetY(context);

                    longPressTriggered = false;
                    longPressRunnable = () -> {
                        longPressTriggered = true;
                        enterDragMode(v);
                    };
                    dragHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if(longPressTriggered && isDragging) {
                    float deltaX = event.getRawX() - dragStartX;
                    float deltaY = event.getRawY() - dragStartY;

                    int newX = initialOffsetX + (int) deltaX;
                    int newY = initialOffsetY + (int) deltaY;

                    ViewParams newParams = new ViewParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            getTaskbarGravity(TaskbarPosition.getTaskbarPosition(context)),
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                            getBottomMargin(context),
                            newX,
                            newY
                    );

                    try {
                        UIHost host = getHost();
                        if(host != null) host.updateViewLayout(layout, newParams);
                    } catch (Exception ignored) {}
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragHandler.removeCallbacks(longPressRunnable);

                if(longPressTriggered && isDragging) {
                    exitDragMode(v, event);
                }
                longPressTriggered = false;
                break;
        }
        return false;
    }

    private boolean isTouchOnEmptyArea(View v, MotionEvent event) {
        // 检查触摸点是否落在图标/按钮控件上
        float x = event.getX();
        float y = event.getY();

        // 遍历子 View，如果触摸在某个具体控件上则返回 false
        if(isPointInsideView(startButton, x, y)) return false;
        if(dashboardButton != null && dashboardButton.getVisibility() == View.VISIBLE
                && isPointInsideView(dashboardButton, x, y)) return false;
        if(navbarButtons != null && navbarButtons.getVisibility() == View.VISIBLE
                && isPointInsideView(navbarButtons, x, y)) return false;
        if(button != null && isPointInsideView(button, x, y)) return false;
        if(scrollView != null && scrollView.getVisibility() == View.VISIBLE
                && isPointInsideView(scrollView, x, y)) return false;

        return true;
    }

    private boolean isPointInsideView(View view, float x, float y) {
        int[] location = new int[2];
        view.getLocationInWindow(location);
        return x >= location[0] && x <= location[0] + view.getWidth()
                && y >= location[1] && y <= location[1] + view.getHeight();
    }

    private void enterDragMode(View v) {
        isDragging = true;

        // 震动反馈
        if(vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(50);

        // 添加边框高亮
        v.setBackgroundColor(Color.argb(60, 0, 0, 255));
        // 或者使用带边框的 drawable
    }

    private void exitDragMode(View v, MotionEvent event) {
        isDragging = false;

        // 移除高亮
        int backgroundTint = U.getBackgroundTint(context);
        v.setBackgroundColor(backgroundTint);

        // 计算最终偏移（dp），保存到 SharedPreferences
        float deltaX = event.getRawX() - dragStartX;
        float deltaY = event.getRawY() - dragStartY;

        float density = context.getResources().getDisplayMetrics().density;
        int offsetXDp = Math.round((initialOffsetX + deltaX) / density);
        int offsetYDp = Math.round((initialOffsetY + deltaY) / density);

        SharedPreferences pref = U.getSharedPreferences(context);
        pref.edit()
                .putInt(PREF_TASKBAR_OFFSET_X, offsetXDp)
                .putInt(PREF_TASKBAR_OFFSET_Y, offsetYDp)
                .apply();
    }
}
```

- [ ] **步骤 3：添加重置偏移设置入口**

在 `tb_pref_general.xml` 添加：

```xml
<Preference
    android:key="reset_offset"
    android:title="@string/tb_reset_taskbar_offset"/>
```

在 `GeneralFragment.loadPrefs()` 绑定：

```java
findPreference(PREF_RESET_OFFSET).setOnPreferenceClickListener(this);
```

在 `GeneralFragment.onPreferenceClick()` 添加：

```java
case PREF_RESET_OFFSET:
    SharedPreferences pref = U.getSharedPreferences(getActivity());
    pref.edit()
            .putInt(PREF_TASKBAR_OFFSET_X, 0)
            .putInt(PREF_TASKBAR_OFFSET_Y, 0)
            .apply();
    U.restartTaskbar(getActivity());
    break;
```

字符串资源：
```xml
<string name="tb_reset_taskbar_offset">重置任务栏偏移</string>
```

- [ ] **步骤 4：编译验证**

```bash
./gradlew assembleFreeDebug
```

- [ ] **步骤 5：提交**

```bash
git add app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java \
        app/src/main/res/xml/tb_pref_general.xml \
        app/src/main/java/com/farmerbb/taskbar/fragment/GeneralFragment.java \
        app/src/main/res/values/strings.xml
git commit -m "feat: 实现任务栏长按拖拽微调偏移

- 长按任务栏空白区域 (>400ms) 进入拖拽模式
- 震动反馈 + 蓝色高亮提示可拖拽状态
- 拖拽结束时自动保存偏移到 SharedPreferences
- 设置页新增"重置任务栏偏移"按钮"
```

---

### Task 9: 测试

**文件：**
- 新建：`app/src/test/java/com/farmerbb/taskbar/util/AppEntryTest.kt`
- 新建：`app/src/test/java/com/farmerbb/taskbar/activity/PinnedAppsActivityTest.kt`
- 修改：`app/src/test/java/com/farmerbb/taskbar/ui/TaskbarControllerTest.kt`

- [ ] **步骤 1：编写 AppEntry 定制字段测试**

```kotlin
package com.farmerbb.taskbar.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import com.google.common.truth.Truth.assertThat

@RunWith(RobolectricTestRunner::class)
class AppEntryTest {

    private val context: Context = RuntimeEnvironment.application

    @Test
    fun `test customText setter and getter`() {
        val entry = AppEntry("com.test", "com.test.Main", "Test", null, false)

        entry.customText = "AB"
        assertThat(entry.customText).isEqualTo("AB")
        assertThat(entry.hasCustomText()).isTrue()
    }

    @Test
    fun `test customText max length`() {
        val entry = AppEntry("com.test", "com.test.Main", "Test", null, false)

        entry.customText = "ABCDEFG"
        assertThat(entry.customText).isEqualTo("AB")
    }

    @Test
    fun `test customText empty and null`() {
        val entry = AppEntry("com.test", "com.test.Main", "Test", null, false)

        entry.customText = ""
        assertThat(entry.hasCustomText()).isFalse()

        entry.customText = null
        assertThat(entry.hasCustomText()).isFalse()
    }

    @Test
    fun `test customIconByteArray`() {
        val entry = AppEntry("com.test", "com.test.Main", "Test", null, false)

        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(context.resources, bitmap)
        entry.setCustomIconFromDrawable(drawable)

        assertThat(entry.hasCustomIcon()).isTrue()
        assertThat(entry.customIconByteArray).isNotNull()
    }
}
```

- [ ] **步骤 2：编写 UIController 偏移测试**

```kotlin
// 在 UIControllerTest.kt 中添加
@Test
fun `test getOffsetX and getOffsetY`() {
    val pref = U.getSharedPreferences(context)
    pref.edit()
        .putInt(PREF_TASKBAR_OFFSET_X, 10)
        .putInt(PREF_TASKBAR_OFFSET_Y, -5)
        .apply()

    val offsetX = UIController.getOffsetX(context)
    val offsetY = UIController.getOffsetY(context)

    val density = context.resources.displayMetrics.density
    assertThat(offsetX).isEqualTo((10 * density).toInt())
    assertThat(offsetY).isEqualTo((-5 * density).toInt())
}
```

- [ ] **步骤 3：编写 drawStartButton 隐藏测试**

在 `TaskbarControllerTest.kt` 中添加：

```kotlin
@Test
fun `test drawStartButton with hide pref enabled`() {
    val pref = U.getSharedPreferences(context)
    pref.edit().putBoolean(PREF_HIDE_START_BUTTON, true).apply()

    val startButton = ImageView(context)
    controller.drawStartButton(context, startButton, pref)

    assertThat(startButton.visibility).isEqualTo(View.GONE)
}

@Test
fun `test drawStartButton with hide pref disabled`() {
    val pref = U.getSharedPreferences(context)
    pref.edit().putBoolean(PREF_HIDE_START_BUTTON, false).apply()

    val startButton = ImageView(context)
    controller.drawStartButton(context, startButton, pref)

    assertThat(startButton.visibility).isNotEqualTo(View.GONE)
}
```

- [ ] **步骤 4：运行测试**

```bash
./gradlew testFreeDebug --tests "com.farmerbb.taskbar.util.AppEntryTest"
./gradlew testFreeDebug --tests "com.farmerbb.taskbar.ui.TaskbarControllerTest"
```

预期：新增测试全部通过。

- [ ] **步骤 5：提交**

```bash
git add app/src/test/java/com/farmerbb/taskbar/util/AppEntryTest.kt \
        app/src/test/java/com/farmerbb/taskbar/ui/UIControllerTest.kt \
        app/src/test/java/com/farmerbb/taskbar/ui/TaskbarControllerTest.kt
git commit -m "test: 添加新功能的单元测试

- AppEntryTest: 测试 customText/customIcon 字段
- UIControllerTest: 测试偏移加载方法
- TaskbarControllerTest: 测试隐藏开始按钮逻辑"
```

---

### Task 10: 最终验证

- [ ] **步骤 1：运行完整测试套件**

```bash
./gradlew testFreeDebug
```

确认所有测试通过。

- [ ] **步骤 2：代码格式检查**

```bash
./gradlew spotlessCheck
```

如有格式问题，运行 `./gradlew spotlessApply` 修复。

- [ ] **步骤 3：完整构建**

```bash
./gradlew assembleFreeDebug
```

确认构建成功，产物位于 `app/build/outputs/apk/free/debug/`。

- [ ] **步骤 4：最终提交（如有格式化修改）**

```bash
git add . && git commit -m "style: 代码格式化修复"
```
