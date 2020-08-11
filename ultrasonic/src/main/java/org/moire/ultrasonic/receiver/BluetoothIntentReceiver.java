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

 Copyright 2010 (C) Sindre Mehus
 */
package org.moire.ultrasonic.receiver;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Util;

/**
 * Request media button focus when connected to Bluetooth A2DP.
 *
 * @author Sindre Mehus
 */
public class BluetoothIntentReceiver extends BroadcastReceiver
{

	private static final String TAG = BluetoothIntentReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent)
	{
		int state = intent.getIntExtra("android.bluetooth.a2dp.extra.SINK_STATE", -1);
		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		String action = intent.getAction();
		String name = device != null ? device.getName() : "None";

		Log.d(TAG, String.format("Sink State: %d; Action: %s; Device: %s", state, action, name));

		boolean actionConnected = false;
		boolean actionDisconnected = false;

		if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
		{
			actionConnected = true;
		}
		else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) || BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action))
		{
			actionDisconnected = true;
		}

		boolean connected = state == android.bluetooth.BluetoothA2dp.STATE_CONNECTED || actionConnected;
		boolean disconnected = state == android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED || actionDisconnected;

		if (connected)
		{
			Log.i(TAG, "Connected to Bluetooth device, requesting media button focus.");
			Util.registerMediaButtonEventReceiver(context, false);
		}

		if (disconnected)
		{
			Log.i(TAG, "Disconnected from Bluetooth device, requesting pause.");
			context.sendBroadcast(new Intent(Constants.CMD_PAUSE));
		}
	}
}
