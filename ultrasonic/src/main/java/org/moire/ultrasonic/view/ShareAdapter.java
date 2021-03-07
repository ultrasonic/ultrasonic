package org.moire.ultrasonic.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.Share;

import java.util.List;

/**
 * @author Sindre Mehus
 */
public class ShareAdapter extends ArrayAdapter<Share>
{

	private final Context context;

	public ShareAdapter(Context context, List<Share> Shares)
	{
		super(context, R.layout.share_list_item, Shares);
		this.context = context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		Share entry = getItem(position);
		ShareView view;

		if (convertView instanceof ShareView)
		{
			ShareView currentView = (ShareView) convertView;

			ViewHolder viewHolder = (ViewHolder) convertView.getTag();
			view = currentView;
			view.setViewHolder(viewHolder);
		}
		else
		{
			view = new ShareView(context);
			view.setLayout();
		}

		view.setShare(entry);
		return view;
	}

	static class ViewHolder
	{
		TextView url;
		TextView description;
	}
}