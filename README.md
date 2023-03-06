# SpotifyDiscoveryBot
![Banner](https://i.imgur.com/5LhM1TP.png)

A Java-based bot that automatically crawls for new releases by your followed artists and puts them into playlists separated by release type. Never miss a Spotify release again and uncover hidden gems!

Proudly utilizes the [Spotify Web API Java Wrapper](https://github.com/thelinmichael/spotify-web-api-java).

## How it works
(Almost) everything visible in the Spotify client is also visible to the Spotify API. Therefore, this bot is really just a sophisticated and automated "look at every artist I'm following and see if they got anything new on the table." However, there are some subtleties one might not think about first.

For example, albums get re-uploaded _all_ the time, often without any sensible explanation. Sometimes albums get uploaded _multiple_ times at once. And sometimes albums even get released to the _wrong artist_ altogether, usually because a rather common name got used by multiple bands – enjoy your low-quality hip hop on the main page of your favorite rock band.

All these tiny nitpicks quickly add up and would render the approach of simply crawling through all artists and adding anything new as rather bloated. This is especially true for Appears-On releases, as labels pump out samplers on a daily basis.

This bot does its best effort to filter out anything you don't care about, to only leave you with the music you actually like!

### Playlists
Results get added to one or multiple playlists, separated by what is called an "Album Group". Playlists with new releases will get also receive a dope notification marker (lights up!):

![Playlists](https://i.imgur.com/oTacgq6.png)

This marker automatically disappears when you play any of the recently added songs to the playlist for about 10 seconds.

The playlists themselves are fully customizable and can be merged if you don't care about separation as much (see instructions below).

## Installation

### Step 1: Spotify Developer App
You will require a [Spotify Developer App](https://developer.spotify.com/dashboard) for this bot to work, so create one.

First, go to "Users and Access" and add your account there (name and email address).

Then click on the green "Edit Settings" button and enter the following URL as login callback:
```
http://localhost:8182/login-callback
```
Finally open the file `config/spotifybot.properties` and enter the *Client ID* and *Client Secret* that was given to you when you created the Spotify Developer App:
```
client_id = jwi7hg...
client_secret = hdow93...
```

### Step 2: Target Playlists
By default, the bot will create the eight playlists for the different album groups on its own when you first launch it. If you're okay with that, you may skip this step.

Otherwise, if you wish to set custom targets for the discovery results, open `config/playlist.properties`, where you can enter the target playlists manually. All eight album groups are required to be in the properties file; if an entry is missing, the bot will automatically create the missing playlist on startup and set the property entry on its own.

You can also disable certain groups altogether. To do this, simply keep the part after the `=` blank for the respective group you want to disable.

#### Example
```
album = https://open.spotify.com/playlist/0DOQiI2FP82IXy9Z0nHdTz?si=848836e7b4754227
single = https://open.spotify.com/playlist/19FdF4ZUwv8JdYx2YB5YBZ?si=007269cdc7714564
ep = https://open.spotify.com/playlist/19FdF4ZUwv8JdYx2YB5YBZ?si=007269cdc7714564
remix = https://open.spotify.com/playlist/5LA0xQetL5h7RXKjwBol03?si=bd1a2c67ab2e4937
live = https://open.spotify.com/playlist/5LA0xQetL5h7RXKjwBol03?si=bd1a2c67ab2e4937
compilation = 
re_release = 
appears_on = https://open.spotify.com/playlist/5LA0xQetL5h7RXKjwBol03?si=bd1a2c67ab2e4937
```
Explanation:
* Albums are put into their own playlist
* Singles and EPs are grouped together in a combined playlist
* Remixes, live releases, and appears-on tracks are grouped together in another combined playlist
* Compilations and re-releases will be ignored entirely

**Note:** After the first launch, the bot will recreate this file with only the playlist IDs (as in, only the ID after the last / without the *?si* part). This is purely for performance reasons, so that future crawls don't need to parse the entire URLs over and over again.

### Step 3: Starting the bot for the first time
To start the bot, make sure you have at least Java 11 installed and run the fatJar:
```
java -jar SpotifyDiscoveryBot.jar
```
Once booted up, the bot will try (and fail) to log into the Spotify Web App, as there is no access token yet. You will be presented with a URL that looks something like this:
```
https://accounts.spotify.com:443/authorize?client_id=[...]&response_type=code&redirect_uri=[...]&scope=[...]
```
Open it with your preferred browser (if it didn't automatically do so) and follow the login steps.

Once you're logged in, you're set to go! For the first launch, the bot will index every single artist you're following, which might take a couple of minutes, depending on how many artists you're following. After that, just keep the bot running in the background or start it when you feel like it.

Relax, lean back, and watch as the bot crawls for new releases!

## Options
In the `config` folder, you will be able to further customize a few special settings by creating files with a specific format. If you don't need any of these, simply ignore this part.

### `blacklist.properties`
Use this file to ban certain artists from certain release types. For example, you like a specific artist, but dislike how often they appear as a featured artist.

**Usage:**
* `Spotify Artist ID = Release Group (separated by commas without spaces)`
* Allowed Release Groups: `ALBUM`, `APPEARS_ON`, `COMPILATION`, `SINGLE`, `EP`, `REMIX`, `LIVE`, `RE_RELEASE`

**Example:**
```
7dGJo4pcD2V6oG8kP0tJRR = APPEARS_ON,RE_RELEASE
7rSMEcqv4Ez0OLgJKDjrvq = RE_RELEASE
```

### `relay.properties`
Use this file to automatically forward specific artist to a given URL. You can use this to, for example, post new releases to a webhook that is connected to a Discord bot. You must additionally have the artists followed on Spotify for this to work.

**Usage:**
* `relay_url`: The target URL
* `whitelisted_artist_ids`: The artist IDs you are allowing to be forwarded to the target URL, separated by commas without spaces
* `message_mask`: The message that is sent to the target URL, where the first %s is the artist name and the second %s is a placeholder for the respective album IDs

**Example:**
```
relay_url = https://someprivatebot.com/forwarddiscovery
whitelisted_artist_ids = 09Z51O0q4AwHl7FjUUlFKw,0cbL6CYnRqpAxf1evwUVQD,1Gh3UMZ0WVesXifHfziSx9
message_mask = {"message":"New release from <b>%s</b>: https://open.spotify.com/album/%s"}
```

## Log
You can get detailed information about what the bot did at any time by directly accessing the log in your preferred browser (by default `http://localhost:8182/`):
![Log](https://i.imgur.com/yH4cvdf.png)
By default, the last 10 log entries are displayed. You can set the optional query parameter `?limit=n` where *n* is the number of entries you want to have displayed. Set it to *-1* to display every log entry ever made.

## Circular Playlist-Fitting
Spotify's playlists are limited to 10,000 songs. While plenty for most people to not care, eventually it may run out of space.

The bot will automatically rotate the playlists in a circular fashion when the limit is reached, e.g. new goes in, old goes out to make room. If you never want to lose any additions, make sure to create a copy of your playlist once you're about to reach 10,000 songs.

## Final Notes
This project started as a simple script to replace the – in my opinion – feature-lacking [Spotishine](https://www.spotishine.com). It has since evolved into a passion project with lots of tiny features to make discovering new music on Spotify more convenient.

If you got any problems or feature suggestions, [write an issue ticket on GitHub](https://github.com/Selbi182/SpotifyDiscoveryBot/issues) and I will gladly take a look at it! :)

Alternatively, message me on Discord: **Selbi#7270**
