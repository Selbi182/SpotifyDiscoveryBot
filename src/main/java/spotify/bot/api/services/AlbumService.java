package spotify.bot.api.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.Artist;
import com.wrapper.spotify.model_objects.specification.ArtistSimplified;

import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyCall;
import spotify.bot.config.dto.PlaylistStoreConfig;
import spotify.bot.config.dto.SpotifyApiConfig;
import spotify.bot.util.BotUtils;

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
		// every single artist in a simple for-each is just as fast as any more advanced
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
		List<AlbumSimplified> albumsOfCurrentArtist = SpotifyCall.executePaging(spotifyApi
			.getArtistsAlbums(artistId)
			.market(spotifyApiConfig.getMarket())
			.limit(MAX_ALBUM_FETCH_LIMIT)
			.album_type(albumGroups));
		return attachOriginArtistIdForAppearsOnReleases(artistId, albumsOfCurrentArtist);
	}

	/**
	 * Attach the artist IDs for any appears_on releases so they won't get lost down
	 * the way. For performance reasons, the proper conversion to an Artist object
	 * is done after the majority of filtering is completed (more specifically,
	 * after the previously cached releases have been removed).<br/>
	 * <br/>
	 * 
	 * See also {@link AlbumService#insertViaAppearsOnArtists}.
	 * 
	 * @param artistId
	 * @param albumsOfArtist
	 * @return
	 */
	private List<AlbumSimplified> attachOriginArtistIdForAppearsOnReleases(String artistId, List<AlbumSimplified> albumsOfArtist) {
		List<AlbumSimplified> albumsExtended = new ArrayList<>();
		for (AlbumSimplified as : albumsOfArtist) {
			as = as.getAlbumGroup().equals(AlbumGroup.APPEARS_ON)
				? appendStringToArtist(artistId, as)
				: as;
			albumsExtended.add(as);
		}
		return albumsExtended;
	}

	/**
	 * Quick (and dirty) way to wrap the artist ID inside an ArtistSimplified and
	 * append it to the list of actual artists of this AlbumSimplified.
	 * 
	 * @param artistId
	 * @param as
	 * @return
	 */
	private AlbumSimplified appendStringToArtist(String artistId, AlbumSimplified as) {
		ArtistSimplified[] appendedArtists = new ArtistSimplified[as.getArtists().length + 1];

		ArtistSimplified wrappedArtistId = new ArtistSimplified.Builder()
			.setName(artistId)
			.build();
		appendedArtists[appendedArtists.length - 1] = wrappedArtistId;
		for (int i = 0; i < appendedArtists.length - 1; i++) {
			appendedArtists[i] = as.getArtists()[i];
		}

		// Builders can't copy-construct for some reason, so I gotta copy everything
		// else over as well... Only keeping it to the important attributes though
		return as.builder()
			.setArtists(appendedArtists)
			.setAlbumGroup(as.getAlbumGroup())
			.setAlbumType(as.getAlbumType())
			.setId(as.getId())
			.setName(as.getName())
			.setReleaseDate(as.getReleaseDate())
			.setReleaseDatePrecision(as.getReleaseDatePrecision())
			.build();
	}

	/**
	 * Replace any appears_on releases' artists that were preserved in
	 * {@link AlbumService#attachOriginArtistIdForAppearsOnReleases}.
	 * 
	 * @param filteredAlbums
	 * @return
	 * @throws BotException
	 */
	public List<AlbumSimplified> resolveViaAppearsOnArtistNames(List<AlbumSimplified> filteredAlbums) throws BotException {
		List<String> relevantAppearsOnArtistsIds = filteredAlbums.stream()
			.filter(album -> AlbumGroup.APPEARS_ON.equals(album.getAlbumGroup()))
			.map(BotUtils::getLastArtistName)
			.collect(Collectors.toList());

		Map<String, String> artistIdToName = new HashMap<>();
		for (List<String> sublistArtistIds : Lists.partition(relevantAppearsOnArtistsIds, 50)) {
			Artist[] execute = SpotifyCall.execute(spotifyApi.getSeveralArtists(sublistArtistIds.toArray(String[]::new)));
			for (Artist a : execute) {
				artistIdToName.put(a.getId(), a.getName());
			}
		}

		for (AlbumSimplified as : filteredAlbums) {
			if (AlbumGroup.APPEARS_ON.equals(as.getAlbumGroup())) {
				String viaArtistId = BotUtils.getLastArtistName(as);
				String viaArtistName = artistIdToName.get(viaArtistId);
				if (viaArtistName != null) {
					ArtistSimplified viaArtistWithName = new ArtistSimplified.Builder()
						.setId(viaArtistId)
						.setName(String.format("(%s)", viaArtistName))
						.build();
					ArtistSimplified[] artists = as.getArtists();
					artists[artists.length - 1] = viaArtistWithName;
				}
			}
		}
		return filteredAlbums;
	}
}
