package spotify.bot.api.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyCall;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.SpotifyApiConfig;

@Service
public class AlbumService {

	private final static int MAX_ALBUM_FETCH_LIMIT = 50;

	@Autowired
	private SpotifyApiConfig spotifyApiConfig;

	@Autowired
	private PlaylistStoreConfig playlistStoreConfig;

	@Autowired
	private SpotifyApi spotifyApi;

	/**
	 * Fetch all albums of the given artists. (Note: This will very likely take up
	 * the majority of the crawling process, as it requires to fire at least one
	 * Spotify Web API request for EVERY SINGLE ARTIST!)
	 * 
	 * @param followedArtists
	 * @param albumGroups
	 * @return
	 */
	public List<AlbumSimplified> getAllAlbumsOfArtists(List<String> followedArtists) throws BotException {
		Collection<AlbumGroup> enabledAlbumGroups = playlistStoreConfig.getEnabledAlbumGroups();
		String albumGroupString = createAlbumGroupString(enabledAlbumGroups);

		// I've tried just about anything you can imagine. Parallel streams, threads,
		// thread pools, custom sleep intervals. It doesn't matter. Going through
		// every single artist in a simple for-loop is just as fast as any more advanced
		// solution, while still being way more straightforward and comprehensible.
		// I wish Spotify's API allowed for fetching multiple artists' albums at once.

		List<AlbumSimplified> albums = new ArrayList<>();
		for (String artist : followedArtists) {
			List<AlbumSimplified> albumIdsOfSingleArtist = getAlbumIdsOfSingleArtist(artist, albumGroupString);
			albums.addAll(albumIdsOfSingleArtist);
		}
		return albums;
	}

	/**
	 * Creates the comma-delimited, lowercase String of album groups to search for
	 * 
	 * @param enabledAlbumGroups
	 * @return
	 */
	private String createAlbumGroupString(Collection<AlbumGroup> enabledAlbumGroups) {
		StringJoiner albumGroupsAsString = new StringJoiner(",");
		for (AlbumGroup ag : enabledAlbumGroups) {
			albumGroupsAsString.add(ag.getGroup());
		}
		return albumGroupsAsString.toString();
	}

	/**
	 * Return the albums of a single given artist
	 * 
	 * @param artistId
	 * @param albumGroup
	 * @return
	 */
	private List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumGroups) throws BotException {
		List<AlbumSimplified> albumsOfCurrentArtist = Collections.emptyList();
		albumsOfCurrentArtist = SpotifyCall.executePaging(spotifyApi
			.getArtistsAlbums(artistId)
			.market(spotifyApiConfig.getMarket())
			.limit(MAX_ALBUM_FETCH_LIMIT)
			.album_type(albumGroups));
		return albumsOfCurrentArtist;
	}
}
