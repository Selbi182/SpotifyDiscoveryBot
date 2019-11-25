package spotify.bot.api.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.enums.AlbumGroup;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;

import spotify.bot.api.SpotifyCall;
import spotify.bot.config.Config;

@Service
public class AlbumService {

	private final static int MAX_ALBUM_FETCH_LIMIT = 50;

	@Autowired
	private Config config;

	@Autowired
	private SpotifyApi spotifyApi;

	/**
	 * Fetch all albums of the given artists
	 * 
	 * @param followedArtists
	 * @param albumGroups
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SpotifyWebApiException
	 */
	public List<AlbumSimplified> getAllAlbumsOfArtists(List<String> followedArtists)
		throws IOException, SQLException, SpotifyWebApiException, InterruptedException {
		Collection<AlbumGroup> enabledAlbumGroups = config.getEnabledAlbumGroups();
		List<AlbumSimplified> allAlbums = getAlbumsOfArtists(followedArtists, enabledAlbumGroups);
		return allAlbums;
	}

	/**
	 * Get all album IDs of the given list of artists, mapped into album group
	 * 
	 * @param artists
	 * @return
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	private List<AlbumSimplified> getAlbumsOfArtists(List<String> artists, Collection<AlbumGroup> enabledAlbumGroups) throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		String albumGroupString = createAlbumGroupString(enabledAlbumGroups);
		List<AlbumSimplified> albums = new ArrayList<>();
		for (String a : artists) {
			List<AlbumSimplified> albumsOfCurrentArtist = getAlbumIdsOfSingleArtist(a, albumGroupString);
			albums.addAll(albumsOfCurrentArtist);
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
	 * @throws SQLException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws SpotifyWebApiException
	 */
	private List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumGroups) throws SpotifyWebApiException, IOException, InterruptedException, SQLException {
		List<AlbumSimplified> albumsOfCurrentArtist = SpotifyCall.executePaging(spotifyApi
			.getArtistsAlbums(artistId)
			.market(config.getStaticConfig().getMarket())
			.limit(MAX_ALBUM_FETCH_LIMIT)
			.album_type(albumGroups));
		return albumsOfCurrentArtist;
	}
}
