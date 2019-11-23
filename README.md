![Banner](https://i.imgur.com/PLKEDro.png)

# Spotify Discovery Bot
A Spring-based bot that automatically crawls for new releases by your followed artists. Never miss a Spotify release again!

Proudly utilizes the [Spotify Web API Java Wrapper](https://github.com/thelinmichael/spotify-web-api-java).

## Overview
Most new music gets released exactly at midnight between Thursday and Friday, but there are exceptions. It has been a top priority of this bot to fetch those at the exact moment they get released. As a result, the bot runs once every couple minutes to search for any new releases.

### Crawling
(Almost) everything visible in the Spotify client is also visible to the Spotify API. Therefore, this bot is really just a sophisticated "look at every artist I'm following and see if they got anything new on the table." But there are some subtleties one might not think about first.

For example, albums get re-uploaded _all_ the time. Sometimes, albums get uploaded _multiple_ times. And sometimes, albums get released to the _wrong artist_ altogether, usually because a super generic name got used by multiple bands – enjoy your low-quality hip hop on the main page of your favorite metal band.

All these tiny nitpicks quickly add up. The bot does its best effort to filter out any excessive garbage. This is especially true for Appears-On releases, as labels pump out roughly twenty billion compilations and samplers per minute.

### Playlists
Results get added into one or multiple playlists, separated by what is called an "Album Group". Playlists with new releases will get also receive a dope 🅽🅴🆆 notification marker that automatically disappears when you mark the playlist as read:

![Playlists](https://i.imgur.com/TG7keIF.png)

The playlists themselves are fully customizable, can be merged if you don't care about separation as much (as me), and the Album Groups can be individually turned off altogether – most people won't care about Appears-On releases as much.

## Usage
Unfortunately, being still in development, the bot is rather shabby in terms of production environment. Because it doesn't have any. You will have to do the configuration manually, for now. It will eventually be made available as a web-based application with a straightforward interface. Until then, the old-school approach of setting the key bits up yourself will have to do.

### Database (SQLite)
This bot requires an SQLite-based database to store both any cached data as well as the configuration for the bot itself. You'll find an almost-ready-to-rock template in `templates/database.db` where you need to set up the client ID and client secret for your ![Spotify Developer App](https://developer.spotify.com/dashboard). These must be entered in the `bot_config` table.

### Java
I personally have the bot compiled as a FatJAR and run it on my Raspberry Pi 24/7, and I recommend you also find some sort of permanent hosting solution. Start the compiled JAR from your console using:

```java -jar SpotifyDiscoveryBot.jar /path/to/database.db```

If the database path is omitted, the working directory will be used.

### First run
During your first run, you will be asked to log in to Spotify to receive your access and refresh token. This is a one-time process. The tokens will be stored in the database and automatically updated with each crawl.

## Options
There are a couple of customizable features available:
* *Cache Followed Artists*: It's highly unlikely to have an artist you've literally just started to follow immediately release new music the next minute. To save on bandwidth, followed artists are cached every 24 hours.
* *Intelligent Appears-On Search*: Basically, Appears-On search but it's actually usable. It throws away anything you probably don't care about – samplers, compilations, or if the followed artist is featured on an artist's album you don't follow only that song will remain.
* *Circular Playlist Fitting*: Spotify playlists are limited to 10000 songs. You can either set it to stop any further additions after reaching its limit or use this option to rotate the playlist in a circular fashion (e.g. new goes in, old goes out to make room).
* *EP Separation*: Unfortunately, Spotify doesn't differentiate between Singles and EPs, despite the two being harshly different. The bot will try its best to detect those and put them into their own playlist. This is either done by simply looking at the title ("Some Name EP"), if the song count is at least 4, or if the duration exceeds 20 minutes.
* *Live Separation*: Live albums suck – change my mind. Following the footsteps of the EP Separation, the bot will look for any release with the word "Live" in it, as well as at least half its songs with the same word, and extract these into a separate playlist.

## Final Notes
This project has widely spiraled out of control, given that it was originally meant to be a hastly thrown-together script to replace the (for my tastes) feature-lacking ![Spotishine](https://www.spotishine.com). Anything is subject to change at this point, as it's difficult to walk the line between hobbyist side project and sorta-kinda-serious project.
