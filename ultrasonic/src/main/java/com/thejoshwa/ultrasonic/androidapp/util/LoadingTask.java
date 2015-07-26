package com.thejoshwa.ultrasonic.androidapp.util;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Build;

import com.thejoshwa.ultrasonic.androidapp.activity.SubsonicTabActivity;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public abstract class LoadingTask<T> extends BackgroundTask<T>
{
	private final SubsonicTabActivity tabActivity;
	private final boolean cancellable;
	private boolean cancelled;

	public LoadingTask(SubsonicTabActivity activity, final boolean cancellable)
	{
		super(activity);
		tabActivity = activity;
		this.cancellable = cancellable;
	}

	@Override
	public void execute()
	{
		final ProgressDialog loading = ProgressDialog.show(tabActivity, "", "Loading. Please Wait...", true, cancellable, new DialogInterface.OnCancelListener()
		{
			@Override
			public void onCancel(DialogInterface dialog)
			{
				cancelled = true;
			}

		});

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
							loading.cancel();
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
							loading.cancel();
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
		return Build.VERSION.SDK_INT >= 17 ? tabActivity.isDestroyed() || cancelled : cancelled;
	}

	@Override
	public void updateProgress(final String message)
	{
		getHandler().post(new Runnable()
		{
			@Override
			public void run()
			{

			}
		});
	}
}