/* Copyright 2016 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.taskbar.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PinnedAppsActivity extends AppCompatActivity {

    private PinnedBlockedApps pba;
    private List<AppEntry> pinnedApps;
    private PinnedAppsAdapter adapter;
    private AppEntry currentEditingEntry;

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

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PinnedAppsAdapter(this, pinnedApps, this::showCustomizeDialog);
        recyclerView.setAdapter(adapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder source,
                                  RecyclerView.ViewHolder target) {
                int fromPos = source.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                adapter.onItemMove(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        adapter.setDragStartListener(itemTouchHelper::startDrag);

        adapter.setOnDeleteListener(entry -> {
            PinnedBlockedApps.getInstance(this).removePinnedApp(this, entry.getComponentName());
            PinnedBlockedApps.getInstance(this).forceSave(this);
        });

        findViewById(R.id.fab_add).setOnClickListener(v -> showAddAppDialog());
    }

    private void showAddAppDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tb_action_add_pinned_app);

        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        List<LauncherActivityInfo> allApps = launcherApps.getActivityList(null, Process.myUserHandle());

        List<LauncherActivityInfo> availableApps = new ArrayList<>();
        for(LauncherActivityInfo info : allApps) {
            String componentName = info.getComponentName().flattenToString();
            if(!PinnedBlockedApps.getInstance(this).isPinned(componentName)) {
                availableApps.add(info);
            }
        }

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

    private void showCustomizeDialog(AppEntry entry) {
        currentEditingEntry = entry;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.tb_pinned_app_customize_dialog, null);

        ImageView iconPreview = dialogView.findViewById(R.id.custom_icon_preview);
        EditText customTextInput = dialogView.findViewById(R.id.custom_text_input);
        TextView previewLabel = dialogView.findViewById(R.id.custom_text_preview_label);

        if(entry.hasCustomIcon()) {
            iconPreview.setImageDrawable(entry.getCustomIcon(this));
        } else {
            iconPreview.setImageDrawable(entry.getIcon(this));
        }

        if(entry.hasCustomText()) {
            customTextInput.setText(entry.getCustomText());
            previewLabel.setText(entry.getCustomText());
        }

        // Live preview of custom text
        customTextInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                previewLabel.setText(text.isEmpty() ? "" : text.substring(0, Math.min(text.length(), 2)));
            }
        });

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
                    syncEntryCustomization();
                    reloadPinnedApps();
                    U.restartTaskbar(this);
                })
                .setNegativeButton(R.string.tb_action_cancel, null)
                .show();
    }

    private void syncEntryCustomization() {
        List<AppEntry> current = PinnedBlockedApps.getInstance(this).getPinnedApps();
        for(AppEntry e : current) {
            if(e.getComponentName().equals(currentEditingEntry.getComponentName())) {
                e.setCustomText(currentEditingEntry.getCustomText());
                e.setCustomIconByteArray(currentEditingEntry.getCustomIconByteArray());
                break;
            }
        }
        PinnedBlockedApps.getInstance(this).forceSave(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 2 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Bitmap source = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                Bitmap cropped = cropToSquare(source);
                Bitmap rounded = applyRoundedCorners(cropped,
                        getResources().getDimensionPixelSize(R.dimen.tb_icon_corner_radius));
                BitmapDrawable drawable = new BitmapDrawable(getResources(), rounded);
                currentEditingEntry.setCustomIconFromDrawable(drawable);
                source.recycle();
                cropped.recycle();
                Toast.makeText(this, "Icon updated", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static Bitmap cropToSquare(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        return Bitmap.createBitmap(source, x, y, size, size);
    }

    private static Bitmap applyRoundedCorners(Bitmap source, int cornerRadius) {
        int size = source.getWidth();
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawRoundRect(new android.graphics.RectF(0, 0, size, size),
                cornerRadius, cornerRadius, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(source, 0, 0, paint);
        return output;
    }

    private void reloadPinnedApps() {
        pba = PinnedBlockedApps.getInstance(this);
        pinnedApps.clear();
        pinnedApps.addAll(pba.getPinnedApps());
        adapter.notifyDataSetChanged();
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
        // Sync current order to persistent store
        List<AppEntry> snapshot = new ArrayList<>(pinnedApps);
        pba.getPinnedApps().clear();
        for(AppEntry e : snapshot) {
            pba.getPinnedApps().add(e);
        }
        pba.forceSave(this);
        U.restartTaskbar(this);
    }
}
