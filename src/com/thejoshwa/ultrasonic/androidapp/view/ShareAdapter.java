package com.thejoshwa.ultrasonic.androidapp.view;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.activity.SubsonicTabActivity;
import com.thejoshwa.ultrasonic.androidapp.domain.Share;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Sindre Mehus
 */
public class ShareAdapter extends ArrayAdapter<Share>
{

	private final SubsonicTabActivity activity;

	public ShareAdapter(SubsonicTabActivity activity, List<Share> Shares)
	{
		super(activity, R.layout.share_list_item, Shares);
		this.activity = activity;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		Share entry = getItem(position);
		ShareView view;
		view = convertView != null && convertView instanceof ShareView ? (ShareView) convertView : new ShareView(activity);
		view.setShare(entry);
		return view;
	}

	public static class ShareComparator implements Comparator<Share>, Serializable
	{
		private static final long serialVersionUID = -7169409928471418921L;

		@Override
		public int compare(Share share1, Share share2)
		{
			return share1.getId().compareToIgnoreCase(share2.getId());
		}

		public static List<Share> sort(List<Share> shares)
		{
			Collections.sort(shares, new ShareComparator());
			return shares;
		}

	}
}