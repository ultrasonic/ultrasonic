package org.moire.ultrasonic.view;

import android.content.Context;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.data.ActiveServerProvider;
import org.moire.ultrasonic.domain.ChatMessage;
import org.moire.ultrasonic.imageloader.ImageLoader;
import org.moire.ultrasonic.subsonic.ImageLoaderProvider;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import kotlin.Lazy;

import static org.koin.java.KoinJavaComponent.inject;

public class ChatAdapter extends ArrayAdapter<ChatMessage>
{
	private final Context context;
	private final List<ChatMessage> messages;

	private static final String phoneRegex = "1?\\W*([2-9][0-8][0-9])\\W*([2-9][0-9]{2})\\W*([0-9]{4})";
	private static final Pattern phoneMatcher = Pattern.compile(phoneRegex);

	private final Lazy<ActiveServerProvider> activeServerProvider = inject(ActiveServerProvider.class);
	private final Lazy<ImageLoaderProvider> imageLoaderProvider = inject(ImageLoaderProvider.class);

	public ChatAdapter(Context context, List<ChatMessage> messages)
	{
		super(context, R.layout.chat_item, messages);
		this.context = context;
		this.messages = messages;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return true;
	}

	@Override
	public boolean isEnabled(int position) {
		return false;
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

		String me = activeServerProvider.getValue().getActiveServer().getUserName();

		layout = messageUser.equals(me) ? R.layout.chat_item_reverse : R.layout.chat_item;

		if (convertView == null)
		{
			convertView = inflateView(layout, parent);
			holder = createViewHolder(layout, convertView);
		}
		else
		{
			holder = (ViewHolder) convertView.getTag();

			if (!holder.chatMessage.equals(message))
			{
				convertView = inflateView(layout, parent);
				holder = createViewHolder(layout, convertView);
			}
		}

		holder.chatMessage = message;

		DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);
		String messageTimeFormatted = String.format("[%s]", timeFormat.format(messageTime));

		ImageLoader imageLoader = imageLoaderProvider.getValue().getImageLoader();

		if (holder.avatar != null && !TextUtils.isEmpty(messageUser))
		{
			imageLoader.loadAvatarImage(holder.avatar, messageUser);
		}

		holder.username.setText(messageUser);
		holder.message.setText(messageText);
		holder.time.setText(messageTimeFormatted);

		return convertView;
	}

	private View inflateView(int layout, ViewGroup parent)
	{
		return LayoutInflater.from(context).inflate(layout, parent, false);
	}

	private static ViewHolder createViewHolder(int layout, View convertView)
	{
		ViewHolder holder = new ViewHolder();
		holder.layout = layout;

		TextView usernameView;
		TextView timeView;
		TextView messageView;
		ImageView imageView;

		if (convertView != null)
		{
			usernameView = (TextView) convertView.findViewById(R.id.chat_username);
			timeView = (TextView) convertView.findViewById(R.id.chat_time);
			messageView = (TextView) convertView.findViewById(R.id.chat_message);
			imageView = (ImageView) convertView.findViewById(R.id.chat_avatar);

			messageView.setMovementMethod(LinkMovementMethod.getInstance());
			Linkify.addLinks(messageView, Linkify.ALL);
			Linkify.addLinks(messageView, phoneMatcher, "tel:");

			holder.avatar = imageView;
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
		ImageView avatar;
		TextView message;
		TextView username;
		TextView time;
		ChatMessage chatMessage;
	}
}
