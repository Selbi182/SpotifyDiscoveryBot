package spotify.bot.config.dto;

import java.util.Date;

import com.wrapper.spotify.enums.AlbumGroup;

public class PlaylistStoreDTO {
	private final AlbumGroup albumGroup;
	private String playlistId;
	private AlbumGroup parentAlbumGroup;
	private Date lastUpdate;

	public PlaylistStoreDTO(AlbumGroup albumGroup) {
		this.albumGroup = albumGroup;
	}

	/////////////

	public AlbumGroup getAlbumGroup() {
		return albumGroup;
	}

	public String getPlaylistId() {
		return playlistId;
	}

	public AlbumGroup getParentAlbumGroup() {
		return parentAlbumGroup;
	}

	public Date getLastUpdate() {
		return lastUpdate;
	}

	/////////////

	public void setPlaylistId(String playlistId) {
		this.playlistId = playlistId;
	}

	public void setParentAlbumGroup(AlbumGroup parentAlbumGroup) {
		this.parentAlbumGroup = parentAlbumGroup;
	}

	public void setLastUpdate(Date lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	/////////////

	@Override
	public String toString() {
		return "PlaylistStore [albumGroup=" + albumGroup
			+ ", playlistId=" + playlistId
			+ ", parentAlbumGroup=" + parentAlbumGroup
			+ ", lastUpdated=" + lastUpdate
			+ "]";
	}
}
