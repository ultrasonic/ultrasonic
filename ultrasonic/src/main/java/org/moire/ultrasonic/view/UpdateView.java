package org.moire.ultrasonic.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.LinearLayout;

import org.moire.ultrasonic.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.WeakHashMap;

import timber.log.Timber;

/**
 * A View that is periodically refreshed
 * @deprecated
 * Use LiveData to ensure that the content is up-to-date
 **/
public class UpdateView extends LinearLayout
{
	private static final WeakHashMap<UpdateView, ?> INSTANCES = new WeakHashMap<UpdateView, Object>();

	private static Handler backgroundHandler;
	private static Handler uiHandler;
	private static Runnable updateRunnable;

	public UpdateView(Context context)
	{
		super(context);

		setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		INSTANCES.put(this, null);
		startUpdater();
	}

	@Override
	public void setPressed(boolean pressed)
	{

	}

	private static synchronized void startUpdater()
	{
		if (uiHandler != null)
		{
			return;
		}

		uiHandler = new Handler();
		updateRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				updateAll();
			}
		};

		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Thread.currentThread().setName("startUpdater");
				Looper.prepare();
				backgroundHandler = new Handler(Looper.myLooper());
				uiHandler.post(updateRunnable);
				Looper.loop();
			}
		}).start();
	}

	private static void updateAll()
	{
		try
		{
			Collection<UpdateView> views = new ArrayList<UpdateView>();

			for (UpdateView view : INSTANCES.keySet())
			{
				if (view.isShown())
				{
					views.add(view);
				}
			}

			updateAllLive(views);
		}
		catch (Throwable x)
		{
			Timber.w(x, "Error when updating song views.");
		}
	}

	private static void updateAllLive(final Iterable<UpdateView> views)
	{
		final Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					for (UpdateView view : views)
					{
						view.update();
					}
				}
				catch (Throwable x)
				{
					Timber.w(x, "Error when updating song views.");
				}

				uiHandler.postDelayed(updateRunnable, Util.getViewRefreshInterval());
			}
		};

		backgroundHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					Thread.currentThread().setName("updateAllLive-Background");

					for (UpdateView view : views)
					{
						view.updateBackground();
					}
					uiHandler.post(runnable);
				}
				catch (Throwable x)
				{
					Timber.w(x, "Error when updating song views.");
				}
			}
		});
	}

	protected void updateBackground()
	{

	}

	protected void update()
	{

	}
}
