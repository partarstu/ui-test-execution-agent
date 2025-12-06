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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.google.common.base.Preconditions.checkArgument;
import static java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment;
import static java.lang.Thread.currentThread;
import static java.nio.file.Files.*;
import static java.time.Instant.now;
import static java.util.Comparator.comparingInt;
import static java.util.Optional.*;
import static org.tarik.ta.utils.ImageUtils.toBufferedImage;

public class CommonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CommonUtils.class);
    private static Robot robot;

    public static Optional<String> getObjectPrettyPrinted(ObjectMapper mapper, Map<String, String> toolExecutionInfoByToolName) {
        try {
            return of(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toolExecutionInfoByToolName));
        } catch (JsonProcessingException e) {
            LOG.error("Couldn't write the provided tool execution info by tool name as a pretty string.", e);
            return empty();
        }
    }

    public static Object getStaticFieldValue(Field colorField) {
        try {
            return colorField.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Color getColorByName(@NotNull String colorName) {
        try {
            Field colorField = Color.class.getField(colorName.toLowerCase());
            var value = getStaticFieldValue(colorField);
            checkArgument(value instanceof Color, "No suitable instance of %s found in JDK for the value of %s"
                    .formatted(Color.class.getName(), colorName));
            return (Color) value;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No suitable instance of %s found in JDK for the value of %s"
                    .formatted(Color.class.getName(), colorName));
        }
    }

    public static String getColorName(@NotNull Color color) {
        return Arrays.stream(Color.class.getFields())
                .filter(field -> field.getType() == Color.class)
                .filter(field -> color.equals(getStaticFieldValue(field)))
                .findFirst()
                .map(Field::getName)
                .orElseThrow(() -> new IllegalStateException("No suitable color name found in JDK for the value of " + color));
    }

    public static Optional<Integer> parseStringAsInteger(String str) {
        if (isBlank(str)) {
            return empty();
        }
        try {
            return Optional.of(Integer.parseInt(str.trim()));
        } catch (NumberFormatException e) {
            LOG.error("Failed to parse string as integer: '{}'", str, e);
            return empty();
        }
    }

    public static Optional<Double> parseStringAsDouble(String str) {
        if (isBlank(str)) {
            return empty();
        }
        try {
            return Optional.of(Double.parseDouble(str.trim()));
        } catch (NumberFormatException e) {
            return empty();
        }
    }

    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(Duration.ofSeconds(seconds));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static BufferedImage captureScreen(boolean withHighestResolution) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return captureScreenPart(new Rectangle(screenSize), withHighestResolution);
    }

    public static Dimension getScreenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }

    public static BufferedImage captureScreen() {
        return captureScreen(true);
    }

    public static BufferedImage captureScreenPart(@NotNull Rectangle target, boolean withHighestResolution) {
        var screenShots = getRobot().createMultiResolutionScreenCapture(target);
        Comparator<BufferedImage> comparator = comparingInt(BufferedImage::getHeight);
        if (withHighestResolution) {
            comparator = comparator.reversed();
        }

        return screenShots.getResolutionVariants().stream()
                .map(i -> toBufferedImage(i, target.width, target.height))
                .min(comparator)
                .orElseThrow();
    }

    public static synchronized Robot getRobot() {
        if (robot == null) {
            try {
                robot = new Robot();
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
        }
        return robot;
    }

    public static Point getMouseLocation() {
        return MouseInfo.getPointerInfo().getLocation();
    }

    public static Point getScaledScreenLocationCoordinates(@NotNull Point physicalScreenCoordinates) {
        var graphicsConfiguration = getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = graphicsConfiguration.getDefaultTransform();
        double uiScaleX = tx.getScaleX();
        double uiScaleY = tx.getScaleY();
        if (uiScaleX == 1 && uiScaleY == 1) {
            return physicalScreenCoordinates;
        } else {
            return new Point((int) (physicalScreenCoordinates.getX() / uiScaleX), (int) (physicalScreenCoordinates.getY() / uiScaleY));
        }
    }

    public static Rectangle getScaledBoundingBox(@NotNull Rectangle originalBoundingBox) {
        var graphicsConfiguration = getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = graphicsConfiguration.getDefaultTransform();
        double uiScaleX = tx.getScaleX();
        double uiScaleY = tx.getScaleY();
        if (uiScaleX == 1 && uiScaleY == 1) {
            return originalBoundingBox;
        } else {
            var scaledX = originalBoundingBox.getX() / uiScaleX;
            var scaledY = originalBoundingBox.getY() / uiScaleY;
            var scaledWidth = originalBoundingBox.getWidth() / uiScaleX;
            var scaledHeight = originalBoundingBox.getHeight() / uiScaleY;
            return new Rectangle((int) scaledX, (int) scaledY, (int) scaledWidth, (int) scaledHeight);
        }
    }

    public static Rectangle getPhysicalBoundingBox(@NotNull Rectangle logicalBoundingBox) {
        var graphicsConfiguration = getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = graphicsConfiguration.getDefaultTransform();
        double uiScaleX = tx.getScaleX();
        double uiScaleY = tx.getScaleY();
        if (uiScaleX == 1 && uiScaleY == 1) {
            return logicalBoundingBox;
        } else {
            var physicalX = logicalBoundingBox.getX() * uiScaleX;
            var physicalY = logicalBoundingBox.getY() * uiScaleY;
            var physicalWidth = logicalBoundingBox.getWidth() * uiScaleX;
            var physicalHeight = logicalBoundingBox.getHeight() * uiScaleY;
            return new Rectangle((int) physicalX, (int) physicalY, (int) physicalWidth, (int) physicalHeight);
        }
    }

    public static void deleteFile(@NotNull File file) {
        if (file.exists()) {
            if (!file.delete()) {
                LOG.warn("Failed to delete file: {}", file.getAbsolutePath());
            }
        }
    }

    public static void waitUntil(Instant deadline) {
        while (now().isBefore(deadline)) {
            sleepMillis(100);
        }
    }

    public static void deleteFolderContents(@NotNull Path pathToFolder) {
        checkArgument(isDirectory(pathToFolder), "%s is not a directory".formatted(pathToFolder));
        try {
            FileUtils.cleanDirectory(pathToFolder.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Point getPhysicalScreenLocationCoordinates(@NotNull Point scaledScreenCoordinates) {
        var gc = getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform tx = gc.getDefaultTransform();
        double uiScaleX = tx.getScaleX();
        double uiScaleY = tx.getScaleY();
        return new Point((int) (scaledScreenCoordinates.getX() * uiScaleX), (int) (scaledScreenCoordinates.getY() * uiScaleY));
    }

    public static <T> Optional<T> getFutureResult(Future<T> future, String resultDescription) {
        try {
            return ofNullable(future.get());
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("%s task failed".formatted(resultDescription), e);
            if (e instanceof InterruptedException) {
                currentThread().interrupt();
            }
            return empty();
        }
    }

    @NotNull
    public static Rectangle getCommonArea(List<Rectangle> initialCandidates) {
        if (initialCandidates.isEmpty()) {
            return new Rectangle();
        } else {
            int minX = initialCandidates.stream().mapToInt(r -> r.x).min().getAsInt();
            int minY = initialCandidates.stream().mapToInt(r -> r.y).min().getAsInt();
            int maxX = initialCandidates.stream().mapToInt(r -> r.x + r.width).max().getAsInt();
            int maxY = initialCandidates.stream().mapToInt(r -> r.y + r.height).max().getAsInt();
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        }
    }
}