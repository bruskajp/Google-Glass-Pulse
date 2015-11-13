package edu.usc.xinyu.telescope;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.FrameLayout;

import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.cpp.opencv_core.IplImage;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.Buffer;
import java.nio.ShortBuffer;

import static com.googlecode.javacv.cpp.opencv_core.*;
import java.io.PrintWriter;

public class MainActivity extends Activity {

    private final static String LOG_TAG = "MainActivity";

    private PowerManager.WakeLock mWakeLock;
    //private String serverAddress = "http://127.0.0.1:8090/webcam.ffm";

    private volatile FFmpegFrameRecorder recorder;
    //private volatile FFmpegFrameRecorder recorder2; //testing code
    boolean recording = false;
    long startTime = 0;

    private int sampleAudioRateInHz = 44100;
    private int imageWidth = 320;
    private int imageHeight = 240;

    private int frameRate = 20;

    private Thread audioThread;
    volatile boolean runAudioThread = true;

    private AudioRecordRunnable audioRecordRunnable;

    private CameraView cameraView;
    private IplImage yuvIplimage = null;
    //private IplImage yuvIplimage2 = null; //testing code

    private RelativeLayout mainLayout;
    private AddressRetriever addressRetriever;
    private TextView promptTextView;      // A text view displayed on the screen to nofity user
    private TextView recordingTextView;


    private GestureDetector mGestureDetector;
    private static int GLASS_MAX_ZOOM = 60;

    TalkToServer talkToServer;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {

        @Override
        public void run() {
            //String serverAddress = "192.168.1.102";
            String serverAddress = "192.168.1.111";
            int serverPort = 8080;

            talkToServer = new TalkToServer(serverAddress, serverPort, "test", recordingTextView);
            talkToServer.execute();
            timerHandler.postDelayed(this, 200);
        }
    };


    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mGestureDetector = createGestureDetector(this);
        addressRetriever = new AddressRetriever(promptTextView);
        setContentView(R.layout.activity_main);
        initLayout();
        addressRetriever.execute();
        initRecorder();

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, LOG_TAG);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private GestureDetector createGestureDetector(Context context) {
        GestureDetector gestureDetector = new GestureDetector(context);
        cameraView = new CameraView(this);
        gestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture){
                if (gesture == Gesture.TAP) {
                    if (!recording) {
                        if (recorder == null) {
                            initRecorder();
                        }
                        recordingTextView.setVisibility(View.VISIBLE);
                        startRecording();

                        promptTextView.setText("Tap the touch pad to stop streaming");
                        Log.i(LOG_TAG, "Start recording pressed");

                    } else {
                        stopRecording();
                        recordingTextView.setVisibility(View.INVISIBLE);
                        promptTextView.setText("Tap the touch pad to start streaming");
                        Log.i(LOG_TAG, "Stop recording pressed");
                    }
                    return true;
                } else if (gesture == Gesture.SWIPE_RIGHT){
                    cameraView.zoomIn(10);
                    return true;
                } else if (gesture == Gesture.SWIPE_LEFT){
                    cameraView.zoomOut(10);
                    return true;
                }
                return false;
            }
        });

