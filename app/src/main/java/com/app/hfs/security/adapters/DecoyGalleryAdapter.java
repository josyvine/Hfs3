package com.hfs.security.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.hfs.security.databinding.ItemDecoyPhotoBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the Fake Gallery (Decoy System).
 * Populates the grid in FakeGalleryFragment with harmless nature/wallpaper images.
 * If an intruder navigates here, they see this content instead of private files.
 */
public class DecoyGalleryAdapter extends RecyclerView.Adapter<DecoyGalleryAdapter.DecoyViewHolder> {

    private List<String> decoyImages;

    public DecoyGalleryAdapter(List<String> decoyImages) {
        this.decoyImages = decoyImages != null ? decoyImages : new ArrayList<>();
    }

    @NonNull
    @Override
    public DecoyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDecoyPhotoBinding binding = ItemDecoyPhotoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DecoyViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DecoyViewHolder holder, int position) {
        String imageName = decoyImages.get(position);
        holder.bind(imageName);
    }

    @Override
    public int getItemCount() {
        return decoyImages.size();
    }

    public void setItems(List<String> newItems) {
        this.decoyImages = newItems;
        notifyDataSetChanged();
    }

    /**
     * ViewHolder for a single decoy photo item.
     */
    static class DecoyViewHolder extends RecyclerView.ViewHolder {
        private final ItemDecoyPhotoBinding binding;

        public DecoyViewHolder(ItemDecoyPhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(String imageName) {
            /*
             * In a real production app, 'imageName' would be a URL or a 
             * local asset path. For this security demo, we use Glide to 
             * load a random nature placeholder from the web (Lorempixel/Unsplash)
             * or fall back to a system drawable to simulate content.
             */
            
            // Using a distinct seed based on position to get different images
            String dummyUrl = "https://source.unsplash.com/random/300x300?nature,water&sig=" + getAdapterPosition();

            Glide.with(itemView.getContext())
                    .load(dummyUrl)
                    .thumbnail(0.1f)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery) // Fallback if offline
                    .into(binding.ivDecoyImage);
        }
    }
}