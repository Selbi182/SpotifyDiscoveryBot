![Banner](https://i.imgur.com/MkS2cLj.png)

# Spotify Discovery Bot

A Spring Boot–based bot that automatically crawls for new releases by your followed artists and puts them into playlists separated by release type. Never miss a Spotify release again and uncover hidden gems!

Proudly utilizes the [Spotify Web API Java Wrapper](https://github.com/thelinmichael/spotify-web-api-java).

## Overview

Most new music gets released every week exactly at midnight between Thursday and Friday, but there are exceptions. It has been a top priority of this bot to fetch those at the exact moment they get released. The bot runs once every half hour to search for any new releases.

### Crawling

(Almost) everything visible in the Spotify client is also visible to the Spotify API. Therefore, this bot is really just a sophisticated and automated "look at every artist I'm following and see if they got anything new on the table." However, there are some subtleties one might not think about first.

For example, albums get re-uploaded _all_ the time, often without any sensible explanation. Sometimes albums get uploaded _multiple_ times at once. And sometimes albums even get released to the _wrong artist_ altogether, usually because a rather common name got used by multiple bands – enjoy your low-quality hip hop on the main page of your favorite rock band.

All these tiny nitpicks quickly add up and would render the approach of simply crawling through all artists and adding anything new as rather useless. This is especially true for Appears-On releases, as labels pump out low-quality samplers daily.

This bot does its best effort to filter out any excessive garbage to only leave you with the music you actually care about!

### Playlists

Results get added into one or multiple playlists, separated by what is called an "Album Group". Playlists with new releases will get also receive a dope notification marker (lights up!):

![Playlists](https://i.imgur.com/6ceKj71.png)

This marker automatically disappears when you play any of the recently added songs to the playlist.

The playlists themselves are fully customizable and can be merged if you don't care about separation as much (as me).

## Installation

### Step 1: Spotify Developer App
You will require a [Spotify Developer App](https://developer.spotify.com/dashboard) for this bot to work, so create one. As login callback use:
```
http://localhost:8182/login-callback
```
Then create or open the file `spotifybot.properties` and enter the *Client ID* and *Client Secret* that was given to you when you created the Spotify Developer App:
```
client_id=jwi7hg[...]
client_secret=hdow93[...]
```

### Step 2: Setting the target playlists
(This is an optional step; if you're okay with the bot creating the playlists automatically, make sure the file `config/playlist.properties` is **deleted**!)
To set custom targets for the discovery results, open `config/playlist.properties`, where you can enter the target playlist IDs like so:
```
album=0DOQiI2FP82IXy9Z0nHdTz
single=19FdF4ZUwv8JdYx2YB5YBZ
ep=6aIB410c4X1VdM6zujXkR0
remix=5LA0xQetL5h7RXKjwBol03
live=2iX6i2cr5JpHlLrnD8nPPd
compilation=6s18DTSGfkRA7hZR1sTzxL
re_release=0jddpHmZZtfAdxzauw1aCF
appears_on=1wMWWCP4XKK9jfVWvUoz0y
```

### Step 3: Starting the bot for the first time
To start the bot, make sure you have at least Java 11 installed and run the fatJar:
```
java -jar SpotifyDiscoveryBot.jar
```
Once booted up, the bot will try (and fail) to log into the Spotify Web App, as there is no access token yet. After a short timeout, you will be presented with a link that looks something like this:
```
https://accounts.spotify.com:443/authorize?client_id=[...]&response_type=code&redirect_uri=[...]&scope=[...]
```
Open it with your preferred browser (if it didn't automatically do so) and follow the login steps to Spotify using your private account.

Once you're logged in, you're set to go! For the first launch, the bot will index every single artist you're following, which might take a couple of minutes. After that, just keep the bot running in the background or start it when you feel like it to relax, lean back, and watch as the bot crawls for new releases!

## Options

In the `config` folder, you will also find a few customization options in different files:

### `blacklist.properties`
Use this file to ban certain artists from certain release types. For example, you like a specific artist, but dislike how often they appear as featured artist.

**Usage:**
* `<Spotify Artist ID>=<Release Group (separated by comma without spaces)>`
* Allowed Release Groups: `ALBUM`, `APPEARS_ON`, `COMPILATION`, `SINGLE`, `EP`, `REMIX`, `LIVE`, `RE_RELEASE`

**Example:**
```
7dGJo4pcD2V6oG8kP0tJRR=APPEARS_ON,RE_RELEASE
7rSMEcqv4Ez0OLgJKDjrvq=RE_RELEASE
```
If you don't need this feature, just delete this file.

### `relay.properties`
Use this file to automatically forward specific artist to a given URL. You can use this to, for example, post new releases to a webhook that is connected to a Discord bot.

**Usage:**
* `RELAY_URL`: The target URL
* `WHITELISTED_ARTIST_IDS`: The artist IDs you are allowing to be forwarded to the target URL, separated by comma without spaces
* `MESSAGE_MASK`: The message that is sent to the target URL, where the first %s is the artist name and the second %s is a placeholder for the respective album IDs

**Example:**
```
RELAY_URL=https://someprivatebot.com/forwarddiscovery
WHITELISTED_ARTIST_IDS=09Z51O0q4AwHl7FjUUlFKw,0cbL6CYnRqpAxf1evwUVQD,1Gh3UMZ0WVesXifHfziSx9
MESSAGE_MASK={"message":"New release from <b>%s</b>: https://open.spotify.com/album/%s"}
```
If you don't need this feature, just delete this file.

## Log
You can get detailed information about what the bot did at any time by directly accessing the bot in your preferred browser (by default `http://localhost:8182/`):
![Log](https://i.imgur.com/yH4cvdf.png)
By default, the last 10 log entries are displayed. You can set the optional query parameter `?limit=n` where *n* is the number of entries you want to have displayed. Set it to *-1* to display every log entry ever made.

## Circular Playlist-Fitting
Spotify's playlists are limited to 10,000 songs. While plenty for most people not to care, eventually it may run out of space.

This bot will automatically rotate the playlists in a circular fashion, e.g. new goes in, old goes out to make room. If you never want to lose any additions, make sure to create a copy of your playlist once you're about to reach 10,000 songs.

## Final Notes

This project started as a simple script to replace the – in my opinion – feature-lacking [Spotishine](https://www.spotishine.com). It has since evolved into a passion project with lots of tiny features to make discovering new music on Spotify more convenient.

Anything is subject to change at this point, as it's difficult to walk the line between hobbyist side project and sorta-kinda-serious project. However, as of writing this (Januar 2023) I consider it pretty much stable.
