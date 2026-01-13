package com._650a.movietheatrecore.actionbar;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.bukkit.entity.Player;

import com._650a.movietheatrecore.util.ActionBar;

public class ReflectiveActionBar implements ActionBar {

	@Override
	public void send(Player player, String text) {
		if(player == null || text == null) return;
		if(sendWithSpigot(player, text)) return;
		player.sendMessage(text);
	}

	private boolean sendWithSpigot(Player player, String text) {
		try {
			Class<?> chatMessageType = Class.forName("net.md_5.bungee.api.ChatMessageType");
			Class<?> baseComponent = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
			Class<?> textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent");
			Object component = textComponent.getConstructor(String.class).newInstance(text);
			Object actionBar = Enum.valueOf(chatMessageType.asSubclass(Enum.class), "ACTION_BAR");
			Object spigot = player.getClass().getMethod("spigot").invoke(player);

			Method sendMessage = null;
			for(Method method : spigot.getClass().getMethods()) {
				Class<?>[] params = method.getParameterTypes();
				if(method.getName().equals("sendMessage") && params.length == 2 && params[0].equals(chatMessageType)
						&& params[1].isArray() && params[1].getComponentType().equals(baseComponent)) {
					sendMessage = method;
					break;
				}
			}
			if(sendMessage == null) return false;

			Object componentArray = Array.newInstance(baseComponent, 1);
			Array.set(componentArray, 0, component);
			sendMessage.invoke(spigot, actionBar, componentArray);
			return true;
		}catch (ReflectiveOperationException | IllegalArgumentException ignored) {
			return false;
		}
	}
}
