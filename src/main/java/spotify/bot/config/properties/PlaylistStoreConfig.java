package spotify.bot.config.properties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Configuration;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.User;
import spotify.api.SpotifyCall;
import spotify.api.SpotifyDependenciesSettings;
import spotify.api.events.SpotifyApiException;
import spotify.bot.service.PlaylistMetaService;
import spotify.bot.util.DiscoveryBotLogger;
import spotify.bot.util.DiscoveryBotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.services.UserService;

@Configuration
public class PlaylistStoreConfig {
	private final static String PLAYLIST_PROPERTIES_FILENAME = "playlist.properties";
	private final static String PLAYLIST_URI_PREFIX = "https://open.spotify.com/playlist/";

	private Map<AlbumGroupExtended, PlaylistStore> playlistStoreMap;

	private final List<AlbumGroupExtended> defaultPlaylistGroupOrderReversed;

	private final List<AlbumGroupExtended> enabledAlbumGroups;
	private final List<AlbumGroupExtended> disabledAlbumGroups;

	private final SpotifyApi spotifyApi;
	private final UserService userService;
	private final DiscoveryBotLogger log;

	private final File playlistPropertiesFile;

	PlaylistStoreConfig(SpotifyApi spotifyApi, UserService userService, DiscoveryBotLogger discoveryBotLogger, SpotifyDependenciesSettings spotifyDependenciesSettings) {
		this.spotifyApi = spotifyApi;
		this.userService = userService;
		this.log = discoveryBotLogger;
		this.enabledAlbumGroups = new ArrayList<>();
		this.disabledAlbumGroups = new ArrayList<>();
		this.playlistPropertiesFile = new File(spotifyDependenciesSettings.configFilesBase(), PLAYLIST_PROPERTIES_FILENAME);
		this.defaultPlaylistGroupOrderReversed = DiscoveryBotUtils.reversedList(DiscoveryBotUtils.DEFAULT_PLAYLIST_GROUP_ORDER);
	}

	public void setupPlaylistStores() {
		try {
			if (!playlistPropertiesFile.exists()) {
				if (playlistPropertiesFile.getParentFile().mkdirs()) {
					log.info(playlistPropertiesFile.getParent() + " folder was automatically created");
				}
				if (playlistPropertiesFile.createNewFile()) {
					log.info("Playlist properties file not found. Creating new playlists for each album group and linking them to the file...");
				}
			}
			FileReader reader = new FileReader(playlistPropertiesFile);
			Properties properties = new Properties();
			properties.load(reader);

			convertPlaylistUrlsToIds(properties);
			verifyPlaylistsAndCreateMissingOnes(properties);
			this.playlistStoreMap = createPlaylistStoreMap(properties);

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
			throw new IllegalStateException("Failed to read " + playlistPropertiesFile + ". Terminating!");
		}
	}

	private void convertPlaylistUrlsToIds(Properties properties) throws IOException {
		boolean changes = false;
		for (AlbumGroupExtended albumGroupExtended : this.defaultPlaylistGroupOrderReversed) {
			String key = albumGroupExtended.getGroupName();
			String playlistIdOrUrl = properties.getProperty(key);
			if (playlistIdOrUrl != null && playlistIdOrUrl.startsWith(PLAYLIST_URI_PREFIX)) {
				try {
					URL url = new URL(playlistIdOrUrl);
					String path = url.getPath();
					String playlistId = path.substring(path.lastIndexOf('/') + 1);
					properties.setProperty(key, playlistId);
					changes = true;
				} catch (Exception e) {
					throw new IOException("Invalid /playlist URL for album group '" + albumGroupExtended.getGroupName() + "'");
				}
			}
		}
		if (changes) {
			properties.store(new FileOutputStream(playlistPropertiesFile), null);
		}
	}

	private void verifyPlaylistsAndCreateMissingOnes(Properties properties) throws IOException {
		boolean changes = false;
		for (AlbumGroupExtended albumGroupExtended : this.defaultPlaylistGroupOrderReversed) {
			String key = albumGroupExtended.getGroupName();
			String playlistId = properties.getProperty(key);
			if (playlistId != null && !playlistId.isBlank()) {
				Playlist playlist;
				try {
					playlist = SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));
				} catch (SpotifyApiException e) {
					throw new IOException("Playlist ID for '" + albumGroupExtended.getGroupName() + "' does not point to an existing playlist");
				}
				User playlistOwner = playlist.getOwner();
				User currentUser = userService.getCurrentUser();
				if (!Objects.equals(playlistOwner.getId(), currentUser.getId())) {
					throw new IOException("You are not the owner of the playlist for '" + albumGroupExtended.getGroupName() + "'");
				}
				enabledAlbumGroups.add(albumGroupExtended);
			} else {
				if (properties.containsKey(key)) {
					disabledAlbumGroups.add(albumGroupExtended);
				} else {
					String playlistName = PlaylistMetaService.INDICATOR_OFF + " New " + albumGroupExtended.getHumanName();
					Playlist newPlaylist = SpotifyCall.execute(spotifyApi.createPlaylist(userService.getCurrentUser().getId(), playlistName).public_(false));
					properties.putIfAbsent(albumGroupExtended.getGroupName(), newPlaylist.getId());
					changes = true;
				}
			}
		}
		if (!disabledAlbumGroups.isEmpty()) {
			log.warning("Disabled album groups (no IDs set in " + playlistPropertiesFile + "): " + disabledAlbumGroups);
		}
		if (changes) {
			properties.store(new FileOutputStream(playlistPropertiesFile), null);
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
	 * Returns all set playlist stores.
	 */
	public Collection<PlaylistStore> getEnabledPlaylistStores() {
		return getAllPlaylistStores().stream()
				.filter(ps -> getEnabledAlbumGroups().contains(ps.getAlbumGroupExtended()))
				.collect(Collectors.toList());
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
	 * Returns the list of album groups that are enabled
	 */
	public List<AlbumGroupExtended> getEnabledAlbumGroups() {
		return enabledAlbumGroups;
	}

	/**
	 * Returns the list of album groups that were disabled in the playlist.properties
	 */
	public List<AlbumGroupExtended> getDisabledAlbumGroups() {
		return disabledAlbumGroups;
	}

	/**
	 * Set the playlist store for this album group to be last updated just now
	 */
	public void setPlaylistStoreUpdatedJustNow(AlbumGroupExtended albumGroup) {
		playlistStoreMap.get(albumGroup).setLastUpdate(LocalDateTime.now());
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

		private LocalDateTime lastUpdate;

		public PlaylistStore(AlbumGroupExtended albumGroupExtended, String playlistId) {
			this.albumGroupExtended = albumGroupExtended;
			this.playlistId = playlistId;
		}

		/////////////

		public AlbumGroupExtended getAlbumGroupExtended() {
			return albumGroupExtended;
		}

		public String getPlaylistId() {
			return playlistId;
		}

		public LocalDateTime getLastUpdate() {
			return lastUpdate;
		}

		public void setLastUpdate(LocalDateTime lastUpdate) {
			this.lastUpdate = lastUpdate;
		}

		/////////////

		@Override
		public String toString() {
			return String.format("PlaylistStore<%s>", albumGroupExtended.toString());
		}

		/////////////

		@Override
		public int compareTo(PlaylistStore o) {
			return Integer.compare(
					DiscoveryBotUtils.DEFAULT_PLAYLIST_GROUP_ORDER.indexOf(this.getAlbumGroupExtended()),
					DiscoveryBotUtils.DEFAULT_PLAYLIST_GROUP_ORDER.indexOf(o.getAlbumGroupExtended()));
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
