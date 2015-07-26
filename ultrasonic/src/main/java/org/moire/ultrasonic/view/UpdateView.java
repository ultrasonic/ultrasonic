package org.moire.ultrasonic.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.LinearLayout;

import org.moire.ultrasonic.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.WeakHashMap;

public class UpdateView extends LinearLayout
{
	private static final String TAG = UpdateView.class.getSimpleName();
	private static final WeakHashMap<UpdateView, ?> INSTANCES = new WeakHashMap<UpdateView, Object>();

	private static Handler backgroundHandler;
	private static Handler uiHandler;
	private static Runnable updateRunnable;
	private static Context context;

	public UpdateView(Context context)
	{
		super(context);
		UpdateView.context = context;

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
			Log.w(TAG, "Error when updating song views.", x);
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
					Log.w(TAG, "Error when updating song views.", x);
				}

				uiHandler.postDelayed(updateRunnable, Util.getViewRefreshInterval(context));
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
					Log.w(TAG, "Error when updating song views.", x);
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
