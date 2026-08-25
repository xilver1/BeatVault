# BeatVault CLI Syntax & Examples

## Authentication

$ bvault login

  Username: dj_pepe
  Password: ******

  ✓ Welcome back, dj_pepe!

## Ingesting Tracks

**Single YouTube Video:**
$ bvault ingest youtube "windowlicker"

  Top results:
  1. Aphex Twin - Windowlicker (official video) 1080p HD
  2. Aphex Twin - WindowLicker
  3. Aphex Twin The making of Windowlicker
  4. Aphex Twin - Windowlicker (Director's Version)

  ✓ Aphex Twin - WindowLicker chosen ("https://youtube.com/watch?v=...")
  ⠋ downloading & analyzing...
  ✓ ingested successfully

**Batch YouTube Playlist (SSO):**
$ bvault ingest youtube --login
  Opening your browser to authenticate...
  ✓ SSO authorization successful! Credentials cached.

$ bvault ingest youtube --playlists
  Opening your browser to authenticate...
  ? Select a YouTube playlist:
  > Lofi Beats (Lofi Girl)
    My Favorites (dj_pepe)
  ✓ Selected playlist: Lofi Beats
  Fetching videos for playlist ID: PLxyz...
  Submitting 45 videos for ingestion...
  [00:00:15] [########################################] 45/45 (0 failed)
  ✓ Batch complete. 0 failed.

**Batch YouTube Playlist (Direct URL):**
$ bvault ingest youtube "https://youtube.com/playlist?list=PLxyz"
  Fetching videos for playlist ID: PLxyz...
  Submitting 45 videos for ingestion...
  [00:00:15] [########################################] 45/45 (0 failed)
  ✓ Batch complete. 0 failed.

**Local File:**
$ bvault ingest local "C:\Music\my_song.mp3"
  ⠋ Uploading local file... ✓
  ✓ ingested: my_song

**Google Drive Folder:**
$ bvault ingest gdrive "1A2B3C4D5E6F7G8H9I0J"
  Opening your browser to authenticate...
  ✓ Google Drive folder import started! Tracks appear as they process.

## Library & Playlists

**List Library:**

$ bvault library
  ┌────────────────────────┬───────┬─────┬────────┐
  │ Track                  │ BPM   │ Key │ Length │
  ├────────────────────────┼───────┼─────┼────────┤
  │ Windowlicker           │ 128.0 │ Am  │ 6:23   │
  │ Xtal                   │ 100.5 │ Dm  │ 4:51   │
  └────────────────────────┴───────┴─────┴────────┘

**List Playlists:**
$ bvault playlist list
  ┌────────┬──────────────────┬─────────────┐
  │ Name   │ Created At       │ Description │
  ├────────┼──────────────────┼─────────────┤
  │ warmup │ 2026-08-17 00:00 │             │
  └────────┴──────────────────┴─────────────┘

**Add Tracks to Playlist:**
$ bvault playlist add "warmup" "windowlicker, xtal"
  ✓ Added (100%): Aphex Twin - Windowlicker
  ✓ Added (100%): Aphex Twin - Xtal
  ✓ Playlist 'warmup' successfully updated.

**View Playlist Tracks:**
$ bvault playlist view "warmup"
  ? Multiple playlists found with the name 'warmup':
  > Created: 2026-08-17 00:00 | Description: No description
    Created: 2026-08-15 14:30 | Description: Old warmup tracks

  Playlist: warmup
  ┌─────────────────────────┬─────────────────────────┐
  │ Artist                  │ Title                   │
  ├─────────────────────────┼─────────────────────────┤
  │ Aphex Twin              │ Windowlicker            │
  │ Aphex Twin              │ Xtal                    │
  └─────────────────────────┴─────────────────────────┘

**Remove Tracks from Playlist:**
$ bvault playlist remove "warmup" "xtal"

**Delete Playlist:**
$ bvault playlist delete "warmup"

## Exporting to CDJs

**Export to USB:**

$ bvault export warmup --usb

    Choose your USB device:
      1. [D:] KINGSTON 32 GB

  ✓ KINGSTON 32 GB Chosen
  ⠋ building rekordbox layout (PDB + ANLZ)... ✓
  ✓ rekordbox USB written — 2 tracks, plug into any CDJ

**Export to Local Folder:**
$ bvault export warmup --path "C:\Users\dj\Desktop\Export"
  ⠋ building rekordbox layout (PDB + ANLZ)... ✓
  ✓ rekordbox exported to C:\Users\dj\Desktop\Export