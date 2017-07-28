package com.example.audiorecorder;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.LinearLayoutCompat;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.android.AndroidFFMPEGLocator;
import be.tarsos.dsp.mfcc.MFCC;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

public class MainActivity extends AppCompatActivity implements ResponseReceiver.Receiver {

    private static final String LOG_TAG = "AudioRecordTest";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    public static final String TAG = "com.example.audiorecorder";
    private static String mFileNameBase = null;

    private RecordButton mRecordButton = null;
    private MediaRecorder mRecorder = null;

    private PlayButton   mPlayButton = null;
    private MediaPlayer   mPlayer = null;

    private MFCCButton mMFCCButton = null;
    private AppCompatEditText mNewFileField = null;
    private NewFileButton mNewFileButton = null;

    private TextField mTextField = null;
    private StreamButton mStreamButton = null;
    private RecordTimeField mRecordTimeField = null;
    private TextView mResultsView = null;
    private ListView mListView = null;
    private ArrayAdapter<String> arrayAdapter = null;
    private ResponseReceiver mReceiver;

    private List<float[]> all_mfccs = new ArrayList<>();
    private MFCCData[] mfcc_stats = new MFCCData[12];

    // Requesting permission to RECORD_AUDIO
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    private void onRecord(boolean start) {
        if (start) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void onPlay(boolean start) {
        if (start) {
            startPlaying();
        } else {
            stopPlaying();
        }
    }

    private void startPlaying() {

        mPlayer = new MediaPlayer();

        String mFileName = mFileNameBase + "default.mp4";
        if (! mTextField.getSelectedItem().toString().equals("")) {
            mFileName = mFileNameBase + "/" + mTextField.getSelectedItem().toString() + ".mp4";
        }

        if (! (new File(mFileName)).exists()) {
            AlertDialog.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
            } else {
                builder = new AlertDialog.Builder(this);
            }
            builder.setTitle(R.string.dialog_title)
                    .setMessage(String.format(getString(R.string.dialog_message), (mTextField.getSelectedItem().toString() + ".mp4")))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            mRecordButton.setEnabled(true);
                            mPlayButton.setText(R.string.playStart);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return;
        }

        try {
            mPlayer.setDataSource(mFileName);
            mPlayer.prepare();
            mPlayer.start();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
    }

    private void startRecording() {

        String mFileName = mFileNameBase + "default.mp4";
        if (! mTextField.getSelectedItem().toString().equals("")) {
            mFileName = mFileNameBase + "/" + mTextField.getSelectedItem() + ".mp4";
        }
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setAudioEncodingBitRate(96000);
        mRecorder.setAudioSamplingRate(44100);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }

        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;

    }

    private void onMFCC() {

        int sampleRate = 44100;
        int bufferSize = 1024;
        int bufferOverlap = 512;
        //final AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050,1024,512);
        String path = mFileNameBase + "/" + mTextField.getSelectedItem().toString() + ".mp4";
        new AndroidFFMPEGLocator(this);
        //final AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(path, sampleRate, samplesPerFrame, bufferOverlap);
        InputStream inStream;

        try {
            inStream = new FileInputStream(path);
        } catch (java.io.IOException e) {
            AlertDialog.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
            } else {
                builder = new AlertDialog.Builder(this);
            }
            builder.setTitle(R.string.dialog_title)
                    .setMessage(String.format(getString(R.string.dialog_message), (mTextField.getSelectedItem().toString() + ".mp4")))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            mRecordButton.setEnabled(true);
                            mPlayButton.setText(R.string.playStart);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return;
        }


        AudioDispatcher dispatcher = new AudioDispatcher(new UniversalAudioInputStream(inStream, new TarsosDSPAudioFormat(sampleRate, 16, 1, true, true)), bufferSize, bufferOverlap);
        final MFCC mfcc = new MFCC(bufferSize, sampleRate, 13, 40, 300, 3000);

        try {
            dispatcher.addAudioProcessor(mfcc);
        } catch (Exception e) {
            e.printStackTrace();
        }

