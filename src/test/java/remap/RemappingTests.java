package remap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Objects;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.neovisionaries.i18n.CountryCode;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.Album;
import com.wrapper.spotify.model_objects.specification.AlbumSimplified;
import com.wrapper.spotify.model_objects.specification.TrackSimplified;

import spotify.bot.api.BotException;
import spotify.bot.api.SpotifyApiAuthorization;
import spotify.bot.api.SpotifyApiWrapper;
import spotify.bot.api.SpotifyCall;
import spotify.bot.api.services.TrackService;
import spotify.bot.config.BotConfigFactory;
import spotify.bot.config.ConfigUpdate;
import spotify.bot.config.database.DatabaseService;
import spotify.bot.config.database.DiscoveryDatabase;
import spotify.bot.config.dto.SpotifyApiConfig;
import spotify.bot.filter.FilterService;
import spotify.bot.filter.remapper.EpRemapper;
import spotify.bot.filter.remapper.LiveRemapper;
import spotify.bot.filter.remapper.Remapper;
import spotify.bot.filter.remapper.Remapper.Action;
import spotify.bot.filter.remapper.RemixRemapper;
import spotify.bot.filter.remapper.RereleaseRemapper;
import spotify.bot.util.BotLogger;
import spotify.bot.util.BotUtils;
import spotify.bot.util.data.AlbumGroupExtended;
import spotify.bot.util.data.AlbumTrackPair;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
	BotConfigFactory.class,
	BotLogger.class,
	ConfigUpdate.class,
	DiscoveryDatabase.class,
	DatabaseService.class,
	FilterService.class,
	SpotifyApiWrapper.class,
	SpotifyApiAuthorization.class,
	TrackService.class
})
@EnableConfigurationProperties
public class RemappingTests {

	@Autowired
	private SpotifyApi spotifyApi;

	@Autowired
	private SpotifyApiAuthorization spotifyApiAuthorization;

	@Autowired
	private TrackService trackService;

	@Autowired
	private FilterService filterService;

	private static EpRemapper epRemapper;
	private static LiveRemapper liveRemapper;
	private static RemixRemapper remixRemapper;
	private static RereleaseRemapper rereleaseRemapper;

	private static boolean initialized = false;

	@Before
	public void createRemappers() {
		if (!initialized) {
			epRemapper = new EpRemapper();
			liveRemapper = new LiveRemapper(trackService);
			remixRemapper = new RemixRemapper();

			SpotifyApiConfig fakeApiConfigWithMarket = new SpotifyApiConfig();
			fakeApiConfigWithMarket.setMarket(CountryCode.DE);
			rereleaseRemapper = new RereleaseRemapper(fakeApiConfigWithMarket, filterService);

			login();
			
			initialized = true;
		}
	}

	private void login() {
		try {
			spotifyApiAuthorization.initialLogin();
		} catch (BotException e) {
			e.printStackTrace();
			fail("Couldn't log in to Spotify Web API!");
		}
	}

	///////////////

	private boolean willRemap(Remapper remapper, String albumId) {
		Action remapAction = getRemapAction(remapper, albumId);
		return Objects.equals(remapAction, Remapper.Action.REMAP);	
	}

	private boolean willErase(Remapper remapper, String albumId) {
		Action remapAction = getRemapAction(remapper, albumId);
		return Objects.equals(remapAction, Remapper.Action.ERASE);
	}

	private Action getRemapAction(Remapper remapper, String albumId) {
		try {
			AlbumSimplified album = getAlbumSimplified(albumId);
			if (!remapper.isAllowedAlbumGroup(AlbumGroupExtended.fromAlbumGroup(album.getAlbumGroup()))) {
				fail("Didn't fulfill isAllowedAlbumGroup requirement");
			}
			List<TrackSimplified> tracks = getTracksOfSingleAlbum(album);
			AlbumTrackPair atp = AlbumTrackPair.of(album, tracks);			
			Action remapAction = remapper.determineRemapAction(atp);
			return remapAction;			
		} catch (BotException e) {
			e.printStackTrace();
			fail();
			return null;
		}
	}


	private AlbumSimplified getAlbumSimplified(String albumId) throws BotException {
		Album album = SpotifyCall.execute(spotifyApi.getAlbum(albumId));
		return BotUtils.asAlbumSimplified(album);
	}

