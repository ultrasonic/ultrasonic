package org.moire.ultrasonic.activity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.R;
import org.moire.ultrasonic.domain.ChatMessage;
import org.moire.ultrasonic.service.MusicService;
import org.moire.ultrasonic.service.MusicServiceFactory;
import org.moire.ultrasonic.util.BackgroundTask;
import org.moire.ultrasonic.util.TabActivityBackgroundTask;
import org.moire.ultrasonic.util.Util;
import org.moire.ultrasonic.view.ChatAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Joshua Bahnsen
 */
public final class ChatActivity extends SubsonicTabActivity
{
	private ListView chatListView;
	private EditText messageEditText;
	private ImageButton sendButton;
	private Timer timer;
	private volatile static Long lastChatMessageTime = (long) 0;
	private volatile static ArrayList<ChatMessage> messageList = new ArrayList<ChatMessage>();

	@Override
	protected void onCreate(Bundle bundle)
	{
		super.onCreate(bundle);
		setContentView(R.layout.chat);

		messageEditText = (EditText) findViewById(R.id.chat_edittext);
		sendButton = (ImageButton) findViewById(R.id.chat_send);

		sendButton.setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View view)
			{
				sendMessage();
			}
		});

		chatListView = findViewById(R.id.chat_entries_list);
		chatListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		chatListView.setStackFromBottom(true);

		String serverName = Util.getServerName(this, Util.getActiveServer(this));
		String userName = Util.getUserName(this, Util.getActiveServer(this));
		String title = String.format("%s [%s@%s]", getResources().getString(R.string.button_bar_chat), userName, serverName);

		setActionBarSubtitle(title);

		messageEditText.setImeActionLabel("Send", KeyEvent.KEYCODE_ENTER);

		messageEditText.addTextChangedListener(new TextWatcher()
		{
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2)
			{
			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2)
			{
			}

			@Override
			public void afterTextChanged(Editable editable)
			{
				sendButton.setEnabled(!Util.isNullOrWhiteSpace(editable.toString()));
			}
		});

		messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener()
		{

			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
			{
				if (actionId == EditorInfo.IME_ACTION_DONE || (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN))
				{
					sendMessage();
					return true;
				}

				return false;
			}
		});

		View chatMenuItem = findViewById(R.id.menu_chat);
		menuDrawer.setActiveView(chatMenuItem);

		load();
	}

	@Override
	protected void onPostCreate(Bundle bundle)
	{
		super.onPostCreate(bundle);

		timerMethod();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.chat, menu);
		super.onCreateOptionsMenu(menu);

		return true;
	}

	/*
	 * Listen for option item selections so that we receive a notification
	 * when the user requests a refresh by selecting the refresh action bar item.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			// Check if user triggered a refresh:
			case R.id.menu_refresh:
				// Start the refresh background task.
				new GetDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				return true;
		}
		// User didn't trigger a refresh, let the superclass handle this action
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if (!messageList.isEmpty())
		{
			ListAdapter chatAdapter = new ChatAdapter(ChatActivity.this, messageList);
			chatListView.setAdapter(chatAdapter);
		}

		if (timer == null)
		{
			timerMethod();
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		if (timer != null)
		{
			timer.cancel();
			timer = null;
		}
	}

	private void timerMethod()
	{
		int refreshInterval = Util.getChatRefreshInterval(this);

		if (refreshInterval > 0)
		{
			timer = new Timer();

			timer.schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					ChatActivity.this.runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							load();
						}
					});
				}
			}, refreshInterval, refreshInterval);
		}
	}

	private void sendMessage()
	{
		if (messageEditText != null)
		{
			final String message;
			Editable text = messageEditText.getText();

			if (text == null)
			{
				return;
			}

			message = text.toString();

			if (!Util.isNullOrWhiteSpace(message))
			{
				messageEditText.setText("");

				BackgroundTask<Void> task = new TabActivityBackgroundTask<Void>(ChatActivity.this, false)
				{
					@Override
					protected Void doInBackground() throws Throwable
					{
						MusicService musicService = MusicServiceFactory.getMusicService(ChatActivity.this);
						musicService.addChatMessage(message, ChatActivity.this, this);
						return null;
					}

					@Override
					protected void done(Void result)
					{
						load();
					}
				};

				task.execute();
			}
		}
	}

	private synchronized void load()
	{
		BackgroundTask<List<ChatMessage>> task = new TabActivityBackgroundTask<List<ChatMessage>>(this, false)
		{
			@Override
			protected List<ChatMessage> doInBackground() throws Throwable
			{
				MusicService musicService = MusicServiceFactory.getMusicService(ChatActivity.this);
				return musicService.getChatMessages(lastChatMessageTime, ChatActivity.this, this);
			}

			@Override
			protected void done(List<ChatMessage> result)
			{
				if (result != null && !result.isEmpty())
				{
					// Reset lastChatMessageTime if we have a newer message
					for (ChatMessage message : result)
					{
						if (message.getTime() > lastChatMessageTime)
						{
							lastChatMessageTime = message.getTime();
						}
					}

					// Reverse results to show them on the bottom
					Collections.reverse(result);
					messageList.addAll(result);

					ListAdapter chatAdapter = new ChatAdapter(ChatActivity.this, messageList);
					chatListView.setAdapter(chatAdapter);
				}
			}
		};

		task.execute();
	}

	private class GetDataTask extends AsyncTask<Void, Void, String[]>
	{
		@Override
		protected void onPostExecute(String[] result)
		{
			load();
			super.onPostExecute(result);
		}

		@Override
		protected String[] doInBackground(Void... params)
		{
			return null;
		}
	}
}
