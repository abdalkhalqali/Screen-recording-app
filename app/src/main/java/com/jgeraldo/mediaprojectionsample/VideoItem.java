package com.jgeraldo.mediaprojectionsample;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoItem {
    private File file;
    private String name;
    private String size;
    private String date;

    public VideoItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.size = formatFileSize(file.length());
        this.date = formatDate(file.lastModified());
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public String getSize() {
        return size;
    }

    public String getDate() {
        return date;
    }
}