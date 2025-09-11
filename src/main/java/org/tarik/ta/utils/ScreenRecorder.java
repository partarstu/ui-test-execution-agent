/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.utils;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.nio.file.Files.createDirectories;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tarik.ta.utils.CommonUtils.getMouseLocation;

public class ScreenRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(ScreenRecorder.class);
    private final boolean recordingEnabled;
    private FFmpegFrameRecorder recorder;
    private ScheduledExecutorService executorService;
    private final Robot robot;
    private final Java2DFrameConverter converter;

    public ScreenRecorder() {
        this.recordingEnabled = AgentConfig.getScreenRecordingEnabled();
        if (recordingEnabled) {
            try {
                this.robot = new Robot();
                this.converter = new Java2DFrameConverter();
            } catch (AWTException e) {
                throw new RuntimeException("Failed to create Robot instance for video recording", e);
            }
        } else {
            this.robot = null;
            this.converter = null;
        }
    }

    public void beginScreenCapture() {
        if (!recordingEnabled) {
            return;
        }

        String folder = AgentConfig.getScreenRecordingFolder();
        File videoFolder = new File(folder);
        if (!videoFolder.exists()) {
            try {
                createDirectories(Paths.get(folder));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String format = AgentConfig.getRecordingFormat();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName = Paths.get(folder, "test_run_" + timestamp + "." + format).toString();

        try {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            recorder = new FFmpegFrameRecorder(fileName, screenSize.width, screenSize.height);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat(format);
            recorder.setFrameRate(AgentConfig.getRecordingFrameRate());
            recorder.setVideoBitrate(AgentConfig.getRecordingBitrate());
            recorder.start();

            executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(this::captureFrame, 0, 1000 / AgentConfig.getRecordingFrameRate(), MILLISECONDS);
            LOG.info("Started video recording to file: {}", fileName);
        } catch (Exception e) {
            LOG.error("Failed to start video recording", e);
        }
    }

    private void captureFrame() {
        try {
            BufferedImage screenCapture = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));

            // Convert to BGR format to ensure correct channel order for FFmpeg
            BufferedImage bgrScreenCapture = new BufferedImage(screenCapture.getWidth(), screenCapture.getHeight(), TYPE_3BYTE_BGR);
            Graphics2D bgrGraphics = bgrScreenCapture.createGraphics();
            bgrGraphics.drawImage(screenCapture, 0, 0, null);
            Point mousePosition = getMouseLocation();
            bgrGraphics.setColor(new Color(255, 0, 0, 128));
            bgrGraphics.fillOval(mousePosition.x - 10, mousePosition.y - 10, 20, 20);
            bgrGraphics.dispose();
            recorder.record(converter.getFrame(bgrScreenCapture));
        } catch (Exception e) {
            LOG.error("Failed to capture frame for video recording", e);
        }
    }

    public void endScreenCapture() {
        if (!recordingEnabled || recorder == null) {
            return;
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        try {
            recorder.stop();
            recorder.release();
            converter.close();
            LOG.info("Stopped video recording.");
        } catch (Exception e) {
            LOG.error("Failed to stop video recording", e);
        }
    }
}