package spotify.bot.config.dto;

import java.util.Map;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import spotify.bot.util.data.AlbumGroupExtended;

public class BlacklistedArtists {
	private final Multimap<String, AlbumGroupExtended> bannedTypesForArtists;

	// TODO blacklist certain release types for artists
	
	public BlacklistedArtists(Map<String, String> bannedMappings) {
		this.bannedTypesForArtists = HashMultimap.create();
		for (Map.Entry<String, String> entry : bannedMappings.entrySet()) {
			String artistdId = entry.getKey();
			String[] bannedTypes = entry.getValue().split(",");
			for (String bannedType : bannedTypes) {
				AlbumGroupExtended age = AlbumGroupExtended.valueOf(bannedType.strip());
				bannedTypesForArtists.put(artistdId, age);
			}
		}
	}
}
