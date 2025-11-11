package com.jgeraldo.mediaprojectionsample;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private List<VideoItem> videoItems;
    private Context context;

    public VideoAdapter(Context context, List<VideoItem> videoItems) {
        this.context = context;
        this.videoItems = videoItems;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoItem item = videoItems.get(position);

        holder.videoName.setText(item.getName());
        holder.videoSize.setText(context.getString(R.string.video_size, item.getSize()));
        holder.videoDate.setText(context.getString(R.string.video_date, item.getDate()));

        holder.buttonPlay.setOnClickListener(v -> {
            try {
                Uri videoUri = FileProvider.getUriForFile(
                        context,
                        context.getApplicationContext().getPackageName() + ".fileprovider",
                        item.getFile()
                );

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(videoUri, "video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, R.string.no_video_player, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, context.getString(R.string.error_opening_video, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });

        holder.buttonShare.setOnClickListener(v -> {
            try {
                Uri videoUri = FileProvider.getUriForFile(
                        context,
                        context.getApplicationContext().getPackageName() + ".fileprovider",
                        item.getFile()
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("video/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, videoUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_video));
                context.startActivity(chooser);
            } catch (Exception e) {
                Toast.makeText(context, context.getString(R.string.error_sharing_video, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return videoItems.size();
    }

    public void updateVideos(List<VideoItem> newVideoItems) {
        this.videoItems = newVideoItems;
        notifyDataSetChanged();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView videoName, videoSize, videoDate;
        Button buttonPlay, buttonShare;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            videoName = itemView.findViewById(R.id.video_name);
            videoSize = itemView.findViewById(R.id.video_size);
            videoDate = itemView.findViewById(R.id.video_date);
            buttonPlay = itemView.findViewById(R.id.button_play);
            buttonShare = itemView.findViewById(R.id.button_share);
        }
    }
}