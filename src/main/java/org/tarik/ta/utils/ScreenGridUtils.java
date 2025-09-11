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

import java.awt.*;
import java.awt.image.BufferedImage;

public class ScreenGridUtils {
    public static BufferedImage drawGrid(BufferedImage image, int rows, int cols) {
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        int width = image.getWidth();
        int height = image.getHeight();
        int cellWidth = width / cols;
        int cellHeight = height / rows;

        for (int i = 1; i < cols; i++) {
            g.drawLine(i * cellWidth, 0, i * cellWidth, height);
        }
        for (int i = 1; i < rows; i++) {
            g.drawLine(0, i * cellHeight, width, i * cellHeight);
        }

        g.setFont(new Font("Arial", Font.BOLD, 12));
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String label = row + "," + col;
                g.drawString(label, col * cellWidth + 5, row * cellHeight + 15);
            }
        }

        g.dispose();
        return image;
    }
}