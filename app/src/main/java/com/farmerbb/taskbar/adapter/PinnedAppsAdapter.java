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

package com.farmerbb.taskbar.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