        dispatcher.addAudioProcessor(new AudioProcessor() {
            int count_process = 0;

            @Override
            public void processingFinished() {
                float[][] mfccs_by_num = new float[12][all_mfccs.size()];
                for (int i = 0; i < all_mfccs.size(); i++) {
                    for (int j = 0; j < 12; j++) {
                        mfccs_by_num[j][i] = all_mfccs.get(i)[j];
                    }
                }
                for (int i = 0; i < 12; i++) {
                    mfcc_stats[i] = new MFCCData(mfccs_by_num[i]);  // this is only for the first small window???
                }
                System.out.println("DONE");
            }

            @Override
            public boolean process(AudioEvent audioEvent) {
                all_mfccs.add(Arrays.copyOfRange(mfcc.getMFCC(), 1, 13));
                return true;
            }
        });
        dispatcher.run();
        //mfcc.process(new AudioEvent(new TarsosDSPAudioFormat(sampleRate, samplesPerFrame, 1, true, true)));

    }

    private void addFileAndRecord() {
        String text = mNewFileField.getText().toString();
        if (text.equals("")) {
            AlertDialog.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
            } else {
                builder = new AlertDialog.Builder(this);
            }
            builder.setTitle(R.string.emptyEntry)
                    .setMessage(R.string.enterValue)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return;
        }
        mTextField.fileStrings.add(text);
        mTextField.setSelection(mTextField.adapter.getPosition(text));
        mNewFileField.setText("");
    }

    class MFCCData {
        float max;
        float min;
        float range;
        int maxPos;
        int minPos;
        double amean;
        double stddev;
        double skewness;
        double kurtosis;
        double quartile1;
        double quartile2;
        double quartile3;
        double iqr1_2;
        double iqr2_3;
        double iqr1_3;

        public double[] floatToDoubleArr(float[] arr) {
            double[] result = new double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                result[i] = arr[i];
            }
            return result;
        }

        public MFCCData(float[] mfcc) {
            this.max = Collections.max(Arrays.asList(ArrayUtils.toObject(mfcc)));
            this.min = Collections.min(Arrays.asList(ArrayUtils.toObject(mfcc)));
            this.range = this.max - this.min;
            this.maxPos = java.util.Arrays.asList(mfcc).indexOf(this.max);
            this.minPos = java.util.Arrays.asList(mfcc).indexOf(this.min);

            double[] mfcc_double = this.floatToDoubleArr(mfcc);
            this.amean = (new Mean()).evaluate(mfcc_double);
            this.stddev = (new StandardDeviation()).evaluate(mfcc_double);
            this.skewness = (new Skewness()).evaluate(mfcc_double);
            this.kurtosis = (new Kurtosis()).evaluate(mfcc_double);
            this.quartile1 = (new Percentile(25)).evaluate(mfcc_double);
            this.quartile2 = (new Percentile(50)).evaluate(mfcc_double);
            this.quartile3 = (new Percentile(75)).evaluate(mfcc_double);
            this.iqr1_2 = this.quartile2 - this.quartile1;
            this.iqr2_3 = this.quartile3 - this.quartile2;
            this.iqr1_3 = this.quartile3 - this.quartile1;
        }
    }

    class RecordButton extends android.support.v7.widget.AppCompatButton {
        boolean mStartRecording = true;

        OnClickListener clicker = new OnClickListener() {
            public void onClick(View v) {
                onRecord(mStartRecording);
                if (mStartRecording) {  // if app is not recording, it will start recording
                    mPlayButton.setEnabled(false);  // disable play button
                    setText(R.string.recordStop);
                } else {  // if app is recording, it will stop recording
                    mPlayButton.setEnabled(true); // enable play button
                    setText(R.string.recordStart);
                }
                mStartRecording = !mStartRecording;
            }
        };

        public RecordButton(Context ctx) {
            super(ctx);
            setText(R.string.recordStart);
            setOnClickListener(clicker);
        }
    }

    class PlayButton extends android.support.v7.widget.AppCompatButton {
        boolean mStartPlaying = true;

        OnClickListener clicker = new OnClickListener() {
            public void onClick(View v) {
                onPlay(mStartPlaying);
                if (mStartPlaying) {  // if sound is not currently playing, start playing
                    mRecordButton.setEnabled(false);  // disable record button
                    setText(R.string.playStop);
                } else {  // if sound is playing, stop playing
                    mRecordButton.setEnabled(true); // enable record button
                    setText(R.string.playStart);
                }
                mStartPlaying = !mStartPlaying;
            }
        };

        public PlayButton(Context ctx) {
            super(ctx);
            setText(R.string.playStart);
            setOnClickListener(clicker);
        }
    }

    class MFCCButton extends android.support.v7.widget.AppCompatButton {
        OnClickListener clicker = new OnClickListener() {
            @Override
            public void onClick(View view) {
                onMFCC();
            }
        };

        public MFCCButton(Context ctx) {
            super(ctx);
            setText(R.string.mfccText);
            setOnClickListener(clicker);
        }
    }

    class NewFileButton extends android.support.v7.widget.AppCompatButton {
        OnClickListener clicker = new OnClickListener() {
            @Override
            public void onClick(View view) {
                addFileAndRecord();
            }
        };

        public NewFileButton(Context context) {
            super(context);
            setText(R.string.addFile);
            setOnClickListener(clicker);
        }
    }

    class TextField extends android.support.v7.widget.AppCompatSpinner {

        ArrayList<String> fileStrings = new ArrayList<>();
        ArrayAdapter<String> adapter;

        public TextField(Context ctx) {
            super(ctx);
            File cacheDir = new File(mFileNameBase);
            File[] files = cacheDir.listFiles();
            for (File file : files) {
                String nameFull = file.getName();
                fileStrings.add(nameFull.substring(0, nameFull.length() - 4));
            }

            adapter = new ArrayAdapter<>
                    (ctx,android.R.layout.select_dialog_item, fileStrings);
            this.setAdapter(adapter);
        }
    }

    class StreamButton extends android.support.v7.widget.AppCompatButton {
        OnClickListener clicker = new OnClickListener() {
            @Override
            public void onClick(View view) {
                /* Starting Download Service */
                mReceiver = new ResponseReceiver(new Handler());
                mReceiver.setReceiver(MainActivity.this);
                Intent intent = new Intent(Intent.ACTION_SYNC, null, MainActivity.this, StreamService.class);

                /* Send optional extras to Download IntentService */
                intent.putExtra("text", mTextField.getSelectedItem().toString());
                intent.putExtra("time", mRecordTimeField.getText().toString());
                intent.putExtra("receiver", mReceiver);
                intent.putExtra("requestId", 101);

                startService(intent);
            }
        };

        public StreamButton(Context ctx) {
            super(ctx);
            setText(R.string.streamText);
            setOnClickListener(clicker);
        }
    }

    class RecordTimeField extends android.support.v7.widget.AppCompatEditText {

        public RecordTimeField(Context ctx) {
            super(ctx);
            setHint("Seconds for Mic Service");
            setInputType(InputType.TYPE_CLASS_NUMBER);
        }
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        switch (resultCode) {
            case StreamService.STATUS_RUNNING:
                mResultsView.setText(R.string.statusRunning);
                break;
            case StreamService.STATUS_FINISHED:
                mResultsView.setText(R.string.finished);
                String[] results = resultData.getStringArray("result");

                /* Update ListView with result */
                arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, results);
                mListView.setAdapter(arrayAdapter);

                break;
            case StreamService.STATUS_ERROR:
                /* Handle the error */
                String error = resultData.getString(Intent.EXTRA_TEXT);
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                break;
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Record to the external cache directory for visibility
        //storage/emulated/0/Android/data/com.example.audiorecorder/cache/audiorecordtest.3gp
        mFileNameBase = getExternalCacheDir().getAbsolutePath();

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        LinearLayout ll = new LinearLayout(this);

        LinearLayout recordAndPlay = new LinearLayout(this);

        mRecordButton = new RecordButton(this);
        recordAndPlay.addView(mRecordButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        mPlayButton = new PlayButton(this);
        recordAndPlay.addView(mPlayButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        mMFCCButton = new MFCCButton(this);
        recordAndPlay.addView(mMFCCButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        recordAndPlay.setOrientation(LinearLayout.HORIZONTAL);
        ll.addView(recordAndPlay,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));

        mTextField = new TextField(this);
        ll.addView(mTextField,
                new LinearLayout.LayoutParams(
                        600,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));

        LinearLayout addFile = new LinearLayout(this);
        mNewFileField = new AppCompatEditText(this);
        mNewFileField.setHint("Add new file");
        addFile.addView(mNewFileField,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        mNewFileButton = new NewFileButton(this);
        addFile.addView(mNewFileButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        addFile.setOrientation(LinearLayout.HORIZONTAL);
        ll.addView(addFile,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));

        LinearLayout streamLl = new LinearLayout(this);
        mRecordTimeField = new RecordTimeField(this);
        streamLl.addView(mRecordTimeField,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1));
        mStreamButton = new StreamButton(this);
        streamLl.addView(mStreamButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        streamLl.setOrientation(LinearLayout.HORIZONTAL);
        ll.addView(streamLl,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                0));
        mResultsView = new TextView(this);
        mResultsView.setText(R.string.results);
        ll.addView(mResultsView,
                new LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                0));
        mListView = new ListView(this);
        try {
            mListView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new String[]{"Result 1", "Result 2", "Result 3"}));
        } catch (Exception e) {
            Log.d(TAG, "Printed stack trace");
            e.printStackTrace();
        }
        ll.addView(mListView,
                new LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        ll.setOrientation(LinearLayout.VERTICAL);
        setContentView(ll);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

}