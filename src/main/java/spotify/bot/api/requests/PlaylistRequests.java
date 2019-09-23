package spotify.bot.api.requests;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.exceptions.detailed.InternalServerErrorException;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.Config;
import spotify.bot.api.SpotifyApiRequest;
import spotify.bot.api.SpotifyApiSessionManager;
import spotify.bot.util.BotUtils;
import spotify.bot.util.Constants;

public class PlaylistRequests {
	/**
	 * Static calls only
	 */
	private PlaylistRequests() {}
	
	/**
	 * Add the given list of song IDs to the playlist (a delay of a second per release is used to retain order).
	 * 
	 * @param albumType
	 * @param songs
	 * @throws Exception 
	 */
	public static void addSongsToPlaylist(List<List<TrackSimplified>> newSongs, AlbumType albumType) throws Exception {
		String playlistId = BotUtils.getPlaylistIdByType(albumType);
		if (!newSongs.isEmpty()) {
			int songsAdded = 0;
			AddToPlaylistLoop:
			for (List<TrackSimplified> songsInAlbum : newSongs) {
				for (List<TrackSimplified> partition : Lists.partition(songsInAlbum, Constants.PLAYLIST_ADD_LIMIT)) {
					JsonArray json = new JsonArray();
					for (TrackSimplified s : partition) {
						json.add(Constants.TRACK_PREFIX + s.getId());
					}
					try {
						SpotifyApiRequest.execute(SpotifyApiSessionManager.api().addTracksToPlaylist(playlistId, json).position(0).build());
						songsAdded += partition.size();
					} catch (InternalServerErrorException e) {
						Playlist p = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getPlaylist(playlistId).build());
						int playlistSize = p.getTracks().getTotal();
						if (playlistSize >= Constants.PLAYLIST_SIZE_LIMIT) {
							Config.log().severe(albumType.toString() + " playlist is full! Maximum capacity is " + Constants.PLAYLIST_SIZE_LIMIT + ".");
							break AddToPlaylistLoop;
						}
					}
				}
			}
			if (songsAdded > 0) {
				Config.log().info("> " + songsAdded + " new " + albumType.toString() + " song" + (songsAdded == 1 ? "" : "s") + " added!");
			}
		}
		timestampPlaylist(playlistId);
	}
	
	/**
	 * Timestamp the playlist's description (and, if set, the title) with the last time the crawling process was initiated.
	 * 
	 * @param playlistId
	 * @throws Exception 
	 */
	public static void timestampPlaylist(String playlistId) throws Exception {
		Calendar cal = Calendar.getInstance();
		
		Playlist p = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getPlaylist(playlistId).build());
		String playlistName = p.getName().replace(Constants.NEW_INDICATOR_TEXT, "").trim();
		PlaylistTrack[] playlistTracks = p.getTracks().getItems();
		if (playlistTracks.length > 0) {
			Date mostRecentAdditionDate = playlistTracks[0].getAddedAt();
			Calendar calOld = Calendar.getInstance();
			calOld.setTime(mostRecentAdditionDate);
			calOld.add(Calendar.HOUR_OF_DAY, Config.getInstance().getNewNotificationTimeout());
			if (cal.before(calOld)) {
				playlistName = String.format("%s %s", playlistName, Constants.NEW_INDICATOR_TEXT);
			}
		}
		
		String newDescription = String.format("Last Search: %s", Constants.DESCRIPTION_TIMESTAMP_FORMAT.format(cal.getTime()));
		
		SpotifyApiRequest.execute(SpotifyApiSessionManager.api().changePlaylistsDetails(playlistId).name(playlistName).description(newDescription).build());			
	}

}
