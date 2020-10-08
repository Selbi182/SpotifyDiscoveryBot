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

The playlists themselves are fully customizable, can be merged if you don't care about separation as much (as me), and the Album Groups can be individually turned off altogether – e.g. most people won't care about Appears-On releases as much.

## Usage

The bot is rather simple in terms of production environment... as it doesn't have any. You will have to do the configuration manually, for now. It will eventually be made available as a web-based application with a straightforward interface. Until then, the old-school approach of setting things up yourself will have to do.

### Database (SQLite)

This bot requires an SQLite-based database to store both any cached data as well as the configuration for the bot itself. You'll find an almost-ready template in `templates/database.db` where you need to set up the client ID and client secret for your ![Spotify Developer App](https://developer.spotify.com/dashboard). These must be entered in the `bot_config` table.

### Java

I personally have the bot compiled as a Fat JAR and run it on my Raspberry Pi 24/7, and I recommend you also find some sort of permanent hosting solution. Start the compiled JAR from your console using:

```java -jar SpotifyDiscoveryBot.jar /path/to/database.db```

If the database path is omitted, the working directory will be used. If no database is found or it doesn't have the required tables/configuration, the application will immediately halt. In a future release, there will be an automated setup process.

### First Run

During your first run, you will be asked to log in to Spotify to receive your access and refresh token. This is a one-time process. The tokens will be stored in the database and automatically refreshed with each crawl.

In very rare cases you might still be required to re-login (usually due to an internal server error on Spotify's end).

## Options

There are a couple of customizable features available:

* *Release Separation*: There are only four official release types on Spotify: albums, singles, compilations, and what they call "appears on" (usually as featured artist or as a third-party compilation appearance). However, there's a whole lot more to uncover and sometimes it's nice to know ahead of time what to get excited about. So the bot will also automatically detect and recategorize certain releases into their own playlists:
** *EPs*: Normally classified as single, they will get identified as EPs if they either literally contain the word "EP" in the release title or exceed a certain duration. ([What exactly classifies as an EP anyway?](https://support.landr.com/hc/en-us/articles/115009568227-What-s-the-difference-between-a-single-an-EP-and-an-album-))
** *Live*: Live releases are simply released as albums or singles, respectively. Using [Spotify Audio Features](https://developer.spotify.com/documentation/web-api/reference/tracks/get-audio-features/), the bot will read the _liveness_ value of every song on a release and determines whether the release counts as live or not – at least half of the songs on the release need to be qualified as live.
** *Remixes*: Same drill as the previous options, this option searches for releases that are either named "Remix" or have a certain number of songs with the word "Remix" in them and puts the result into a separate playlist.
** *Re-Releases*: New music gets added all the time, even if it isn't exactly "new". It's easy to dismiss anything that wasn't released before today, but there might be good reasons why you want to know about certain re-releases anyway. What about the Super Deluxe Remastered 2020 version of an old classic? Or what about an album that's been previously missing on Spotify but has finally, silently been added (looking at you, Fear of a Blank Planet)?
* *Intelligent Appears-On Search*: Basically, Appears-On search but it's actually usable. It throws away anything you probably don't care about – namely samplers and compilations. As a bonus, should an artist you follow be featured on a release by another artist you _don't_ follow, only the featured songs will get added because you probably don't care about the rest.
* *Circular Playlist-Fitting*: Spotify playlists are limited to 10000 songs. While plenty for most people not to care, eventually it may run out of space. You can either set it to stop any further additions after reaching its limit or use this option to rotate the playlist in a circular fashion (e.g. new goes in, old goes out to make room).

## Final Notes

This project started as a simple script to replace the – in my opinion – feature-lacking ![Spotishine](https://www.spotishine.com). It has since evolved into a passion project with lots of tiny features to make discovering new music on Spotify more convenient.

Anything is subject to change at this point, as it's difficult to walk the line between hobbyist side project and sorta-kinda-serious project. However, as of writing this (October 2020) I consider it pretty much stable.
