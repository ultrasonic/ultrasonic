package org.moire.ultrasonic.util;

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public abstract class LoadingTask<T> extends BackgroundTask<T>
{
	private final SwipeRefreshLayout swipe;
	private final CancellationToken cancel;

	public LoadingTask(Activity activity, SwipeRefreshLayout swipe, CancellationToken cancel)
	{
		super(activity);
		this.swipe = swipe;
		this.cancel = cancel;
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
					if (cancel.isCancellationRequested())
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
					if (cancel.isCancellationRequested())
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

	@Override
	public void updateProgress(final String message)
	{
	}
}