package fr.xxathyx.mediaplayer.render;

import java.awt.image.BufferedImage;

public final class MapTileSplitter {
    private MapTileSplitter() {
    }

    public static BufferedImage[] split(BufferedImage image, int tilesWide, int tilesHigh) {
        BufferedImage[] tiles = new BufferedImage[tilesWide * tilesHigh];
        int index = 0;

        for (int y = 0; y < tilesHigh; y++) {
            for (int x = 0; x < tilesWide; x++) {
                int startX = x * 128;
                int startY = y * 128;
                tiles[index++] = image.getSubimage(startX, startY, 128, 128);
            }
        }

        return tiles;
    }
}