//        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
//            @Override
//            public boolean onScroll(float displacement, float delta, float velocity) {
//                Log.d(LOG_TAG, "delta: " + delta);
//                Log.d(LOG_TAG, "disp: " + displacement);
//                Log.d(LOG_TAG, "velocity: " + velocity);
//                preview.zoom((int) displacement);
//                return true;
//            }
//        });

        return gestureDetector;
    }

    /*
     * Send generic motion events to the gesture detector
     */
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }

    private void initLayout() {

        mainLayout = (RelativeLayout) this.findViewById(R.id.camera_layout);

        cameraView = new CameraView(this);
        RelativeLayout.LayoutParams camviewParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
        );
        mainLayout.addView(cameraView, camviewParams);

        RelativeLayout.LayoutParams txtviewParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        txtviewParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        txtviewParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        promptTextView = new TextView(this);
        promptTextView.setText("Tap the touch pad to start streaming");
        promptTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        mainLayout.addView(promptTextView, txtviewParams);

        RelativeLayout.LayoutParams recviewParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        recviewParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        recviewParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        recordingTextView = new TextView(this);
        recordingTextView.setText("Streaming");
        recordingTextView.setTextColor(Color.RED);
        recordingTextView.setVisibility(View.INVISIBLE);
        mainLayout.addView(recordingTextView, recviewParams);

        Log.d(LOG_TAG, "Main layout initialization completed");
    }

    private boolean initRecorder() {
        String serverAddress = addressRetriever.getServerAddress();

        if (yuvIplimage == null) {
            yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2);
            //yuvIplimage2 = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2); //testing code
            Log.d(LOG_TAG, "IplImage.create");
        }
        if (serverAddress.isEmpty()) {
            promptTextView.setText("Failed to get server address, please try again later.");
            return false;
        }

        recorder = new FFmpegFrameRecorder(serverAddress, imageWidth, imageHeight, 1);
        //recorder2 = new FFmpegFrameRecorder(serverAddress, imageWidth, imageHeight, 1); //testing code
        Log.i(LOG_TAG, "FFmpegFrameRecorder: " + serverAddress + " imageWidth: " + imageWidth + " imageHeight " + imageHeight);

        recorder.setFormat("ffm");
        recorder.setSampleRate(sampleAudioRateInHz);
        recorder.setFrameRate(frameRate);

        audioRecordRunnable = new AudioRecordRunnable(sampleAudioRateInHz);
        audioThread = new Thread(audioRecordRunnable);
        promptTextView.setText("Tap the touch pad to start streaming");
        return true;
    }

    public void startRecording() {
        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (!wifi.isConnected()) {
            // TODO Add text prompt to user that wifi is not connected.
            return;
        }
        if (recorder == null) {
            promptTextView.setText("Recorder is not ready yet. Please try again later.");
            return;
        }
        try {
            recorder.start();
            runAudioThread = true;
            audioThread.start();
            startTime = System.currentTimeMillis();
            recording = true;
            timerHandler.postDelayed(timerRunnable, 0);
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            Log.e(LOG_TAG, "Error: recorder is null");
        }
    }

    public void stopRecording() {
        // This should stop the audio thread from running
        runAudioThread = false;
        if (recorder != null && recording) {
            recording = false;
            Log.v(LOG_TAG,"Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
                audioRecordRunnable.StopRecording();
                timerHandler.removeCallbacks(timerRunnable);
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }

    class CameraView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

        private boolean previewRunning = false;
        private SurfaceHolder holder;
        private Camera camera;
        long videoTimestamp = 0;
        Bitmap bitmap;

        public CameraView(Context _context) {
            super(_context);

            holder = this.getHolder();
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            camera = Camera.open();

            try {
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(this);

                Camera.Parameters currentParams = camera.getParameters();
                //currentParams.setPreviewFpsRange(30000, 30000);
                //currentParams.setPreviewFrameRate(15); // remove in future version
                camera.setParameters(currentParams);
                int[] range = new int[2];
                System.out.println(range[0]+ "  " + range[1]);
                currentParams.getPreviewFpsRange(range);
                Log.i(LOG_TAG,"Preview imageWidth: " + currentParams.getPreviewSize().width + " imageHeight: " + currentParams.getPreviewSize().height +  " FPS: " + range[0] + ", " + range[1]);

                // Use these values
                imageWidth = currentParams.getPreviewSize().width;
                imageHeight = currentParams.getPreviewSize().height;

                bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ALPHA_8);

                camera.startPreview();
                previewRunning = true;
            }
            catch (IOException e) {
                Log.i(LOG_TAG,e.getMessage());
                e.printStackTrace();
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(LOG_TAG,"Surface Changed: width " + width + " height: " + height);

            Camera.Parameters currentParams = camera.getParameters();
            Log.i(LOG_TAG,"Preview imageWidth: " + currentParams.getPreviewSize().width + " imageHeight: " + currentParams.getPreviewSize().height);

            imageWidth = currentParams.getPreviewSize().width;
            imageHeight = currentParams.getPreviewSize().height;

            yuvIplimage = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2);
            //yuvIplimage2 = IplImage.create(imageWidth, imageHeight, IPL_DEPTH_8U, 2); //testing code
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                camera.setPreviewCallback(null);

                previewRunning = false;
                camera.release();

            } catch (RuntimeException e) {
                Log.i(LOG_TAG,e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            if (yuvIplimage != null && recording) {
                videoTimestamp = 1000 * (System.currentTimeMillis() - startTime);
                // Put the camera preview frame right into the yuvIplimage object
                yuvIplimage.getByteBuffer().put(data);
                //yuvIplimage2.getByteBuffer().put(data); //testing code
                try {
                    // Get the correct time
                    recorder.setTimestamp(videoTimestamp);
                    recorder.record(yuvIplimage);
                } catch (FFmpegFrameRecorder.Exception e) {
                    Log.i(LOG_TAG,e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        private boolean zoomIn(int zoomBy) {

            if (camera == null) {
                Log.i(LOG_TAG, "Camera is not running");
                return false;
            }

            if (zoomBy > GLASS_MAX_ZOOM) {
                Log.i(LOG_TAG, "Zoomed in too much");
                return false;
            }

            int maxZoom = camera.getParameters().getMaxZoom();
            int curZoom = camera.getParameters().getZoom();

            if (curZoom >= maxZoom) {
                Log.i(LOG_TAG, "Camera is at max zoom");
                return false;
            }

            curZoom += zoomBy;

            if (curZoom > maxZoom) {
                curZoom = maxZoom;
            }

            camera.startSmoothZoom(curZoom);
            return true;
        }

        private boolean zoomOut(int zoomBy){
            if (camera == null) {
                Log.i(LOG_TAG, "Camera is not running");
                return false;
            }

            if (zoomBy > GLASS_MAX_ZOOM) {
                Log.i(LOG_TAG, "Zoomed in too much");
                return false;
            }

            int curZoom = camera.getParameters().getZoom();

            if (curZoom <= 0) {
                Log.i(LOG_TAG, "Camera is at min zoom");
                return false;
            }

            curZoom -= zoomBy;

            if (curZoom < 0) {
                curZoom = 0;
            }

            camera.startSmoothZoom(curZoom);
            return true;
        }
    }

    class AudioRecordRunnable implements Runnable {
        private int sampleRateInHz;
        private AudioRecord audioRecord;

        public AudioRecordRunnable(int sampleRate) {
            sampleRateInHz = sampleRate;
            Log.i(LOG_TAG, "Audio recorder is created");
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            int bufferSize;
            short[] audioData;
            int bufferReadResult;
            bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            audioData = new short[bufferSize];
            Log.d(LOG_TAG, "audioRecord.startRecording()");
            audioRecord.startRecording();

            // Audio Capture/Encoding Loop
            while (runAudioThread) {
                // Read from audioRecord
                bufferReadResult = audioRecord.read(audioData, 0, audioData.length);
                if (bufferReadResult > 0) {
                    if (recording) {
                        try {
                            Buffer[] buffer = {ShortBuffer.wrap(audioData, 0, bufferReadResult)};
                            recorder.record(buffer);
                        } catch (FFmpegFrameRecorder.Exception e) {
                            Log.i(LOG_TAG,e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.i(LOG_TAG,"AudioThread Finished");

            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.i(LOG_TAG,"audioRecord released");
            }
        }

        public void StopRecording() {
            if (runAudioThread) {
                runAudioThread = false;
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.d(LOG_TAG, "Audio recorder has stopped");
            }
        }
    }


}
