package fr.xxathyx.mediaplayer.render;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public class FrameScaler {

    public BufferedImage scale(BufferedImage source, int dstWidth, int dstHeight, ScalingMode mode) {
        ScalingTransform transform = ScalingMath.computeTransform(source.getWidth(), source.getHeight(), dstWidth, dstHeight, mode);
        BufferedImage output = new BufferedImage(dstWidth, dstHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        switch (mode) {
            case FIT -> {
                int drawWidth = transform.scaledWidth(source.getWidth());
                int drawHeight = transform.scaledHeight(source.getHeight());
                graphics.drawImage(source, transform.offsetX(), transform.offsetY(), drawWidth, drawHeight, null);
            }
            case FILL -> {
                BufferedImage cropped = source.getSubimage(transform.cropX(), transform.cropY(), transform.cropWidth(), transform.cropHeight());
                graphics.drawImage(cropped, 0, 0, dstWidth, dstHeight, null);
            }
            case STRETCH -> graphics.drawImage(source, 0, 0, dstWidth, dstHeight, null);
        }

        graphics.dispose();
        return output;
    }
}
