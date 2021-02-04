package org.moire.ultrasonic.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Build;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.moire.ultrasonic.activity.SubsonicTabActivity;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public abstract class LoadingTask<T> extends BackgroundTask<T>
{
	private final Activity tabActivity;
	private final boolean cancellable;
	private boolean cancelled;
	private SwipeRefreshLayout swipe;

	public LoadingTask(Activity activity, final boolean cancellable, SwipeRefreshLayout swipe)
	{
		super(activity);
		tabActivity = activity;
		this.cancellable = cancellable;
		this.swipe = swipe;
	}

	@Override
	public void execute()
	{
		swipe.setRefreshing(true);

		new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					final T result = doInBackground();
					if (isCancelled())
					{
						return;
					}

					getHandler().post(new Runnable()
					{
						@Override
						public void run()
						{
							swipe.setRefreshing(false);
							done(result);
						}
					});
				}
				catch (final Throwable t)
				{
					if (isCancelled())
					{
						return;
					}

					getHandler().post(new Runnable()
					{
						@Override
						public void run()
						{
							swipe.setRefreshing(false);
							error(t);
						}
					});
				}
			}
		}.start();
	}

	@SuppressLint("NewApi")
	private boolean isCancelled()
	{
		// TODO: Implement cancelled
		//return Build.VERSION.SDK_INT >= 17 ? tabActivity.isDestroyed() || cancelled : cancelled;
		return cancelled;
	}

	@Override
	public void updateProgress(final String message)
	{
		getHandler().post(new Runnable()
		{
			@Override
			public void run()
			{
				// TODO: This seems to be NOOP, can it be removed?
			}
		});
	}
}