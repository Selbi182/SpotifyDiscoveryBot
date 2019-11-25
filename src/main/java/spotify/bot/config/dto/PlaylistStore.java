package spotify.bot.config.dto;

import java.util.Date;

import spotify.bot.util.data.AlbumGroupExtended;

public class PlaylistStore {
	private final AlbumGroupExtended albumGroupExtended;
	private String playlistId;
	private Date lastUpdate;

	public PlaylistStore(AlbumGroupExtended albumGroup) {
		this.albumGroupExtended = albumGroup;
	}

	/////////////

	public AlbumGroupExtended getAlbumGroupExtended() {
		return albumGroupExtended;
	}

	public String getPlaylistId() {
		return playlistId;
	}

	public Date getLastUpdate() {
		return lastUpdate;
	}

	/////////////

	public void setPlaylistId(String playlistId) {
		this.playlistId = playlistId;
	}

	public void setLastUpdate(Date lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	/////////////

	@Override
	public String toString() {
		return String.format("PlaylistStore<%s>", albumGroupExtended.toString());
	}
}