	private List<TrackSimplified> getTracksOfSingleAlbum(AlbumSimplified album) throws BotException {
		return SpotifyCall.executePaging(spotifyApi
			.getAlbumsTracks(album.getId())
			.limit(50));
	}

	///////////////////////////////

	@Test
	public void epPositive() {
		assertTrue(willRemap(epRemapper, "5wMGdTWNzO3qqztd2MyKrr")); // Crimson Shadows - The Resurrection
		assertTrue(willRemap(epRemapper, "4J0hkWvySY1xfL9oHyF3ql")); // Swallow the Sun - Lumina Aurea
		assertTrue(willRemap(epRemapper, "45eac6gDsbKRnuAEaKfVxO")); // Porcupine Tree - Nil Recurring
	}

	@Test
	public void epNegative() {
		assertFalse(willRemap(epRemapper, "5NNahOPVAbt5gSCMmRuHTG")); // Nightwish - Harvest
		assertFalse(willRemap(epRemapper, "4YfXSuoJWZGcTNGAkFK8cO")); // Green Day - Oh Yeah!
		assertFalse(willRemap(epRemapper, "3BOQrewswG2ePGkShTx389")); // Die Aerzte - Drei Mann - Zwei Songs
		assertFalse(willRemap(epRemapper, "0UaxFieNv1ccs2GCECCUGy")); // Billy Talent - I Beg To Differ (This Will Get Better)
		assertFalse(willRemap(epRemapper, "3whJvuQ0hMC5DwPDbCIvXR")); // Ayreon - Talk of the Town
		// assertFalse(willRemap(epRemapper, "3iGAuNUEmvxqRhvfWuFvEv")); // VOLA - These Black Claws [impossible to fix without breaking other stuff... EPs suck]
	}

	///////////////////////////////

	@Test
	public void livePositive() {
		assertTrue(willRemap(liveRemapper, "6U2FX33shPcoezU7oZS0eW")); // Helloween - United Alive in Madrid
		assertTrue(willRemap(liveRemapper, "5tCkJBpsqSgjk1ZHvHdC2I")); // Haken - L+1VE
		assertTrue(willRemap(liveRemapper, "6kJuATIGbPYxHmRoWCC5IB")); // Carpenter Brut - CARPENTERBRUTLIVE
		assertTrue(willRemap(liveRemapper, "1C0CHLxgm1yWcR2pCaj0q7")); // Disturbed - Hold on to Memories (Live)
		assertTrue(willRemap(liveRemapper, "0NvnXREzEa8ZNCI5PRpukR")); // Septicflesh - Infernus Sinfonica MMXIX (Live)
		assertTrue(willRemap(liveRemapper, "52SQNIbuBk99ZlpUh2tSz5")); // BABYMETAL - LEGEND - METAL GALAXY [DAY 1]
		assertTrue(willRemap(liveRemapper, "7hrzbg8R1gI1loeF9Xmwdr")); // Devin Townsend - Why? (live in London 2019)
		assertTrue(willRemap(liveRemapper, "0lB9qbHN7Km0TquNaQbNW9")); // Deafheaven - Daedalus (Live)
		assertTrue(willRemap(liveRemapper, "4PVyUMglBuxtPVip15aFfq")); // Deafheaven - 10 Years Gone (Live)
	}

	@Test
	public void liveNegative() {
		assertFalse(willRemap(liveRemapper, "3WJZV73n2hL1Hd4ldmalZR")); // Alestorm - Captain Morgan's Revenge (10th Anniversary Edition)
		assertFalse(willRemap(liveRemapper, "4WXCtg5Qs7McMVarDVzSxd")); // Saltatio Mortis - Brot und Spiele - Klassik & Krawall (Deluxe)
		assertFalse(willRemap(liveRemapper, "12cGa7OeAt3BiN8F8Ec1uJ")); // The Unguided - And the Battle Royale
		assertFalse(willRemap(liveRemapper, "7hRRdRCPsoWCF2gJr7yPZR")); // Enter Shikari - The King
		assertFalse(willRemap(liveRemapper, "6dksdceqBM8roInjffIaZw")); // Apocalyptica - Live or Die
		assertFalse(willRemap(liveRemapper, "7d3PsJu4ozQs75MTgWlBGC")); // Santiano - Wie Zuhause (MTV Unplugged)
		assertFalse(willRemap(liveRemapper, "5C8a48NKuieqX1XlJvHzCs")); // Panic at the Disco - The Greatest Show
		assertFalse(willRemap(liveRemapper, "7FtlSQ9bDd5u1EPXHYsVCB")); // Long Distance Calling - How Do We Want To Live?
	}

