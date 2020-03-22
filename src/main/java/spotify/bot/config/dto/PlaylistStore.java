package spotify.bot.config.dto;

import java.util.Date;

import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

public class PlaylistStore implements Comparable<PlaylistStore> {
	private final AlbumGroupExtended albumGroupExtended;
	private String playlistId;
	private Date lastUpdate;

	public PlaylistStore(AlbumGroupExtended albumGroupExtended) {
		this.albumGroupExtended = albumGroupExtended;
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

	///////////// a

	@Override
	public int compareTo(PlaylistStore o) {
		return BotUtils.DEFAULT_PLAYLIST_GROUP_ORDER_COMPARATOR.compare(this.getAlbumGroupExtended(), o.getAlbumGroupExtended());
	}
}
