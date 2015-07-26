package org.moire.ultrasonic.util;

import java.util.Calendar;
import java.util.Date;

public class TimeSpan
{
	public static final int MILLISECONDS_IN_DAY = 86400000;
	public static final int MILLISECONDS_IN_HOUR = 3600000;
	public static final int MILLISECONDS_IN_MINUTE = 60000;
	public static final int MILLISECONDS_IN_SECOND = 1000;
	public static final int SECONDS_IN_MINUTE = 60;
	public static final int MINUTES_IN_HOUR = 60;
	public static final int HOURS_IN_DAY = 24;

	private long totalMilliseconds;
	private int milliseconds;
	private int seconds;
	private int minutes;
	private int hours;
	private int days;

  public TimeSpan(long milliseconds)
	{
		this.totalMilliseconds = milliseconds;
		this.milliseconds = (int) (milliseconds % MILLISECONDS_IN_SECOND);
		milliseconds /= MILLISECONDS_IN_SECOND;
		this.seconds = (int) (milliseconds % SECONDS_IN_MINUTE);
		milliseconds /= SECONDS_IN_MINUTE;
		this.minutes = (int) (milliseconds % MINUTES_IN_HOUR);
		milliseconds /= MINUTES_IN_HOUR;
		this.hours = (int) (milliseconds % HOURS_IN_DAY);
		milliseconds /= HOURS_IN_DAY;
		this.days = (int) milliseconds;
	}

	public static TimeSpan create(int minutes, int seconds)
	{
		long totalMilliseconds = (seconds * MILLISECONDS_IN_SECOND);
		totalMilliseconds += (minutes * MILLISECONDS_IN_MINUTE);

		return new TimeSpan(totalMilliseconds);
	}

	public static TimeSpan create(int hours, int minutes, int seconds)
	{
		long totalMilliseconds = (seconds * MILLISECONDS_IN_SECOND);
		totalMilliseconds += (minutes * MILLISECONDS_IN_MINUTE);
		totalMilliseconds += (hours * MILLISECONDS_IN_HOUR);

		return new TimeSpan(totalMilliseconds);
	}

	public static TimeSpan create(long days, long hours, long minutes, long seconds)
	{
		long totalMilliseconds = (seconds * MILLISECONDS_IN_SECOND);
		totalMilliseconds += (minutes * MILLISECONDS_IN_MINUTE);
		totalMilliseconds += (hours * MILLISECONDS_IN_HOUR);
		totalMilliseconds += (days * MILLISECONDS_IN_DAY);

		return new TimeSpan(totalMilliseconds);
	}

	public static TimeSpan create(long days, long hours, long minutes, long seconds, long milliseconds)
	{
		long totalMilliseconds = milliseconds;
		totalMilliseconds += (seconds * MILLISECONDS_IN_SECOND);
		totalMilliseconds += (minutes * MILLISECONDS_IN_MINUTE);
		totalMilliseconds += (hours * MILLISECONDS_IN_HOUR);
		totalMilliseconds += (days * MILLISECONDS_IN_DAY);

		return new TimeSpan(totalMilliseconds);
	}

	public static TimeSpan create(Calendar cal)
	{
		return new TimeSpan(cal.getTimeInMillis());
	}

	public static TimeSpan create(Date date)
	{
		return new TimeSpan(date.getTime());
	}

	public static TimeSpan getCurrentTime()
	{
		return new TimeSpan(System.currentTimeMillis());
	}

	public int getDays()
	{
		return days;
	}

	public int getHours()
	{
		return hours;
	}

	public int getMinutes()
	{
		return minutes;
	}

	public int getSeconds()
	{
		return seconds;
	}

	public int getMilliseconds()
	{
		return milliseconds;
	}

	public double getTotalDays()
	{
		return totalMilliseconds / (double) (MILLISECONDS_IN_DAY);
	}

	public double getTotalHours()
	{
		return totalMilliseconds / (double) (MILLISECONDS_IN_HOUR);
	}

	public double getTotalMinutes()
	{
		return totalMilliseconds / (double) (MILLISECONDS_IN_MINUTE);
	}

	public double getTotalSeconds()
	{
		return totalMilliseconds / (double) (MILLISECONDS_IN_SECOND);
	}

	public long getTotalMilliseconds()
	{
		return totalMilliseconds;
	}

	public TimeSpan add(TimeSpan ts)
	{
		return new TimeSpan(this.totalMilliseconds + ts.totalMilliseconds);
	}

	public TimeSpan add(long milliseconds)
	{
		return new TimeSpan(this.totalMilliseconds + milliseconds);
	}

	public TimeSpan subtract(TimeSpan ts)
	{
		return new TimeSpan(this.totalMilliseconds - ts.totalMilliseconds);
	}

	public static int compare(TimeSpan t1, TimeSpan t2)
	{
		if (t1.totalMilliseconds < t2.totalMilliseconds)
		{
			return -1;
		}
		else
		{
			return t1.totalMilliseconds == t2.totalMilliseconds ? 0 : 1;
		}
	}
}