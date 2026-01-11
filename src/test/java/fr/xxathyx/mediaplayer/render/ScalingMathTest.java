package fr.xxathyx.mediaplayer.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScalingMathTest {

    @Test
    void fitScalesWithLetterboxForFiveByThreeMaps() {
        ScalingTransform transform = ScalingMath.computeTransform(1920, 1080, 640, 384, ScalingMode.FIT);
        assertEquals(640, Math.round(1920 * transform.scaleX()));
        assertEquals(360, Math.round(1080 * transform.scaleY()));
        assertEquals(0, transform.offsetX());
        assertEquals(12, transform.offsetY());
    }

    @Test
    void fillScalesWithCropForFiveByThreeMaps() {
        ScalingTransform transform = ScalingMath.computeTransform(1920, 1080, 640, 384, ScalingMode.FILL);
        assertEquals(640, Math.round(transform.cropWidth() * transform.scaleX()));
        assertEquals(384, Math.round(transform.cropHeight() * transform.scaleY()));
    }

    @Test
    void fitScalesSquareDestination() {
        ScalingTransform transform = ScalingMath.computeTransform(1280, 720, 512, 512, ScalingMode.FIT);
        assertEquals(512, Math.round(1280 * transform.scaleX()));
        assertEquals(288, Math.round(720 * transform.scaleY()));
        assertEquals(0, transform.offsetX());
        assertEquals(112, transform.offsetY());
    }

    @Test
    void fillScalesSquareSourceToFiveByThreeMaps() {
        ScalingTransform transform = ScalingMath.computeTransform(1024, 1024, 640, 384, ScalingMode.FILL);
        assertEquals(640, Math.round(transform.cropWidth() * transform.scaleX()));
        assertEquals(384, Math.round(transform.cropHeight() * transform.scaleY()));
    }

    @Test
    void stretchScalesIndependently() {
        ScalingTransform transform = ScalingMath.computeTransform(640, 480, 1280, 720, ScalingMode.STRETCH);
        assertEquals(1280, Math.round(640 * transform.scaleX()));
        assertEquals(720, Math.round(480 * transform.scaleY()));
        assertEquals(0, transform.offsetX());
        assertEquals(0, transform.offsetY());
    }

    @Test
    void invalidDimensionsThrow() {
        assertThrows(IllegalArgumentException.class, () -> ScalingMath.computeTransform(0, 1080, 640, 384, ScalingMode.FIT));
    }
}
