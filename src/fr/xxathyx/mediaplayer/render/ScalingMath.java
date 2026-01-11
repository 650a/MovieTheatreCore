package fr.xxathyx.mediaplayer.render;

public final class ScalingMath {
    private ScalingMath() {
    }

    public static ScalingTransform computeTransform(int srcWidth, int srcHeight, int dstWidth, int dstHeight, ScalingMode mode) {
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive.");
        }

        double srcAspect = (double) srcWidth / (double) srcHeight;
        double dstAspect = (double) dstWidth / (double) dstHeight;

        return switch (mode) {
            case STRETCH -> new ScalingTransform(
                    (double) dstWidth / (double) srcWidth,
                    (double) dstHeight / (double) srcHeight,
                    0,
                    0,
                    0,
                    0,
                    srcWidth,
                    srcHeight
            );
            case FIT -> {
                double scale = Math.min((double) dstWidth / (double) srcWidth, (double) dstHeight / (double) srcHeight);
                int scaledWidth = (int) Math.round(srcWidth * scale);
                int scaledHeight = (int) Math.round(srcHeight * scale);
                int offsetX = (dstWidth - scaledWidth) / 2;
                int offsetY = (dstHeight - scaledHeight) / 2;
                yield new ScalingTransform(scale, scale, offsetX, offsetY, 0, 0, srcWidth, srcHeight);
            }
            case FILL -> {
                int cropWidth = srcWidth;
                int cropHeight = srcHeight;
                if (srcAspect > dstAspect) {
                    cropWidth = (int) Math.round(srcHeight * dstAspect);
                } else if (srcAspect < dstAspect) {
                    cropHeight = (int) Math.round(srcWidth / dstAspect);
                }
                int cropX = (srcWidth - cropWidth) / 2;
                int cropY = (srcHeight - cropHeight) / 2;
                double scaleX = (double) dstWidth / (double) cropWidth;
                double scaleY = (double) dstHeight / (double) cropHeight;
                yield new ScalingTransform(scaleX, scaleY, 0, 0, cropX, cropY, cropWidth, cropHeight);
            }
        };
    }
}
