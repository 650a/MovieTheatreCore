package com._650a.movietheatrecore.map.util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import com._650a.movietheatrecore.util.MapUtil;

public class ReflectiveMapUtil implements MapUtil {

	private final Map<Integer, byte[]> buffers = new ConcurrentHashMap<>();

	@Override
	public void update(Player player, int id, byte[] buffer) {
		if(buffer == null) return;
		buffers.put(id, buffer);
		MapView mapView = getMapView(id);
		if(mapView == null) return;
		ensureRenderer(mapView, id);
		sendMap(player, mapView);
	}

	@Override
	public MapView getMapView(int id) {
		try {
			Method method = Bukkit.class.getMethod("getMap", int.class);
			return (MapView) method.invoke(null, id);
		}catch (ReflectiveOperationException ignored) {
		}
		try {
			Method method = Bukkit.class.getMethod("getMap", short.class);
			return (MapView) method.invoke(null, (short) id);
		}catch (ReflectiveOperationException ignored) {
		}
		return null;
	}

	@Override
	public int getMapId(MapView mapView) {
		if(mapView == null) return 0;
		try {
			Method method = mapView.getClass().getMethod("getId");
			Object value = method.invoke(mapView);
			return ((Number) value).intValue();
		}catch (ReflectiveOperationException ignored) {
		}
		return 0;
	}

	private void ensureRenderer(MapView mapView, int id) {
		boolean hasRenderer = false;
		for(MapRenderer renderer : mapView.getRenderers()) {
			if(renderer instanceof BufferRenderer && ((BufferRenderer) renderer).getMapId() == id) {
				hasRenderer = true;
			}else if(renderer instanceof BufferRenderer) {
				mapView.removeRenderer(renderer);
			}
		}
		if(!hasRenderer) {
			mapView.addRenderer(new BufferRenderer(id, buffers));
		}
	}

	private void sendMap(Player player, MapView mapView) {
		if(player == null || mapView == null) return;
		try {
			Method method = player.getClass().getMethod("sendMap", MapView.class);
			method.invoke(player, mapView);
		}catch (ReflectiveOperationException ignored) {
		}
	}

	private static class BufferRenderer extends MapRenderer {

		private final int mapId;
		private final Map<Integer, byte[]> buffers;

		private BufferRenderer(int mapId, Map<Integer, byte[]> buffers) {
			this.mapId = mapId;
			this.buffers = buffers;
		}

		@Override
		public void render(MapView mapView, MapCanvas canvas, Player player) {
			byte[] buffer = buffers.get(mapId);
			if(buffer == null || buffer.length < 16384) return;
			int index = 0;
			for(int y = 0; y < 128; y++) {
				for(int x = 0; x < 128; x++) {
					canvas.setPixel(x, y, buffer[index++]);
				}
			}
		}

		private int getMapId() {
			return mapId;
		}
	}
}
