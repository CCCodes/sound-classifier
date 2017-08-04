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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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

    private TextField mTextField = null;
    private DeleteButton mDeleteButton = null;
    private SelectMFCCButton mSelectMFCCButton = null;

    private AppCompatEditText mNewFileField = null;
    private NewFileButton mNewFileButton = null;

    private RecordTimeField mRecordTimeField = null;
    private StreamButton mStreamButton = null;

    private TextView mResultsView = null;
    private ListView mListView = null;
    private ArrayAdapter<String> arrayAdapter = null;
    private ResponseReceiver mReceiver;

    private List<float[]> all_mfccs = new ArrayList<>();
    private MFCCData[] mfcc_stats = new MFCCData[12];

    private List<float[]> all_mfccs_select = new ArrayList<>();

    private OutputStream outputStream = null;
    private OutputStreamWriter outputStreamWriter = null;

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

        String mFileName = mFileNameBase + "/" + "default.mp4";
        if (! mTextField.getSelectedItem().toString().equals("")) {
            mFileName = mFileNameBase + "/" + mTextField.getSelectedItem().toString() + ".mp4";
        }

        if (! (new File(mFileName)).exists()) {
            fileNotFoundMessage(mTextField.getSelectedItem().toString() + ".mp4");
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

        String mFileName = mFileNameBase + "/" + "default.mp4";
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
        //String path = mFileNameBase + "/" + mTextField.getSelectedItem().toString() + ".mp4";

        String path = mFileNameBase + "/barks/";
        File barkDir = new File(path);
        File[] barkDirFiles = barkDir.listFiles();

        new AndroidFFMPEGLocator(this);

        final String[] dirPaths = new String[barkDirFiles.length];

        for (int i = 0; i < barkDirFiles.length; i++) {
            String filePath = barkDirFiles[i].toString();
            dirPaths[i] = filePath.substring(filePath.lastIndexOf("/") + 1, filePath.length() - 6);
        }

        try {
            outputStream = new FileOutputStream(mFileNameBase+"/output.arff");
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
        outputStreamWriter = new OutputStreamWriter(outputStream);

        try {
            String classes = TextUtils.join(", ", dirPaths);

            outputStreamWriter.write(readFromFile(MainActivity.this, "assimilated_arff_base.txt")+classes+"}\n@data\n");
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }

        for (int i = 0 ; i < barkDirFiles.length; i++) {
            final int j = i;

            for (File f : barkDirFiles[i].listFiles()) {
                InputStream inStream;

                try {
                    inStream = new FileInputStream(f);
                } catch (java.io.IOException e) {
                    fileNotFoundMessage(mTextField.getSelectedItem().toString() + ".mp4");
                    return;
                }
                AudioDispatcher dispatcher = new AudioDispatcher(new UniversalAudioInputStream(inStream,
                        new TarsosDSPAudioFormat(sampleRate, 16, 1, true, true)), bufferSize, bufferOverlap);
                final MFCC mfcc = new MFCC(bufferSize, sampleRate, 13, 40, 300, 3000);

                try {
                    dispatcher.addAudioProcessor(mfcc);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("Current file: " + f);
                dispatcher.addAudioProcessor(new AudioProcessor() {

                    @Override
                    public void processingFinished() {
                        float[][] mfccs_by_num = new float[12][all_mfccs.size()];
                        for (int i = 0; i < all_mfccs.size(); i++) {
                            for (int j = 0; j < 12; j++) {
                                mfccs_by_num[j][i] = all_mfccs.get(i)[j];
                            }
                        }
                        // write to file here - will happen for however many files there are in the
                        // subdirectories of bark (in cache)
                        try {
                            OutputStream outputStream1 = new FileOutputStream(mFileNameBase + "/raw_mfccs.txt");
                            OutputStreamWriter outputStreamWriter1 = new OutputStreamWriter(outputStream1);

                            StringBuilder mfcc_sb = new StringBuilder();
                            for (float[] mfcc_set : all_mfccs) {
                                List<Float> mfcc_set_list = new ArrayList<>();
                                for (float mfcc_instance :mfcc_set) {
                                    mfcc_set_list.add(mfcc_instance);
                                }
                                mfcc_sb.append(TextUtils.join(",", mfcc_set_list)).append("\n");
                            }
                            outputStreamWriter1.write(mfcc_sb.toString());
                            outputStreamWriter1.close();
                        } catch (IOException e) {
                            Log.e("Exception", "MFCC File write failed: " + e.toString());
                        }
                        for (int i = 0; i < 12; i++) {
                            mfcc_stats[i] = new MFCCData(mfccs_by_num[i]);
                        }
                        List<String> mfcc_instance = new ArrayList<>();
                        for (MFCCData data : mfcc_stats) {
                            mfcc_instance.add(data.toString());
                        }

                        String[] mfcc_instance_arr = new String[mfcc_instance.size()];
                        mfcc_instance_arr = mfcc_instance.toArray(mfcc_instance_arr);
                        try {
                            outputStreamWriter.write(TextUtils.join(",", mfcc_instance_arr) + "," + dirPaths[j] +"\n");
                        } catch (IOException e) {
                            Log.e("Exception", "File write failed: " + e.toString());
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
            }

        }

        try {
            outputStreamWriter.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }

    }

    private void onSelectMFCC() {

        int sampleRate = 44100;
        int bufferSize = 1024;
        int bufferOverlap = 512;

        String f = mFileNameBase + "/" + mTextField.getSelectedItem().toString() + ".mp4";
        InputStream inStream;

        try {
            inStream = new FileInputStream(f);
        } catch (java.io.IOException e) {
            Log.e("Exception", "Could not read input file: " + f);
            return;
        }
        AudioDispatcher dispatcher = new AudioDispatcher(new UniversalAudioInputStream(inStream,
                new TarsosDSPAudioFormat(sampleRate, 16, 1, true, true)), bufferSize, bufferOverlap);
        final MFCC mfcc = new MFCC(bufferSize, sampleRate, 13, 40, 300, 3000);
        dispatcher.addAudioProcessor(mfcc);
        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                all_mfccs_select.add(Arrays.copyOfRange(mfcc.getMFCC(), 1, 13));
                return true;
            }

            @Override
            public void processingFinished() {
                // write to file here - will happen for however many files there are in the
                // subdirectories of bark (in cache)
                try {
                    OutputStream outputStream1 = new FileOutputStream(mFileNameBase + "/mfccs_select.txt");
                    OutputStreamWriter outputStreamWriter1 = new OutputStreamWriter(outputStream1);

                    StringBuilder mfcc_sb = new StringBuilder();
                    for (float[] mfcc_set : all_mfccs_select) {
                        List<Float> mfcc_set_list = new ArrayList<>();
                        for (float mfcc_instance :mfcc_set) {
                            mfcc_set_list.add(mfcc_instance);
                        }
                        mfcc_sb.append(TextUtils.join(",", mfcc_set_list)).append("\n");
                    }
                    outputStreamWriter1.write(mfcc_sb.toString());
                    outputStreamWriter1.close();
                } catch (IOException e) {
                    Log.e("Exception", "MFCC File write failed: " + e.toString());
                }
            }
        });
        dispatcher.run();
    }


    private String readFromFile(Context context, String path) {

        String ret = "";

        try {
            InputStream inputStream = context.getAssets().open(path);

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString;
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append(receiveString);

                    if (! receiveString.endsWith("{")) {
                        stringBuilder.append("\n");
                    }
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("login activity", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("login activity", "Can not read file: " + e.toString());
        }

        return ret;
    }

    private void fileNotFoundMessage(String path) {
        AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        } else {
            builder = new AlertDialog.Builder(this);
        }
        builder.setTitle(R.string.dialog_title)
                .setMessage(String.format(getString(R.string.dialog_message), (path)))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mRecordButton.setEnabled(true);
                        mPlayButton.setText(R.string.playStart);
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
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

        double[] floatToDoubleArr(float[] arr) {
            double[] result = new double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                result[i] = arr[i];
            }
            return result;
        }

        int indexOf(float[] arr, float el) {
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] == el) {
                    return i;
                }
            }
            return -1;
        }

        public MFCCData(float[] mfcc) {
            this.max = Collections.max(Arrays.asList(ArrayUtils.toObject(mfcc)));
            this.min = Collections.min(Arrays.asList(ArrayUtils.toObject(mfcc)));
            this.range = this.max - this.min;

            this.maxPos = indexOf(mfcc, this.max);
            this.minPos = indexOf(mfcc, this.min);

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

        public String toString() {
            List<String> fields = new ArrayList<>();
            fields.add(String.valueOf(this.max));
            fields.add(String.valueOf(this.min));
            fields.add(String.valueOf(this.range));
            fields.add(String.valueOf(this.maxPos));
            fields.add(String.valueOf(this.minPos));
            fields.add(String.valueOf(this.amean));
            fields.add(String.valueOf(this.stddev));
            fields.add(String.valueOf(this.skewness));
            fields.add(String.valueOf(this.kurtosis));
            fields.add(String.valueOf(this.quartile1));
            fields.add(String.valueOf(this.quartile2));
            fields.add(String.valueOf(this.quartile3));
            fields.add(String.valueOf(this.iqr1_2));
            fields.add(String.valueOf(this.iqr2_3));
            fields.add(String.valueOf(this.iqr1_3));
            return TextUtils.join(",", fields);
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
            setText(R.string.mfccAllText);
            setOnClickListener(clicker);
        }
    }

    class SelectMFCCButton extends android.support.v7.widget.AppCompatButton {
        OnClickListener clicker = new OnClickListener() {
            @Override
            public void onClick(View view) {
                onSelectMFCC();
            }
        };

        public SelectMFCCButton(Context ctx) {
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
                if (nameFull.endsWith(".mp4")) {
                    fileStrings.add(nameFull.substring(0, nameFull.length() - 4));
                }
            }

            adapter = new ArrayAdapter<>
                    (ctx,android.R.layout.select_dialog_item, fileStrings);
            this.setAdapter(adapter);
        }
    }

    class DeleteButton extends android.support.v7.widget.AppCompatButton {

        OnClickListener clicker = new OnClickListener() {
            @Override
            public void onClick(View view) {
                String selectedFile = mFileNameBase + "/" + mTextField.getSelectedItem().toString() + ".mp4";
                File fdelete = new File(selectedFile);
                if (fdelete.exists()) {
                    if (fdelete.delete()) {
                        System.out.println("File deleted: " + selectedFile);
                    } else {
                        System.out.println("File NOT deleted: " + selectedFile);
                    }
                }
            }
        };

        public DeleteButton(Context ctx) {
            super(ctx);
            setText(R.string.deleteText);
            setOnClickListener(clicker);
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
                if (results != null) {
                    arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, results);
                    mListView.setAdapter(arrayAdapter);
                }

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
        //storage/emulated/0/Android/data/com.example.audiorecorder/cache/
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

        LinearLayout chooseAndDelete = new LinearLayout(this);

        mTextField = new TextField(this);
        chooseAndDelete.addView(mTextField,
                new LinearLayoutCompat.LayoutParams(
                        600,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        mDeleteButton = new DeleteButton(this);
        chooseAndDelete.addView(mDeleteButton,
                new LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        mSelectMFCCButton = new SelectMFCCButton(this);
        chooseAndDelete.addView(mSelectMFCCButton,
                new LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        chooseAndDelete.setOrientation(LinearLayout.HORIZONTAL);
        ll.addView(chooseAndDelete,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
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
            mListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new String[]{"Result 1", "Result 2", "Result 3"}));
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