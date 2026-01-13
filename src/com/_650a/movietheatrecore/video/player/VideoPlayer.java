package com._650a.movietheatrecore.video.player;

import com._650a.movietheatrecore.screen.Screen;

public class VideoPlayer {
	
	private Screen screen;
	
	public VideoPlayer(Screen screen) {
		this.screen = screen;
	}
	
	public Screen getScreen() {
		return screen;
	}
}