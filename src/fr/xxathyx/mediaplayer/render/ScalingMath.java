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
                int scaledWidth = Math.max(1, (int) Math.floor(srcWidth * scale));
                int scaledHeight = Math.max(1, (int) Math.floor(srcHeight * scale));
                int offsetX = (dstWidth - scaledWidth) / 2;
                int offsetY = (dstHeight - scaledHeight) / 2;
                double scaledX = (double) scaledWidth / (double) srcWidth;
                double scaledY = (double) scaledHeight / (double) srcHeight;
                yield new ScalingTransform(scaledX, scaledY, offsetX, offsetY, 0, 0, srcWidth, srcHeight);
            }
            case FILL -> {
                double scale = Math.max((double) dstWidth / (double) srcWidth, (double) dstHeight / (double) srcHeight);
                int cropWidth = Math.min(srcWidth, (int) Math.ceil(dstWidth / scale));
                int cropHeight = Math.min(srcHeight, (int) Math.ceil(dstHeight / scale));
                int cropX = (srcWidth - cropWidth) / 2;
                int cropY = (srcHeight - cropHeight) / 2;
                double scaleX = (double) dstWidth / (double) cropWidth;
                double scaleY = (double) dstHeight / (double) cropHeight;
                yield new ScalingTransform(scaleX, scaleY, 0, 0, cropX, cropY, cropWidth, cropHeight);
            }
        };
    }
}
