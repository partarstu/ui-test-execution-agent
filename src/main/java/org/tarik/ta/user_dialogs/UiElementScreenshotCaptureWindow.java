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
package org.tarik.ta.user_dialogs;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static javax.swing.JOptionPane.YES_OPTION;
import static org.tarik.ta.utils.BoundingBoxUtil.drawBoundingBox;
import static org.tarik.ta.utils.CommonUtils.captureScreen;
import static org.tarik.ta.utils.ImageUtils.*;

public class UiElementScreenshotCaptureWindow extends AbstractDialog {
    private static final Logger LOG = LoggerFactory.getLogger(UiElementScreenshotCaptureWindow.class);
    private final JPanel imagePanel;
    private final BufferedImage originalScreenshot;
    private final Color boundingBoxColor;
    private Point startPoint;
    private Point endPoint;
    private Rectangle drawnBoundingBox;
    private Rectangle drawnScaledBoundingBox;
    private boolean drawing = false;
    private BufferedImage elementScreenshot;
    private BufferedImage wholeScreenshotWithBoundingBox;
    private static final double defaultZoomOutFactor = 0.9;
    private final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

    private UiElementScreenshotCaptureWindow(Window owner, BufferedImage screenshot, Color boundingBoxColor) {
        super(owner, "UI element screenshot capture");
        this.originalScreenshot = screenshot;
        this.boundingBoxColor = boundingBoxColor;
        BufferedImage scaledScreenshot = scaleImage(screenshot,
                (int) (screenSize.width * defaultZoomOutFactor), (int) (screenSize.height * defaultZoomOutFactor));

        this.imagePanel = initializeImagePanel(scaledScreenshot);
        add(this.imagePanel);

        pack();
        displayPopup();
    }

    private JPanel initializeImagePanel(BufferedImage scaledScreenshot) {
        var panel = getImagePanel(scaledScreenshot);
        panel.setPreferredSize(new Dimension(scaledScreenshot.getWidth(), scaledScreenshot.getHeight()));
        panel.addMouseListener(getMouseAdapter());
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                endPoint = e.getPoint();
                imagePanel.repaint();
            }
        });
        return panel;
    }

    private BufferedImage scaleImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        var scaledImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_AREA_AVERAGING);
        return toBufferedImage(scaledImage, targetWidth, targetHeight);
    }

    private Rectangle scaleRectangle(Rectangle originalRectangle) {
        double scaleX = ((double) originalScreenshot.getWidth()) / screenSize.width / defaultZoomOutFactor;
        double scaleY = ((double) originalScreenshot.getHeight()) / screenSize.height / defaultZoomOutFactor;
        return new Rectangle((int) (originalRectangle.x * scaleX), (int) (originalRectangle.y * scaleY),
                (int) (originalRectangle.width * scaleX), (int) (originalRectangle.height * scaleY));
    }

    private MouseAdapter getMouseAdapter() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();
                drawing = true;
                drawnBoundingBox = null;
                elementScreenshot = null;
                drawnScaledBoundingBox = null;
                wholeScreenshotWithBoundingBox = null;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                drawing = false;
                endPoint = e.getPoint();
                int x = Math.min(startPoint.x, endPoint.x);
                int y = Math.min(startPoint.y, endPoint.y);
                int width = Math.abs(startPoint.x - endPoint.x);
                int height = Math.abs(startPoint.y - endPoint.y);
                drawnBoundingBox = new Rectangle(x, y, width, height);
                imagePanel.repaint();
                drawnScaledBoundingBox = scaleRectangle(new Rectangle(x, y, width, height));
                elementScreenshot = originalScreenshot.getSubimage(drawnScaledBoundingBox.x, drawnScaledBoundingBox.y,
                        drawnScaledBoundingBox.width, drawnScaledBoundingBox.height);
                wholeScreenshotWithBoundingBox = cloneImage(originalScreenshot);
                drawBoundingBox(wholeScreenshotWithBoundingBox, drawnScaledBoundingBox, boundingBoxColor);

                JPanel panel = getElementScreenshotPanel();

                int result = YesNoOptionDialog.display(UiElementScreenshotCaptureWindow.this,
                        "Is this bounding Box correct?", panel);
                if (result == YES_OPTION) {
                    dispose();
                } else {
                    drawnBoundingBox = null;
                    elementScreenshot = null;
                    drawnScaledBoundingBox = null;
                    wholeScreenshotWithBoundingBox = null;
                    startPoint = null;
                    endPoint = null;
                    imagePanel.repaint();
                }
            }
        };
    }

    @NotNull
    private JPanel getElementScreenshotPanel() {
        JPanel panel = new JPanel();
        BufferedImage imageToDisplay = elementScreenshot;
        int imageWidth = elementScreenshot.getWidth();
        int imageHeight = elementScreenshot.getHeight();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = (int) (screenSize.width * 0.9);
        int maxHeight = (int) (screenSize.height * 0.9);

        if (imageWidth > maxWidth || imageHeight > maxHeight) {
            double widthRatio = (double) maxWidth / imageWidth;
            double heightRatio = (double) maxHeight / imageHeight;
            double ratio = Math.min(widthRatio, heightRatio);
            int newWidth = (int) (imageWidth * ratio);
            int newHeight = (int) (imageHeight * ratio);
            imageToDisplay = scaleImage(elementScreenshot, newWidth, newHeight);
        }

        panel.add(new JLabel(new ImageIcon(imageToDisplay)));
        return panel;
    }

    @NotNull
    private JPanel getImagePanel(BufferedImage screenshot) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(screenshot, 0, 0, null);
                if (drawing) {
                    if (startPoint != null && endPoint != null) {
                        int x = Math.min(startPoint.x, endPoint.x);
                        int y = Math.min(startPoint.y, endPoint.y);
                        int width = Math.abs(startPoint.x - endPoint.x);
                        int height = Math.abs(startPoint.y - endPoint.y);
                        g.setColor(Color.GREEN);
                        g.drawRect(x, y, width, height);
                    }
                }

                //Highlight after the rectangle creation is finished
                if (drawnBoundingBox != null) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(new Color(255, 0, 0, 50)); // Semi-transparent red
                    g2d.fillRect(drawnBoundingBox.x, drawnBoundingBox.y, drawnBoundingBox.width, drawnBoundingBox.height);
                }
            }
        };
    }

    private UiElementCaptureResult getCaptureResult() {
        if (drawnBoundingBox == null || elementScreenshot == null) {
            LOG.error("UI element capture has not been completed.");
            return null;
        } else {
            return new UiElementCaptureResult(true, drawnScaledBoundingBox, wholeScreenshotWithBoundingBox, elementScreenshot);
        }
    }

    public static Optional<UiElementCaptureResult> displayAndGetResult(Window owner, Color boundingBoxColor) {
        BufferedImage screenshot = captureScreen();
        UiElementScreenshotCaptureWindow window = new UiElementScreenshotCaptureWindow(owner, screenshot, boundingBoxColor);
        return ofNullable(window.getCaptureResult());
    }

    @Override
    protected void onDialogClosing() {
        // User decided to interrupt the process - it means no result will be returned
        drawnBoundingBox = null;
        elementScreenshot= null;
        wholeScreenshotWithBoundingBox = null;
    }

    public record UiElementCaptureResult(boolean success, Rectangle boundingBox, BufferedImage wholeScreenshotWithBoundingBox,
                                         BufferedImage elementScreenshot) {
    }
}