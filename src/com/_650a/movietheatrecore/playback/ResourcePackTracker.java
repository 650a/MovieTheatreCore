package com._650a.movietheatrecore.playback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;

import com._650a.movietheatrecore.configuration.Configuration;

public class ResourcePackTracker {

	public enum PackStatus {
		UNKNOWN,
		ACCEPTED,
		DECLINED,
		FAILED
	}

	private static final class PackState {
		private String lastUrl;
		private String lastSha;
		private long lastSentAt;
		private PackStatus status = PackStatus.UNKNOWN;
	}

	private final Map<UUID, PackState> states = new ConcurrentHashMap<>();
	private final Configuration configuration;

	public ResourcePackTracker(Configuration configuration) {
		this.configuration = configuration;
	}

	public boolean shouldSend(Player player, String url, byte[] sha1) {
		if (player == null || url == null || url.isBlank()) {
			return false;
		}
		UUID uuid = player.getUniqueId();
		PackState state = states.computeIfAbsent(uuid, key -> new PackState());
		if (state.status == PackStatus.DECLINED) {
			return false;
		}

		String sha = toShaString(sha1);
		boolean changed = !safeEquals(url, state.lastUrl) || !safeEquals(sha, state.lastSha);
		boolean neverAccepted = state.status != PackStatus.ACCEPTED;
		long cooldownMillis = Math.max(0, configuration.resourcepack_apply_cooldown_seconds()) * 1000L;
		long now = System.currentTimeMillis();
		if (state.lastSentAt > 0 && cooldownMillis > 0 && now - state.lastSentAt < cooldownMillis) {
			return false;
		}
		return changed || neverAccepted || state.lastSentAt == 0;
	}

	public void markSent(Player player, String url, byte[] sha1) {
		if (player == null) {
			return;
		}
		UUID uuid = player.getUniqueId();
		PackState state = states.computeIfAbsent(uuid, key -> new PackState());
		state.lastUrl = url;
		state.lastSha = toShaString(sha1);
		state.lastSentAt = System.currentTimeMillis();
		state.status = PackStatus.UNKNOWN;
	}

	public void recordStatus(Player player, Status status) {
		if (player == null || status == null) {
			return;
		}
		UUID uuid = player.getUniqueId();
		PackState state = states.computeIfAbsent(uuid, key -> new PackState());
		if (status == Status.SUCCESSFULLY_LOADED) {
			state.status = PackStatus.ACCEPTED;
		} else if (status == Status.DECLINED) {
			state.status = PackStatus.DECLINED;
		} else if (status == Status.FAILED_DOWNLOAD) {
			state.status = PackStatus.FAILED;
		}
	}

	public PackStatus getStatus(Player player) {
		if (player == null) {
			return PackStatus.UNKNOWN;
		}
		PackState state = states.get(player.getUniqueId());
		return state == null ? PackStatus.UNKNOWN : state.status;
	}

	public void clear(Player player) {
		if (player == null) {
			return;
		}
		states.remove(player.getUniqueId());
	}

	private String toShaString(byte[] sha1) {
		if (sha1 == null || sha1.length == 0) {
			return "";
		}
		StringBuilder builder = new StringBuilder(sha1.length * 2);
		for (byte b : sha1) {
			builder.append(String.format("%02x", b));
		}
		return builder.toString();
	}

	private boolean safeEquals(String left, String right) {
		if (left == null) {
			return right == null || right.isBlank();
		}
		if (right == null) {
			return left.isBlank();
		}
		return left.equals(right);
	}
}
