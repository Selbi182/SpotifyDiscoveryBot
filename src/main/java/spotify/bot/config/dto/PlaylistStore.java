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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((albumGroupExtended == null) ? 0 : albumGroupExtended.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PlaylistStore other = (PlaylistStore) obj;
		if (albumGroupExtended != other.albumGroupExtended)
			return false;
		return true;
	}
}