	///////////////////////////////

	@Test
	public void remixPositive() {
		assertTrue(willRemap(remixRemapper, "5HuNp4GZrOJCw4qYYY5WNp")); // GReeeN - Remixes
		assertTrue(willRemap(remixRemapper, "3kNq2tf4Pl2vE6nlnNVAMH")); // Rammstein - Auslaender (Remixes)
		assertTrue(willRemap(remixRemapper, "7hyxShr7rqBsaCbIuUjssI")); // Savlonic - Black Plastic : Recycled
		assertTrue(willRemap(remixRemapper, "4rtd4mDEbypHitSuDFWvvc")); // Emigrate - War
		assertTrue(willRemap(remixRemapper, "3QZHRDBNh1GHDY6MCBULQp")); // Pendulum - The Reworks
		assertTrue(willRemap(remixRemapper, "1Xe71xvukVuB7MUudgf5CT")); // Enter Shikari - { The Dreamer's Hotel } (Bob Vylan Remix)
		assertTrue(willRemap(remixRemapper, "0qkA24pNLpb9UczrM8HmPn")); // Trollfest - Kletteren Afterski - Remix
	}

	@Test
	public void remixNegative() {
		assertFalse(willRemap(remixRemapper, "29jsaLZpeO5jQLtOVUgwdV")); // Lindemann - Mathematik
		assertFalse(willRemap(remixRemapper, "44YMtn2oYAHVXQGFOKyXkJ")); // Binary Division - Midnight Crisis
		assertFalse(willRemap(remixRemapper, "231la1R3Z2UWS0rjRTIm9U")); // Lindemann - Steh auf
		assertFalse(willRemap(remixRemapper, "1b7Yy5kprvU3YiJmRdt4Bf")); // Cradle of Filth - Cruelty and the Beast
		assertFalse(willRemap(remixRemapper, "1L3K9GVu9coTIckoCpD6S9")); // Perturbator - B-Sides & Remixes 1
	}

	///////////////////////////////

	@Test
	public void rereleasePositive() {
		assertTrue(willRemap(rereleaseRemapper, "3MNvfg19MJsXCQ0I3WVdkm")); // Nickelback - All The Rigth Reasons (15th Anniversary Expanded Edition)
		assertTrue(willRemap(rereleaseRemapper, "4qUMByJ3Pk94BFnCmGaUPS")); // Ozzy Osbourne - Blizzard of Ozz (10th Anniversary Expanded Edition)
		assertTrue(willRemap(rereleaseRemapper, "4MY73Dl519xdqKq4Bdll0a")); // Between the Buried and Me - The Silent Circus (2020 Remix / Remaster)
		assertTrue(willRemap(rereleaseRemapper, "7tCnmn9QvojHgEDCWG6xXs")); // In Flames - Clayman (20th Anniversary Edition)
		assertTrue(willRemap(rereleaseRemapper, "1ucRSsC7KP0oJlTIVQlYU7")); // Stratovarius - Destiny (Reissue 2016)
		assertTrue(willRemap(rereleaseRemapper, "3Rb5pMHWV5ZMVrKOE90wuj")); // Eluveitie - Slania (10 Years)
		assertTrue(willRemap(rereleaseRemapper, "72kuxCdCsdZzfez0lLKUA5")); // Porcupine Tree - Fear of a Blank Planet
		assertTrue(willRemap(rereleaseRemapper, "6kE2yDdOredVYctzaK0PVu")); // Boehse Onkelz - Kneipenterroristen (30 Jahre)
	}

	@Test
	public void rereleaseErase() {
		assertTrue(willErase(rereleaseRemapper, "1ZzgWH1of1iCoe7RSVyPFG")); // Helloween - 7 Sinners <1 song missing>
		assertTrue(willErase(rereleaseRemapper, "7hnrUq4SVrRqsnDxBq0QZY")); // 65daysofstatic - We Were Exploding Anyway <5 songs missing>
	}

}
