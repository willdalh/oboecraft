package io.github.willdalh.oboecraft.fabric.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Checks in a screenshot that the notification after a pass is drawn: its white text, at full brightness, in the
 * middle of the top of the screen. A screen's dark background over it would dim the text, so it would not count.
 */
final class NotificationPixels {
    /** The fewest white pixels that count as text. A short name at the smallest scale has far more. */
    private static final int MIN_TEXT_PIXELS = 50;
    private static final int WHITE = 245;

    private NotificationPixels() {
    }

    static void assertShown(ClientGameTestContext context, String screenshot) {
        int pixels = whitePixelsAtTop(context.takeScreenshot(screenshot));
        if (pixels < MIN_TEXT_PIXELS) {
            throw new AssertionError("The notification should be drawn in " + screenshot + ", but only " + pixels
                    + " white pixels are at the top of the screen");
        }
    }

    static void assertNotShown(ClientGameTestContext context, String screenshot) {
        int pixels = whitePixelsAtTop(context.takeScreenshot(screenshot));
        if (pixels > 0) {
            throw new AssertionError("No notification should be drawn in " + screenshot + ", but " + pixels
                    + " white pixels are at the top of the screen");
        }
    }

    /** White pixels in the middle third of the top fifth of the screenshot, where only the notification is white. */
    private static int whitePixelsAtTop(Path screenshot) {
        BufferedImage image;
        try {
            image = ImageIO.read(screenshot.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int count = 0;
        for (int y = 0; y < image.getHeight() / 5; y++) {
            for (int x = image.getWidth() / 3; x < image.getWidth() * 2 / 3; x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) >= WHITE && ((rgb >> 8) & 0xFF) >= WHITE && (rgb & 0xFF) >= WHITE) {
                    count++;
                }
            }
        }
        return count;
    }
}
