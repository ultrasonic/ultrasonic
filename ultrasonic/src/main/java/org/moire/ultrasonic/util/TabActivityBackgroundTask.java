package org.moire.ultrasonic.util;

import org.moire.ultrasonic.activity.SubsonicTabActivity;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public abstract class TabActivityBackgroundTask<T> extends BackgroundTask<T>
{

	private final SubsonicTabActivity tabActivity;
	private final boolean changeProgress;

	public TabActivityBackgroundTask(SubsonicTabActivity activity, boolean changeProgress)
	{
		super(activity);
		tabActivity = activity;
		this.changeProgress = changeProgress;
	}

	@Override
	public void execute()
	{
		if (changeProgress)
		{
			tabActivity.setProgressVisible(true);
		}

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
							if (changeProgress)
							{
								tabActivity.setProgressVisible(false);
							}

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
							if (changeProgress)
							{
								tabActivity.setProgressVisible(false);
							}

							error(t);
						}
					});
				}
			}
		}.start();
	}

	private boolean isCancelled()
	{
		return tabActivity.getIsDestroyed();
	}

	@Override
	public void updateProgress(final String message)
	{
		getHandler().post(new Runnable()
		{
			@Override
			public void run()
			{
				tabActivity.updateProgress(message);
			}
		});
	}
}
