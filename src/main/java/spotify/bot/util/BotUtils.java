package spotify.bot.util;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Album;

import spotify.bot.Config;

public final class BotUtils {
	/**
	 * Static calls only
	 */
	private BotUtils() {}


	// Comparators
	private final static Comparator<Album> COMPARATOR_ALBUM_TYPE = Comparator.comparing(Album::getAlbumType);
	private final static Comparator<Album> COMPARATOR_RELEASE_DATE = Comparator.comparing(Album::getReleaseDate);
	private final static Comparator<Album> COMPARATOR_FIRST_ARTIST_NAME = (a1, a2) -> a1.getArtists()[0].getName()
			.compareTo(a2.getArtists()[0].getName());
	private final static Comparator<Album> COMPARATOR_ALBUM_NAME = Comparator.comparing(Album::getName);

	private final static Comparator<Album> RELEASE_COMPARATOR =
			COMPARATOR_ALBUM_TYPE
			.thenComparing(COMPARATOR_RELEASE_DATE)
			.thenComparing(COMPARATOR_FIRST_ARTIST_NAME)
			.thenComparing(COMPARATOR_ALBUM_NAME);

	/**
	 * Sort the album list with the default comparator
	 * 
	 * @param albums
	 */
	public static void sortAlbums(List<Album> albums) {
		Collections.sort(albums, (a1, a2) -> RELEASE_COMPARATOR.compare(a1, a2));
	}

	/**
	 * Check if the given playlistId has been set
	 */
	public static boolean isPlaylistSet(String playlistId) {
		return playlistId != null && !playlistId.trim().isEmpty();
	}

	/**
	 * Returns the stored playlist ID by the given album type
	 * 
	 * @param albumType
	 * @return
	 * @throws IOException 
	 */
	public static String getPlaylistIdByType(AlbumType albumType) {
		try {
			switch (albumType) {
				case ALBUM:
						return Config.getInstance().getPlaylistAlbums();
				case SINGLE:
					return Config.getInstance().getPlaylistSingles();
				case COMPILATION:
					return Config.getInstance().getPlaylistCompilations();
				case APPEARS_ON:
					return Config.getInstance().getPlaylistAppearsOn();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
