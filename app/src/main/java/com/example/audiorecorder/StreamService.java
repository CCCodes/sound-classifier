package com.example.audiorecorder;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;

public class StreamService extends IntentService {


    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_ERROR = 2;

    private static final String TAG = "StreamService";

    public StreamService() {
        super(StreamService.class.getName());
    }

    @Override
    protected void onHandleIntent(Intent i) {
        Log.d(TAG, "Service Started!");

        final ResultReceiver receiver = i.getParcelableExtra("receiver");
        String text = i.getStringExtra("text");
        String time = i.getStringExtra("time");

        Bundle bundle = new Bundle();

        if (!TextUtils.isEmpty(text)) {
            /* Update UI: Download Service is Running */
            receiver.send(STATUS_RUNNING, Bundle.EMPTY);

            try {
                String[] results = {text, time};
                Log.d(TAG, text + time);


                if (null != results && results.length > 0) {
                    bundle.putStringArray("result", results);
                    receiver.send(STATUS_FINISHED, bundle);
                }
            } catch (Exception e) {

                /* Sending error message back to activity */
                bundle.putString(Intent.EXTRA_TEXT, e.toString());
                receiver.send(STATUS_ERROR, bundle);
            }
        }
        Log.d(TAG, "Service Stopping!");
        this.stopSelf();
    }


}
