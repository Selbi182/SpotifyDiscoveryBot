package remap;

import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.SpotifyApiAuthorization;
import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.api.SpotifyCall;
import spotify.bot.api.services.TrackService;
import spotify.bot.config.Config;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.database.DiscoveryDatabase;
import spotify.bot.filter.remapper.EPRemapper;
import spotify.bot.filter.remapper.LiveRemapper;
import spotify.bot.filter.remapper.RemixRemapper;
import spotify.bot.util.BotLogger;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
	BotLogger.class,
	TrackService.class,
	DiscoveryDatabase.class,
	DatabaseService.class,
	Config.class,
	SpotifyApiWrapper.class,
	SpotifyApiAuthorization.class })
@EnableConfigurationProperties
class RemappingTests {

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private SpotifyApiAuthorization spotifyApiAuthorization;

	@Autowired
	private TrackService trackService;

	private static EPRemapper epRemapper;
	private static LiveRemapper liveRemapper;
	private static RemixRemapper remixRemapper;

	@BeforeAll
	static void createRemappers() throws SpotifyWebApiException, InterruptedException, IOException {
		epRemapper = new EPRemapper();
		liveRemapper = new LiveRemapper();
		remixRemapper = new RemixRemapper();
	}

	void login() {
		try {
			spotifyApiAuthorization.login();
		} catch (SpotifyWebApiException | InterruptedException | IOException e) {
			e.printStackTrace();
			fail();
		}
	}

	private Album getAlbum(String albumId) {
		try {
			return SpotifyCall.execute(spotifyApi.getAlbum(albumId));
		} catch (SpotifyWebApiException | IOException | InterruptedException e) {
			return null;
		}
	}

	private List<TrackSimplified> getTracksOfSingleAlbum(Album album) {
		try {
			return SpotifyCall.executePaging(spotifyApi.getAlbumsTracks(album.getId()).limit(50));
		} catch (SpotifyWebApiException | IOException | InterruptedException e) {
			return null;
		}
	}
	
	///////////////////////////////

	@Test
	void epPositive() {
		login();

		Album album;
		boolean qualifiesAsRemappable;

		album = getAlbum("5wMGdTWNzO3qqztd2MyKrr"); // Crimson Shadows - The Resurrection
		qualifiesAsRemappable = epRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("0Przlkc8VDMp8SDhC24Nvs"); // Gary Washington - Black Carpet EP
		qualifiesAsRemappable = epRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("4J0hkWvySY1xfL9oHyF3ql"); // Swallow the Sun - Lumina Aurea
		qualifiesAsRemappable = epRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertTrue(qualifiesAsRemappable);
	}

	@Test
	void epNegative() {
		login();

		Album album;
		boolean qualifiesAsRemappable;

		album = getAlbum("5NNahOPVAbt5gSCMmRuHTG"); // Nightwish - Harvest
		qualifiesAsRemappable = epRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("4YfXSuoJWZGcTNGAkFK8cO"); // Green Day - Oh Yeah!
		qualifiesAsRemappable = epRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("3BOQrewswG2ePGkShTx389"); // Die Aerzte - Drei Mann - Zwei Songs
		qualifiesAsRemappable = epRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertFalse(qualifiesAsRemappable);
	}

	///////////////////////////////

	@Test
	void livePositive() {
		login();

		Album album;
		boolean qualifiesAsRemappable;

		album = getAlbum("6U2FX33shPcoezU7oZS0eW"); // Helloween - United Alive in Madrid
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("5tCkJBpsqSgjk1ZHvHdC2I"); // Haken - L+1VE
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("6kJuATIGbPYxHmRoWCC5IB"); // Carpenter Brut - CARPENTERBRUTLIVE
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("1C0CHLxgm1yWcR2pCaj0q7"); // Disturbed - Hold on to Memories (Live)
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertTrue(qualifiesAsRemappable);
	}

	@Test
	void liveNegative() {
		login();

		Album album;
		boolean qualifiesAsRemappable;

		album = getAlbum("3WJZV73n2hL1Hd4ldmalZR"); // Alestorm - Captain Morgan's Revenge (10th Anniversary Edition)
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("4WXCtg5Qs7McMVarDVzSxd"); // Saltatio Mortis - Brot und Spiele - Klassik & Krawall (Deluxe)
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("12cGa7OeAt3BiN8F8Ec1uJ"); // The Unguided - And the Battle Royale
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("7hRRdRCPsoWCF2gJr7yPZR"); // Enter Shikari - The King
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("6dksdceqBM8roInjffIaZw"); // Apocalyptica - Live or Die
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("7d3PsJu4ozQs75MTgWlBGC"); // Santiano - Wie Zuhause (MTV Unplugged)
		qualifiesAsRemappable = liveRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album), trackService);
		assertFalse(qualifiesAsRemappable);
	}

	///////////////////////////////

	@Test
	void remixPositive() {
		login();

		Album album;
		boolean qualifiesAsRemappable;

		album = getAlbum("5HuNp4GZrOJCw4qYYY5WNp"); // GReeeN - Remixes
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("3kNq2tf4Pl2vE6nlnNVAMH"); // Rammstein - Auslaender (Remixes)
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("7hyxShr7rqBsaCbIuUjssI"); // Savlonic - Black Plastic : Recycled
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("4rtd4mDEbypHitSuDFWvvc"); // Emigrate - War
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertTrue(qualifiesAsRemappable);

		album = getAlbum("3QZHRDBNh1GHDY6MCBULQp"); // Pendulum - The Reworks
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertTrue(qualifiesAsRemappable);
	}

	@Test
	void remixNegative() {
		login();

		Album album;
		boolean qualifiesAsRemappable;

		album = getAlbum("29jsaLZpeO5jQLtOVUgwdV"); // Lindemann - Mathematik
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("44YMtn2oYAHVXQGFOKyXkJ"); // Binary Division - Midnight Crisis
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("231la1R3Z2UWS0rjRTIm9U"); // Lindemann - Steh auf
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("1b7Yy5kprvU3YiJmRdt4Bf"); // Cradle of Filth - Cruelty and the Beast
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertFalse(qualifiesAsRemappable);

		album = getAlbum("1L3K9GVu9coTIckoCpD6S9"); // Perturbator - B-Sides & Remixes 1
		qualifiesAsRemappable = remixRemapper.qualifiesAsRemappable(album.getName(), getTracksOfSingleAlbum(album));
		assertFalse(qualifiesAsRemappable);
	}
}
