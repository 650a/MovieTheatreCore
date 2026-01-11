package fr.xxathyx.mediaplayer.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalingMathTest {

    @Test
    void fitScalesWithLetterbox() {
        ScalingTransform transform = ScalingMath.computeTransform(1920, 1080, 640, 480, ScalingMode.FIT);
        assertEquals(0.3333333333333333, transform.scaleX(), 1.0e-9);
        assertEquals(0.3333333333333333, transform.scaleY(), 1.0e-9);
        assertEquals(0, transform.offsetX());
        assertEquals(60, transform.offsetY());
    }

    @Test
    void fillScalesWithCrop() {
        ScalingTransform transform = ScalingMath.computeTransform(1920, 1080, 640, 480, ScalingMode.FILL);
        assertEquals(240, transform.cropX());
        assertEquals(0, transform.cropY());
        assertEquals(1440, transform.cropWidth());
        assertEquals(1080, transform.cropHeight());
        assertEquals(640, Math.round(transform.cropWidth() * transform.scaleX()));
        assertEquals(480, Math.round(transform.cropHeight() * transform.scaleY()));
    }

    @Test
    void fitScalesWideDestination() {
        ScalingTransform transform = ScalingMath.computeTransform(800, 600, 1280, 720, ScalingMode.FIT);
        assertEquals(1.2, transform.scaleX(), 1.0e-9);
        assertEquals(1.2, transform.scaleY(), 1.0e-9);
        assertEquals(160, transform.offsetX());
        assertEquals(0, transform.offsetY());
    }

    @Test
    void stretchScalesIndependently() {
        ScalingTransform transform = ScalingMath.computeTransform(640, 480, 1280, 720, ScalingMode.STRETCH);
        assertEquals(2.0, transform.scaleX(), 1.0e-9);
        assertEquals(1.5, transform.scaleY(), 1.0e-9);
        assertEquals(0, transform.offsetX());
        assertEquals(0, transform.offsetY());
    }

    @Test
    void invalidDimensionsThrow() {
        assertThrows(IllegalArgumentException.class, () -> ScalingMath.computeTransform(0, 1080, 640, 480, ScalingMode.FIT));
    }
}
