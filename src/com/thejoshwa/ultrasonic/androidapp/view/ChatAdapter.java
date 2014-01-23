package com.thejoshwa.ultrasonic.androidapp.view;

import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.activity.SubsonicTabActivity;
import com.thejoshwa.ultrasonic.androidapp.domain.ChatMessage;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

public class ChatAdapter extends ArrayAdapter<ChatMessage>
{

	private final SubsonicTabActivity activity;
	private ArrayList<ChatMessage> messages;

	private static final String phoneRegex = "1?\\W*([2-9][0-8][0-9])\\W*([2-9][0-9]{2})\\W*([0-9]{4})"; //you can just place your support phone here
	private static final Pattern phoneMatcher = Pattern.compile(phoneRegex);

	public ChatAdapter(SubsonicTabActivity activity, ArrayList<ChatMessage> messages)
	{
		super(activity, R.layout.chat_item, messages);
		this.activity = activity;
		this.messages = messages;
	}

	@Override
	public int getCount()
	{
		return messages.size();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		ChatMessage message = this.getItem(position);

		ViewHolder holder;
		int layout;

		String messageUser = message.getUsername();
		Date messageTime = new java.util.Date(message.getTime());
		String messageText = message.getMessage();

		String me = Util.getUserName(activity, Util.getActiveServer(activity));

		layout = messageUser.equals(me) ? R.layout.chat_item_reverse : R.layout.chat_item;

		if (convertView == null)
		{
			convertView = inflateView(layout, parent);
			holder = createViewHolder(layout, convertView);
		}
		else
		{
			holder = (ViewHolder) convertView.getTag();

			if (holder.layout != layout)
			{
				convertView = inflateView(layout, parent);
				holder = createViewHolder(layout, convertView);
			}
		}

		DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(activity);
		String messageTimeFormatted = String.format("[%s]", timeFormat.format(messageTime));

		holder.username.setText(messageUser);
		holder.message.setText(messageText);
		holder.time.setText(messageTimeFormatted);

		return convertView;
	}

	private View inflateView(int layout, ViewGroup parent)
	{
		return LayoutInflater.from(activity).inflate(layout, parent, false);
	}

	private static ViewHolder createViewHolder(int layout, View convertView)
	{
		ViewHolder holder = new ViewHolder();
		holder.layout = layout;

		TextView usernameView;
		TextView timeView;
		TextView messageView;

		if (convertView != null)
		{
			usernameView = (TextView) convertView.findViewById(R.id.chat_username);
			timeView = (TextView) convertView.findViewById(R.id.chat_time);
			messageView = (TextView) convertView.findViewById(R.id.chat_message);

			messageView.setMovementMethod(LinkMovementMethod.getInstance());
			Linkify.addLinks(messageView, Linkify.EMAIL_ADDRESSES);
			Linkify.addLinks(messageView, Linkify.WEB_URLS);
			Linkify.addLinks(messageView, phoneMatcher, "tel:");

			holder.message = messageView;
			holder.username = usernameView;
			holder.time = timeView;

			convertView.setTag(holder);
		}

		return holder;
	}

	private static class ViewHolder
	{
		int layout;
		TextView message;
		TextView username;
		TextView time;
	}
}
