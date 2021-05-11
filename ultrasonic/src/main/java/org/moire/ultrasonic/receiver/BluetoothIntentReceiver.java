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
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import timber.log.Timber;

import org.moire.ultrasonic.util.Constants;
import org.moire.ultrasonic.util.Util;

/**
 * Resume or pause playback on Bluetooth A2DP connect/disconnect.
 *
 * @author Sindre Mehus
 */
public class BluetoothIntentReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		String action = intent.getAction();
		String name = device != null ? device.getName() : "Unknown";
		String address = device != null ? device.getAddress() : "Unknown";

		Timber.d("A2DP State: %d; Action: %s; Device: %s; Address: %s", state, action, name, address);

		boolean actionBluetoothDeviceConnected = false;
		boolean actionBluetoothDeviceDisconnected = false;
		boolean actionA2dpConnected = false;
		boolean actionA2dpDisconnected = false;

		if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
		{
			actionBluetoothDeviceConnected = true;
		}
		else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) || BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action))
		{
			actionBluetoothDeviceDisconnected = true;
		}

		if (state == android.bluetooth.BluetoothA2dp.STATE_CONNECTED) actionA2dpConnected = true;
		else if (state == android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED) actionA2dpDisconnected = true;

		boolean resume = false;
		boolean pause = false;

		switch (Util.getResumeOnBluetoothDevice())
		{
			case Constants.PREFERENCE_VALUE_ALL: resume = actionA2dpConnected || actionBluetoothDeviceConnected;
				break;
			case Constants.PREFERENCE_VALUE_A2DP: resume = actionA2dpConnected;
				break;
		}

		switch (Util.getPauseOnBluetoothDevice())
		{
			case Constants.PREFERENCE_VALUE_ALL: pause = actionA2dpDisconnected || actionBluetoothDeviceDisconnected;
				break;
			case Constants.PREFERENCE_VALUE_A2DP: pause = actionA2dpDisconnected;
				break;
		}

		if (resume)
		{
			Timber.i("Connected to Bluetooth device %s address %s, resuming playback.", name, address);
			context.sendBroadcast(new Intent(Constants.CMD_RESUME_OR_PLAY).setPackage(context.getPackageName()));
		}

		if (pause)
		{
			Timber.i("Disconnected from Bluetooth device %s address %s, requesting pause.", name, address);
			context.sendBroadcast(new Intent(Constants.CMD_PAUSE).setPackage(context.getPackageName()));
		}
	}
}
