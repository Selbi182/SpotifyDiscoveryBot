package spotify.util;

import java.util.Collections;
import java.util.List;

import com.wrapper.spotify.model_objects.specification.Album;

public final class BotUtils {

	private BotUtils() {
	}

	/**
	 * Sort the album list with the default comparator
	 * 
	 * @param albums
	 */
	public static void sortAlbums(List<Album> albums) {
		Collections.sort(albums, (a1, a2) -> Constants.RELEASE_COMPARATOR.compare(a1, a2));
	}

	/**
	 * Check if the given playlistId has been set
	 */
	public static boolean isPlaylistSet(String playlistId) {
		return playlistId != null && !playlistId.trim().isEmpty();
	}

}
