package com.intel.realsense.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.realsense.camera.Models.State.ExportState;
import com.intel.realsense.librealsense.Config;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.GLRsSurfaceView;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingActivity extends AppCompatActivity {
    private static final String TAG = "librs camera rec";

    private Streamer mStreamer;
    private GLRsSurfaceView mGLSurfaceView;

    private boolean mPermissionsGranted = false;

    private FloatingActionButton mStopRecordFab;
    private Path basePath;
    //private ExportModel exportModel;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private FrameExporter exporter;
    private static final int INTERVAL_MS = 5000;

    private long lastFrameTime = 0;
    private int capturedFrames = 0;
    private int exported = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setupControls();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PermissionsUtils.PERMISSIONS_REQUEST_WRITE);
            return;
        }

        mPermissionsGranted = true;

        // this.exportModel = new ViewModelProvider(this).get(ExportModel.class);
        String exportDate = Instant.now().toString();

        this.basePath = Paths.get(getExternalFilesDir(null).getAbsolutePath(), exportDate);
        this.exporter = new FrameExporter(this.executorService);
        // exportModel.getUiState().observe(this, uiState -> {
        //    Integer exported = uiState.getExported();
        //   TextView textView = (TextView) findViewById(R.id.textView);
        //   textView.setText(String.format(Locale.ENGLISH, "Recorded measure %d/4", exported));
        //   if (exported == 4) {
        //      finish();
        //  }
        // });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PermissionsUtils.PERMISSIONS_REQUEST_WRITE);
            return;
        }

        mPermissionsGranted = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // handling device orientation changes to avoid interruption during recording

        // cleanup previous surface
        if (mGLSurfaceView != null) {
            mGLSurfaceView.clear();
            mGLSurfaceView.close();
        }

        // setup recording layout landscape or portrait automatically depends on orientation
        setContentView(R.layout.activity_recording);

        // setup layout controls
        setupControls();
    }

    private void setupControls() {
        mGLSurfaceView = findViewById(R.id.recordingGlSurfaceView);

        mStopRecordFab = findViewById(R.id.stopRecordFab);
        mStopRecordFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(RecordingActivity.this, PreviewActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mPermissionsGranted) {
            mStreamer = new Streamer(this, true, new Streamer.Listener() {
                @Override
                public void config(Config config) {
                    // config.enableRecordToFile(getFilePath());
                }

                @Override
                public void onFrameset(FrameSet frameSet) {
                    mGLSurfaceView.upload(frameSet);
                    nextFrame(frameSet);
                }
            });
            try {
                mGLSurfaceView.clear();
                mStreamer.start();
            } catch (Exception e) {
                finish();
            }
        }
    }

    public void nextFrame(FrameSet frameSet) {
        long time = System.currentTimeMillis();
        long diff = time - lastFrameTime;

        if (diff < INTERVAL_MS || capturedFrames >= 4) {
            return;
        }
        exporter.exportInBackground(this.basePath, frameSet.clone(), capturedFrames, () -> {
            Log.d("Export", String.format(Locale.ENGLISH, "Frame %d exported", capturedFrames));

            exported += 1;

            if (exported == 4) {


                finish();
            }
        });

        capturedFrames += 1;
        lastFrameTime = time;

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mStreamer != null)
            mStreamer.stop();
        if (mGLSurfaceView != null)
            mGLSurfaceView.clear();
    }


}
