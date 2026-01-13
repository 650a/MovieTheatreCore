package com._650a.movietheatrecore.audio.util;

import org.bukkit.entity.Player;

import com._650a.movietheatrecore.util.AudioUtil;

public class Above implements AudioUtil {

	@Override
	public void stopAudio(Player player, String sound) {
		if(player == null || sound == null) return;
		try {
			player.getClass().getMethod("stopSound", String.class).invoke(player, sound);
			return;
		}catch (ReflectiveOperationException ignored) {
		}
		try {
			org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(),
					"stopsound " + player.getName() + " master " + sound);
		}catch (Exception ignored) {
		}
	}
}
