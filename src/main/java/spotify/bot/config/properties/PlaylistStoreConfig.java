package spotify.bot.config.properties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Configuration;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import spotify.api.SpotifyCall;
import spotify.bot.service.performance.CachedUserService;
import spotify.bot.service.PlaylistMetaService;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;

@Configuration
public class PlaylistStoreConfig {
	private final static String PLAYLIST_STORE_FILENAME = "./config/playlist.properties";

	private Map<AlbumGroupExtended, PlaylistStore> playlistStoreMap;

	private final SpotifyApi spotifyApi;
	private final CachedUserService cachedUserService;
	private final DiscoveryBotLogger discoveryBotLogger;

	PlaylistStoreConfig(SpotifyApi spotifyApi, CachedUserService cachedUserService, DiscoveryBotLogger discoveryBotLogger) {
		this.spotifyApi = spotifyApi;
		this.cachedUserService = cachedUserService;
		this.discoveryBotLogger = discoveryBotLogger;
		getPlaylistStoreFromPropertiesFile();
	}

	private void getPlaylistStoreFromPropertiesFile() {
		try {
			File propertiesFile = new File(PLAYLIST_STORE_FILENAME);
			if (!propertiesFile.exists()) {
				if (propertiesFile.getParentFile().mkdirs() & propertiesFile.createNewFile()) {
					discoveryBotLogger.info("Playlist properties file not found. Creating new playlists for each album type and link them to the file");
				}
			}
			FileReader reader = new FileReader(propertiesFile);
			Properties properties = new Properties();
			properties.load(reader);
			createMissingPlaylists(properties);
			this.playlistStoreMap = createPlaylistStoreMap(properties);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to read " + PLAYLIST_STORE_FILENAME + ". Terminating!");
			System.exit(1);
		}
	}

	private void createMissingPlaylists(Properties properties) throws IOException {
		String userId = null;
		for (AlbumGroupExtended albumGroupExtended : DiscoveryBotUtils.DEFAULT_PLAYLIST_GROUP_ORDER_REVERSED) {
			String key = albumGroupExtended.getGroupName();
			if (!properties.containsKey(key)) {
				if (userId == null) {
					userId = cachedUserService.getUserId();
				}
				String playlistName = PlaylistMetaService.INDICATOR_OFF + " " + albumGroupExtended.getHumanName();
				Playlist newPlaylist = SpotifyCall.execute(spotifyApi.createPlaylist(userId, playlistName));
				properties.putIfAbsent(albumGroupExtended.getGroupName(), newPlaylist.getId());
			}
		}
		if (userId != null) {
			properties.store(new FileOutputStream(PLAYLIST_STORE_FILENAME), null);
		}
	}

	private Map<AlbumGroupExtended, PlaylistStore> createPlaylistStoreMap(Properties properties) {
		Map<AlbumGroupExtended, PlaylistStore> playlistStoreMap = new ConcurrentHashMap<>();
		for (AlbumGroupExtended albumGroupExtended : AlbumGroupExtended.values()) {
			String key = albumGroupExtended.getGroupName();
			String playlistId = properties.getProperty(key);
			PlaylistStore playlistStore = new PlaylistStore(albumGroupExtended, playlistId);
			playlistStoreMap.put(albumGroupExtended, playlistStore);
		}
		return playlistStoreMap;
	}

	/////////////////////////
	// PLAYLIST STORE READERS

	/**
	 * Returns the playlist stores as a map
	 */
	public Map<AlbumGroupExtended, PlaylistStore> getPlaylistStoreMap() {
		return playlistStoreMap;
	}

	/**
	 * Returns all set playlist stores.
	 */
	public Collection<PlaylistStore> getAllPlaylistStores() {
		return getPlaylistStoreMap().values();
	}

	/**
	 * 
	 * Returns the stored playlist store by the given album group.
	 */
	public PlaylistStore getPlaylistStore(AlbumGroup albumGroup) {
		return getPlaylistStore(AlbumGroupExtended.fromAlbumGroup(albumGroup));
	}

	/**
	 * Returns the stored playlist store by the given album group.
	 */
	public PlaylistStore getPlaylistStore(AlbumGroupExtended albumGroupExtended) {
		return getPlaylistStoreMap().get(albumGroupExtended);
	}

	/**
	 * Set the playlist store for this album group to be last updated just now
	 */
	public void setPlaylistStoreUpdatedJustNow(AlbumGroupExtended albumGroup) {
		playlistStoreMap.get(albumGroup).setLastUpdate(new Date());
	}

	/**
	 * Set the playlist store for this album group to not have been updated recently
	 */
	public void unsetPlaylistStoreUpdatedRecently(AlbumGroupExtended albumGroup) {
		playlistStoreMap.get(albumGroup).setLastUpdate(null);
	}

	public static class PlaylistStore implements Comparable<PlaylistStore> {
		private final AlbumGroupExtended albumGroupExtended;
		private final String playlistId;

		private Date lastUpdate;
		private boolean forceUpdateOnFirstTime;

		public PlaylistStore(AlbumGroupExtended albumGroupExtended, String playlistId) {
			this.albumGroupExtended = albumGroupExtended;
			this.playlistId = playlistId;
			this.forceUpdateOnFirstTime = true;
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

		public void setLastUpdate(Date lastUpdate) {
			this.lastUpdate = lastUpdate;
		}

		public boolean isForceUpdateOnFirstTime() {
			if (forceUpdateOnFirstTime) {
				forceUpdateOnFirstTime = false;
				return true;
			}
			return false;
		}

		/////////////

		@Override
		public String toString() {
			return String.format("PlaylistStore<%s>", albumGroupExtended.toString());
		}

		/////////////

		@Override
		public int compareTo(PlaylistStore o) {
			return DiscoveryBotUtils.DEFAULT_PLAYLIST_GROUP_ORDER_COMPARATOR.compare(this.getAlbumGroupExtended(), o.getAlbumGroupExtended());
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
			return albumGroupExtended == other.albumGroupExtended;
		}
	}
}
