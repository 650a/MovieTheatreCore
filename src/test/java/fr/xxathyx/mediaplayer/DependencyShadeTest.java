package fr.xxathyx.mediaplayer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class DependencyShadeTest {

    @Test
    void xzCompressorClassIsLoadable() throws Exception {
        Class<?> clazz = Class.forName("org.apache.commons.compress.compressors.xz.XZCompressorInputStream");
        assertNotNull(clazz, "XZCompressorInputStream should be available on the classpath");
    }
}
