/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package org.moire.ultrasonic.util;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import com.fasterxml.jackson.core.JsonParseException;
import org.moire.ultrasonic.R;
import org.moire.ultrasonic.service.SubsonicRESTException;
import org.moire.ultrasonic.subsonic.RestErrorMapper;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;

/**
 * @author Sindre Mehus
 */
public abstract class BackgroundTask<T> implements ProgressListener
{

	private static final String TAG = BackgroundTask.class.getSimpleName();
	private final Activity activity;
	private final Handler handler;

	public BackgroundTask(Activity activity)
	{
		this.activity = activity;
		handler = new Handler();
	}

	protected Activity getActivity()
	{
		return activity;
	}

	protected Handler getHandler()
	{
		return handler;
	}

	public abstract void execute();

	protected abstract T doInBackground() throws Throwable;

	protected abstract void done(T result);

	protected void error(Throwable error)
	{
		Log.w(TAG, String.format("Got exception: %s", error), error);
		new ErrorDialog(activity, getErrorMessage(error), false);
	}

    protected String getErrorMessage(Throwable error) {
        if (error instanceof IOException && !Util.isNetworkConnected(activity)) {
            return activity.getResources().getString(R.string.background_task_no_network);
        } else if (error instanceof FileNotFoundException) {
            return activity.getResources().getString(R.string.background_task_not_found);
        } else if (error instanceof JsonParseException) {
            return activity.getResources().getString(R.string.background_task_parse_error);
        } else if (error instanceof SSLException) {
            if (error.getCause() instanceof CertificateException &&
                    error.getCause().getCause() instanceof CertPathValidatorException) {
                return activity.getResources()
                        .getString(R.string.background_task_ssl_cert_error,
                                error.getCause().getCause().getMessage());
            } else {
                return activity.getResources().getString(R.string.background_task_ssl_error);
            }
        } else if (error instanceof IOException) {
            return activity.getResources().getString(R.string.background_task_network_error);
        } else if (error instanceof SubsonicRESTException) {
            return RestErrorMapper.getLocalizedErrorMessage((SubsonicRESTException) error, activity);
        }

        String message = error.getMessage();
        if (message != null) {
            return message;
        }
        return error.getClass().getSimpleName();
    }

	@Override
	public abstract void updateProgress(final String message);

	@Override
	public void updateProgress(int messageId)
	{
		updateProgress(activity.getResources().getString(messageId));
	}
}