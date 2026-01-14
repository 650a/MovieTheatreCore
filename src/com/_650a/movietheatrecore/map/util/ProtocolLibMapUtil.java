package com._650a.movietheatrecore.map.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;

import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import com._650a.movietheatrecore.util.MapUtil;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;

public class ProtocolLibMapUtil implements MapUtil {

	private static final int MAP_SIZE = 128;

	private final ProtocolManager protocolManager;
	private final ReflectiveMapUtil fallback = new ReflectiveMapUtil();

	public ProtocolLibMapUtil() {
		this.protocolManager = ProtocolLibrary.getProtocolManager();
	}

	@Override
	public void update(Player player, int id, byte[] buffer) {
		if (buffer == null) {
			return;
		}
		if (player == null) {
			return;
		}
		if (!sendPacket(player, id, buffer)) {
			fallback.update(player, id, buffer);
		}
	}

	@Override
	public MapView getMapView(int id) {
		return fallback.getMapView(id);
	}

	@Override
	public int getMapId(MapView mapView) {
		return fallback.getMapId(mapView);
	}

	private boolean sendPacket(Player player, int id, byte[] buffer) {
		try {
			PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.MAP);
			packet.getIntegers().write(0, id);
			if (packet.getBytes().size() > 0) {
				packet.getBytes().write(0, (byte) 0);
			}
			if (packet.getBooleans().size() > 0) {
				packet.getBooleans().write(0, false);
			}
			if (packet.getBooleans().size() > 1) {
				packet.getBooleans().write(1, false);
			}
			if (packet.getSpecificModifier(java.util.List.class).size() > 0) {
				packet.getSpecificModifier(java.util.List.class).write(0, Collections.emptyList());
			}
			if (!writeWrappedMapData(packet, buffer) && packet.getByteArrays().size() > 0) {
				packet.getByteArrays().write(0, buffer);
			}
			protocolManager.sendServerPacket(player, packet);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private boolean writeWrappedMapData(PacketContainer packet, byte[] buffer) {
		try {
			Class<?> clazz = Class.forName("com.comphenix.protocol.wrappers.WrappedMapData");
			Object data = createWrappedMapData(clazz, buffer);
			if (data == null) {
				return false;
			}
			packet.getModifier().withType(clazz).write(0, data);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private Object createWrappedMapData(Class<?> clazz, byte[] buffer) {
		try {
			for (Method method : clazz.getMethods()) {
				if (!Modifier.isStatic(method.getModifiers())) {
					continue;
				}
				Class<?>[] params = method.getParameterTypes();
				if ("fromPixels".equals(method.getName()) && params.length == 5
						&& params[0] == int.class && params[1] == int.class && params[2] == int.class
						&& params[3] == int.class && params[4] == byte[].class) {
					return method.invoke(null, 0, 0, MAP_SIZE, MAP_SIZE, buffer);
				}
				if ("fromPixels".equals(method.getName()) && params.length == 1 && params[0] == byte[].class) {
					return method.invoke(null, buffer);
				}
			}
			for (Constructor<?> constructor : clazz.getConstructors()) {
				Class<?>[] params = constructor.getParameterTypes();
				if (params.length == 5 && params[0] == int.class && params[1] == int.class && params[2] == int.class
						&& params[3] == int.class && params[4] == byte[].class) {
					return constructor.newInstance(0, 0, MAP_SIZE, MAP_SIZE, buffer);
				}
			}
		} catch (Throwable ignored) {
			return null;
		}
		return null;
	}
}
