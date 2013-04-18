package com.thejoshwa.ultrasonic.androidapp.receiver;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory.Entry;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadService;
import com.thejoshwa.ultrasonic.androidapp.service.DownloadServiceImpl;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class A2dpIntentReceiver extends BroadcastReceiver {
	
    private static final String PLAYSTATUS_REQUEST = "com.android.music.playstatusrequest";
    private static final String PLAYSTATUS_RESPONSE = "com.android.music.playstatusresponse";

	@Override
	public void onReceive(Context context, Intent intent) {
		
		DownloadService downloadService = DownloadServiceImpl.getInstance();
		
		if (downloadService != null){
			Intent avrcpIntent = new Intent(PLAYSTATUS_RESPONSE);
			
			avrcpIntent.putExtra("duration", (long) downloadService.getCurrentPlaying().getSong().getDuration());
			avrcpIntent.putExtra("position", (long) downloadService.getPlayerPosition());
			avrcpIntent.putExtra("ListSize", (long) downloadService.getDownloads().size());
						
			switch (downloadService.getPlayerState()){
				case STARTED:
	            	avrcpIntent.putExtra("playing", true);
	                break;
	            case STOPPED:
	            	avrcpIntent.putExtra("playing", false);
	                break;
	            case PAUSED:
	            	avrcpIntent.putExtra("playing", false);
	                break;
	            case COMPLETED:
	            	avrcpIntent.putExtra("playing", false);
	                break;
	            default:
	                return;
			}			
		
			context.sendBroadcast(avrcpIntent);
		}
	}
}