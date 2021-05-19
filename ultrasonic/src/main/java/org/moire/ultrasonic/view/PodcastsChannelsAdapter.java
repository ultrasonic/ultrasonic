package org.moire.ultrasonic.view;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.PodcastsChannel;

import java.util.List;

/**
 * @author Sindre Mehus
 */
public class PodcastsChannelsAdapter extends ArrayAdapter<PodcastsChannel> {
    private final LayoutInflater layoutInflater;

    public PodcastsChannelsAdapter(Context context, List<PodcastsChannel> channels) {
        super(context, R.layout.podcasts_channel_item, channels);

        layoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        PodcastsChannel entry = getItem(position);

        TextView view;
        if (convertView instanceof PlaylistView) {
            view = (TextView) convertView;
        } else {
            view = (TextView) layoutInflater
                    .inflate(R.layout.podcasts_channel_item, parent, false);
        }

        view.setText(entry.getTitle());

        return view;
    }
}
