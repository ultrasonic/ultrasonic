/*
 * MainThreadExecutor.java
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

/*
* Executor for running Futures on the main thread
* See https://stackoverflow.com/questions/52642246/how-to-get-executor-for-main-thread-on-api-level-28
*/
public class MainThreadExecutor implements Executor {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable r) {
        handler.post(r);
    }
}
