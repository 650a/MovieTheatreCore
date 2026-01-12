package fr.xxathyx.mediaplayer.ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import fr.xxathyx.mediaplayer.Main;
public class FFprobeService {

	private final Main plugin = Main.getPlugin(Main.class);

	public ProbeResult probe(File target) throws IOException {
		String executable = plugin.getFfprobe().getExecutablePath();

		List<String> command = new ArrayList<>();
		command.add(FilenameUtils.separatorsToUnix(executable));
		command.addAll(Arrays.asList(
				"-v", "error",
				"-print_format", "json",
				"-show_streams",
				"-show_format",
				FilenameUtils.separatorsToUnix(target.getAbsolutePath())));

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);
		Process process;
		try {
			process = processBuilder.start();
		}catch (IOException e) {
			throw new IOException("ffprobe executable not available: " + executable, e);
		}

		StringBuilder output = new StringBuilder();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while((line = reader.readLine()) != null) {
				if(!line.isBlank()) {
					output.append(line.trim());
				}
			}
		}

		int exitCode;
		try {
			exitCode = process.waitFor();
		}catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("ffprobe interrupted", e);
		}

		if(output.isEmpty()) {
			throw new IOException("ffprobe returned no output (exit code " + exitCode + ").");
		}

		JSONObject json = parseJson(output.toString());
		return parseProbeResult(json);
	}

	private JSONObject parseJson(String output) throws IOException {
		String trimmed = output.trim();
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if(start >= 0 && end > start && (start != 0 || end != trimmed.length() - 1)) {
			trimmed = trimmed.substring(start, end + 1);
		}
		try {
			Object parsed = new JSONParser().parse(trimmed);
			if(parsed instanceof JSONObject json) {
				return json;
			}
			throw new IOException("ffprobe output is not JSON.");
		}catch (ParseException e) {
			throw new IOException("Failed to parse ffprobe output.", e);
		}
	}

	private ProbeResult parseProbeResult(JSONObject json) {
		JSONArray streams = (JSONArray) json.get("streams");
		JSONObject format = (JSONObject) json.get("format");

		int width = 0;
		int height = 0;
		double framerate = 0;
		int frames = 0;
		Double streamDuration = null;
		int audioStreams = 0;

		if(streams != null) {
			for(Object entry : streams) {
				if(!(entry instanceof JSONObject stream)) {
					continue;
				}
				String codecType = asString(stream.get("codec_type"));
				if("audio".equals(codecType)) {
					audioStreams++;
				}
				if("video".equals(codecType) && width == 0 && height == 0) {
					width = parseInt(stream.get("width"));
					height = parseInt(stream.get("height"));
					framerate = parseFraction(asString(stream.get("r_frame_rate")));
					frames = parseInt(stream.get("nb_frames"));
					streamDuration = parseDouble(stream.get("duration"));
				}
			}
		}

		Double formatDuration = format == null ? null : parseDouble(format.get("duration"));
		double duration = 0;
		if(formatDuration != null && formatDuration > 0) {
			duration = formatDuration;
		}else if(streamDuration != null && streamDuration > 0) {
			duration = streamDuration;
		}

		return new ProbeResult(width, height, framerate, duration, frames, audioStreams);
	}

	private String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private double parseFraction(String value) {
		if(value == null || value.isBlank()) {
			return 0;
		}
		if(value.contains("/")) {
			String[] parts = value.split("/", 2);
			double numerator = parseDoubleOrZero(parts[0]);
			double denominator = parseDoubleOrZero(parts[1]);
			if(denominator == 0) {
				return 0;
			}
			return numerator / denominator;
		}
		return parseDoubleOrZero(value);
	}

	private double parseDoubleOrZero(String value) {
		Double parsed = parseDouble(value);
		return parsed == null ? 0 : parsed;
	}

	private Double parseDouble(Object value) {
		if(value == null) {
			return null;
		}
		if(value instanceof Number number) {
			return number.doubleValue();
		}
		String asString = value.toString().trim();
		if(asString.isBlank()) {
			return null;
		}
		try {
			return Double.parseDouble(asString);
		}catch (NumberFormatException e) {
			return null;
		}
	}

	private int parseInt(Object value) {
		if(value == null) {
			return 0;
		}
		if(value instanceof Number number) {
			return number.intValue();
		}
		String asString = value.toString().trim();
		if(asString.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(asString);
		}catch (NumberFormatException e) {
			return 0;
		}
	}

	public static class ProbeResult {
		public final int width;
		public final int height;
		public final double framerate;
		public final double duration;
		public final int frames;
		public final int audioStreams;

		public ProbeResult(int width, int height, double framerate, double duration, int frames, int audioStreams) {
			this.width = width;
			this.height = height;
			this.framerate = framerate;
			this.duration = duration;
			this.frames = frames;
			this.audioStreams = audioStreams;
		}
	}
}
