package com.jgeraldo.mediaprojectionsample;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MediaGalleryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyView;
    private TabLayout tabLayout;
    private MaterialButton btnDeleteAll;

    private MediaAdapter adapter;
    private List<MediaItem> videos = new ArrayList<>();
    private List<MediaItem> screenshots = new ArrayList<>();
    private boolean showingVideos = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_gallery);

        recyclerView = findViewById(R.id.galleryRecycler);
        emptyView = findViewById(R.id.emptyText);
        tabLayout = findViewById(R.id.tabLayout);
        btnDeleteAll = findViewById(R.id.btnDeleteAll);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new MediaAdapter();
        recyclerView.setAdapter(adapter);

        // Setup tabs
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                showingVideos = tab.getPosition() == 0;
                refreshList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnDeleteAll.setOnClickListener(v -> confirmDeleteAll());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadMediaFiles();
    }

    private void loadMediaFiles() {
        videos.clear();
        screenshots.clear();
        String appFolder = "مسجل الشاشة";

        // Load videos from MediaStore
        String[] videoProjection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION
        };
        String videoSelection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ? OR "
                + MediaStore.Video.Media.DATA + " LIKE ?";
        String[] videoArgs = new String[]{"%" + appFolder + "%", "%" + appFolder + "%"};
        String videoOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection, videoSelection, videoArgs, videoOrder)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(2);
                    if (path != null && path.contains(appFolder)) {
                        videos.add(new MediaItem(
                                cursor.getLong(0),
                                cursor.getString(1),
                                path,
                                cursor.getLong(3) * 1000,
                                cursor.getLong(4),
                                cursor.getLong(5),
                                true
                        ));
                    }
                }
            }
        } catch (Exception ignored) {}

        // Load screenshots from MediaStore
        String[] imageProjection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
        };
        String imageSelection = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ? OR "
                + MediaStore.Images.Media.DATA + " LIKE ?";
        String[] imageArgs = new String[]{"%" + appFolder + "%", "%" + appFolder + "%"};
        String imageOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection, imageSelection, imageArgs, imageOrder)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(2);
                    if (path != null && path.contains(appFolder)) {
                        screenshots.add(new MediaItem(
                                cursor.getLong(0),
                                cursor.getString(1),
                                path,
                                cursor.getLong(3) * 1000,
                                cursor.getLong(4),
                                0,
                                false
                        ));
                    }
                }
            }
        } catch (Exception ignored) {}

        refreshList();
    }

    private void refreshList() {
        List<MediaItem> currentList = showingVideos ? videos : screenshots;
        adapter.setItems(currentList);

        if (currentList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(showingVideos ? getString(R.string.no_recordings) : getString(R.string.no_screenshots));
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void confirmDeleteAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("🗑️ " + getString(R.string.delete_all))
                .setMessage(R.string.delete_all_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> deleteAll())
                .setNegativeButton(R.string.perm_later, null)
                .show();
    }

    private void deleteAll() {
        List<MediaItem> items = showingVideos ? videos : screenshots;
        for (MediaItem item : items) {
            try {
                new File(item.path).delete();
            } catch (Exception ignored) {}
        }
        loadMediaFiles();
        Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show();
    }

    private void deleteItem(MediaItem item) {
        try {
            new File(item.path).delete();
            // Also delete from MediaStore
            Uri uri = showingVideos
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            getContentResolver().delete(uri, MediaStore.MediaColumns._ID + "=?",
                    new String[]{String.valueOf(item.id)});
        } catch (Exception ignored) {}
        loadMediaFiles();
        Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show();
    }

    private void shareItem(MediaItem item) {
        Uri uri;
        try {
            File file = new File(item.path);
            uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
        } catch (Exception e) {
            // Fallback
            uri = Uri.fromFile(new File(item.path));
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(item.isVideo ? "video/mp4" : "image/jpeg");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent,
                item.isVideo ? getString(R.string.share_video) : getString(R.string.share_screenshot)));
    }

    // --- Data Model ---
    static class MediaItem {
        final long id;
        final String name;
        final String path;
        final long dateAdded;
        final long size;
        final long duration;
        final boolean isVideo;

        MediaItem(long id, String name, String path, long dateAdded, long size, long duration, boolean isVideo) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.dateAdded = dateAdded;
            this.size = size;
            this.duration = duration;
            this.isVideo = isVideo;
        }

        String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024f);
            if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024f * 1024f));
            return String.format("%.1f GB", size / (1024f * 1024f * 1024f));
        }

        String getFormattedDuration() {
            if (duration <= 0) return "";
            int secs = (int) (duration / 1000);
            int mins = secs / 60;
            secs = secs % 60;
            return String.format("%d:%02d", mins, secs);
        }
    }

    // --- Adapter ---
    class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.ViewHolder> {
        private List<MediaItem> items = new ArrayList<>();

        void setItems(List<MediaItem> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_media_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MediaItem item = items.get(position);
            holder.nameText.setText(item.name);
            holder.sizeText.setText(item.getFormattedSize());
            holder.dateText.setText(new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date(item.dateAdded)));

            if (item.isVideo) {
                holder.typeIcon.setText("🎬");
                holder.durationText.setVisibility(View.VISIBLE);
                holder.durationText.setText(item.getFormattedDuration());
            } else {
                holder.typeIcon.setText("🖼️");
                holder.durationText.setVisibility(View.GONE);
                // Try to load thumbnail
                try {
                    holder.thumbnail.setImageBitmap(BitmapFactory.decodeFile(item.path));
                } catch (Exception e) {
                    holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(new File(item.path)),
                        item.isVideo ? "video/*" : "image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                String[] options = {
                        item.isVideo ? getString(R.string.share_video) : getString(R.string.share_screenshot),
                        getString(R.string.delete)
                };
                new MaterialAlertDialogBuilder(MediaGalleryActivity.this)
                        .setTitle(item.name)
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) shareItem(item);
                            else confirmDeleteItem(item);
                        })
                        .show();
                return true;
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            TextView typeIcon, nameText, sizeText, dateText, durationText;
            CardView card;

            ViewHolder(View v) {
                super(v);
                thumbnail = v.findViewById(R.id.mediaThumbnail);
                typeIcon = v.findViewById(R.id.mediaTypeIcon);
                nameText = v.findViewById(R.id.mediaName);
                sizeText = v.findViewById(R.id.mediaSize);
                dateText = v.findViewById(R.id.mediaDate);
                durationText = v.findViewById(R.id.mediaDuration);
                card = v.findViewById(R.id.mediaCard);
            }
        }
    }

    private void confirmDeleteItem(MediaItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> deleteItem(item))
                .setNegativeButton(R.string.perm_later, null)
                .show();
    }
}
