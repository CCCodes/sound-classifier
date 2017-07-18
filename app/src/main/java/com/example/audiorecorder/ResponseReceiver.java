package com.example.audiorecorder;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.widget.ArrayAdapter;
import android.widget.Toast;

public class ResponseReceiver extends ResultReceiver {

    public static final String ACTION_RESP =
            "com.example.intent.action.MESSAGE_PROCESSED";

    private Receiver mReceiver;

    public ResponseReceiver(Handler handler) {
        super(handler);
    }

    public void setReceiver(Receiver receiver) {
        mReceiver = receiver;
    }

    public interface Receiver {
        void onReceiveResult(int resultCode, Bundle resultData);
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (mReceiver != null) {
            mReceiver.onReceiveResult(resultCode, resultData);
        }
    }
}
