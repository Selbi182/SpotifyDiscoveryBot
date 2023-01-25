package spotify.bot.util.data;

import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;

import spotify.bot.filter.FilterService;

/**
 * Simple wrapper class used in conjunction with {@link spotify.services.AlbumService} and
 * {@link FilterService} to make sure newly followed artists won't have their
 * entire discography accidentally classified as "re-releases"
 */
public class CachedArtistsContainer {
	private final List<String> allArtists;
	private final List<String> newArtists;

	public CachedArtistsContainer(Collection<String> allArtists, Collection<String> newArtists) {
		this.allArtists = ImmutableList.copyOf(allArtists);
		this.newArtists = ImmutableList.copyOf(newArtists);
	}

	public List<String> getAllArtists() {
		return allArtists;
	}

	public List<String> getNewArtists() {
		return newArtists;
	}
}
