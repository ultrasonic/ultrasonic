package org.moire.ultrasonic.util;

import android.app.Activity;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public abstract class TabActivityBackgroundTask<T> extends BackgroundTask<T>
{
	private final boolean changeProgress;
	private final SwipeRefreshLayout swipe;
	private final CancellationToken cancel;

	public TabActivityBackgroundTask(Activity activity, boolean changeProgress,
									 SwipeRefreshLayout swipe, CancellationToken cancel)
	{
		super(activity);
		this.changeProgress = changeProgress;
		this.swipe = swipe;
		this.cancel = cancel;
	}

	@Override
	public void execute()
	{
		if (changeProgress)
		{
			if (swipe != null) swipe.setRefreshing(true);
		}

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
							if (changeProgress)
							{
								if (swipe != null) swipe.setRefreshing(false);
							}

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
							if (changeProgress)
							{
								if (swipe != null) swipe.setRefreshing(false);
							}

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
		// TODO: Remove
		getHandler().post(new Runnable()
		{
			@Override
			public void run()
			{
				//activity.updateProgress(message);
			}
		});
	}
}
