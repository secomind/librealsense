package com.intel.realsense.camera;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.intel.realsense.camera.Models.State.ExportState;
import com.intel.realsense.librealsense.DepthFrame;
import com.intel.realsense.librealsense.Extension;
import com.intel.realsense.librealsense.FrameSet;
import com.intel.realsense.librealsense.Pointcloud;
import com.intel.realsense.librealsense.Points;
import com.intel.realsense.librealsense.StreamType;
import com.intel.realsense.librealsense.VideoFrame;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

public class FrameExporter {
    private final Executor executor;


    public FrameExporter(Executor executor) {
        this.executor = executor;
    }

    public void exportInBackground(Path basePath, FrameSet frameSet, int frameNumber, ExportCallback callback) {
        this.executor.execute(() -> {
            exportFrameSet(basePath, frameSet, frameNumber);
            callback.onComplete();
        });
    }

    public void exportFrameSet(Path basePath, final FrameSet frameSet, final int frameNumber) {
        Path dir = Paths.get(basePath.toString(), this.exportDate);
        Path path = this.getExportPathPath(dir, frameNumber);
        Log.d("Export", path.toString());

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        DepthFrame d = frameSet.first(StreamType.DEPTH).as(Extension.DEPTH_FRAME);
        assert d.is(Extension.DEPTH_FRAME);

        try (Pointcloud pc = new Pointcloud()) {
            Points points = pc.process(d).as(Extension.POINTS);

            Points p = points.as(Extension.POINTS);

            VideoFrame v = frameSet.first(StreamType.COLOR).as(Extension.VIDEO_FRAME);

            p.exportToPly(path.toString(), v);

            Log.d("Export", "exported");
        }
    }

    private Path getExportPathPath(Path basePath, int frameNumber) {
        return Paths.get(basePath.toString(), frameNumber * 90 + ".ply");
    }

}
