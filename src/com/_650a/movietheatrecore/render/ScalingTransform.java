package com._650a.movietheatrecore.render;

public record ScalingTransform(
        double scaleX,
        double scaleY,
        int offsetX,
        int offsetY,
        int cropX,
        int cropY,
        int cropWidth,
        int cropHeight
) {
    public int scaledWidth(int srcWidth) {
        return (int) Math.round(srcWidth * scaleX);
    }

    public int scaledHeight(int srcHeight) {
        return (int) Math.round(srcHeight * scaleY);
    }
}
