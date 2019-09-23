package spotify.bot.api.requests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wrapper.spotify.enums.AlbumType;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;
import com.wrapper.spotify.requests.data.playlists.GetPlaylistsTracksRequest;

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
	
	/**
	 * Add the given list of song IDs to the playlist (a delay of a second per release is used to retain order). May remove older songs to make room.
	 * 
	 * @param albumType
	 * @param songs
	 * @throws Exception 
	 */
	public static void addSongsToPlaylist(List<List<TrackSimplified>> newSongs, AlbumType albumType) throws Exception {
		String playlistId = BotUtils.getPlaylistIdByType(albumType);
		if (!newSongs.isEmpty()) {
			Playlist p = SpotifyApiRequest.execute(SpotifyApiSessionManager.api().getPlaylist(playlistId).build());
			final int currentPlaylistCount = p.getTracks().getTotal();
			final int songsToAddCount = (int) newSongs.stream().mapToInt(List::size).sum();
			if (currentPlaylistCount + songsToAddCount > Constants.PLAYLIST_SIZE_LIMIT) {
				if (!Config.getInstance().isCircularPlaylistFitting()) {
					Config.log().severe(p.getName() + " is full! Maximum capacity is " + Constants.PLAYLIST_SIZE_LIMIT + ". Enable circularPlaylistFitting or flush the playlist for new songs.");
					return;
				}
				deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount, songsToAddCount);
			}
			
			int songsAdded = 0;
			for (List<TrackSimplified> songsInAlbum : newSongs) {
				for (List<TrackSimplified> partition : Lists.partition(songsInAlbum, Constants.PLAYLIST_ADD_LIMIT)) {
					JsonArray json = new JsonArray();
					for (TrackSimplified s : partition) {
						json.add(Constants.TRACK_PREFIX + s.getId());
					}
					SpotifyApiRequest.execute(SpotifyApiSessionManager.api().addTracksToPlaylist(playlistId, json).position(0).build());
					songsAdded += partition.size();
				}
			}
			if (songsAdded > 0) {
				Config.log().info("> " + songsAdded + " new " + albumType.toString() + " song" + (songsAdded == 1 ? "" : "s") + " added!");
			}
		}
		timestampPlaylist(playlistId);
	}
	
	/**
	 * Delete as many songs from the bottom as necessary to make room for any new songs to add, as Spotify playlists have a fixed limit of 10000 songs.
	 * 
	 * If circularPlaylistFitting isn't enabled, an exception is thrown on a full playlist instead.
	 * 
	 * @param playlistId
	 * @param currentPlaylistCount
	 * @param songsToAddCount
	 * @throws Exception 
	 * @throws IOException 
	 */
	private static void deleteSongsFromBottomOnLimit(String playlistId, int currentPlaylistCount, int songsToAddCount) throws IOException, Exception {
		int totalSongsToDeleteCount = currentPlaylistCount + songsToAddCount - Constants.PLAYLIST_SIZE_LIMIT;
		boolean repeat = totalSongsToDeleteCount > Constants.PLAYLIST_ADD_LIMIT;
		int songsToDeleteCount = repeat ? Constants.PLAYLIST_ADD_LIMIT : totalSongsToDeleteCount;
		final int offset = currentPlaylistCount - songsToDeleteCount;
		
		List<PlaylistTrack> tracksToDelete = SpotifyApiRequest.execute(new Callable<List<PlaylistTrack>>() {
			@Override
			public List<PlaylistTrack> call() throws Exception {
				List<PlaylistTrack> currentList = new ArrayList<>();
				Paging<PlaylistTrack> playlistTracks = null;
				do {
					GetPlaylistsTracksRequest.Builder request = SpotifyApiSessionManager.api().getPlaylistsTracks(playlistId).offset(offset).limit(Constants.PLAYLIST_ADD_LIMIT);
					if (playlistTracks != null && playlistTracks.getNext() != null) {
						request = request.offset(playlistTracks.getOffset() + Constants.PLAYLIST_ADD_LIMIT);
					}
					playlistTracks = request.build().execute();
					currentList.addAll(Arrays.asList(playlistTracks.getItems()));
				} while (playlistTracks.getNext() != null);
				return currentList;
			}
		});
		
		JsonArray json = new JsonArray();				
		for (int i = 0; i < tracksToDelete.size(); i++) {
			JsonObject object = new JsonObject();
			object.addProperty("uri", Constants.TRACK_PREFIX + tracksToDelete.get(i).getTrack().getId());
			JsonArray positions = new JsonArray();
			positions.add(currentPlaylistCount - songsToDeleteCount + i);
			object.add("positions", positions);
			json.add(object);
		}
		
		SpotifyApiRequest.execute(SpotifyApiSessionManager.api().removeTracksFromPlaylist(playlistId, json).build());	
		
		// Repeat if more than 100 songs have to be added/deleted (should rarely happen, so a recursion will be slow, but it'll do the job)
		if (repeat) {
			deleteSongsFromBottomOnLimit(playlistId, currentPlaylistCount - 100, songsToAddCount);
		}
	}
}
