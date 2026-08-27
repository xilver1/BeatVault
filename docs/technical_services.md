# BeatVault Services Technical Documentation

The `bvault-app/services/` directory contains the deployable applications that form the BeatVault backend. These services rely heavily on the library functions provided by the `crates/` to enforce business logic and communicate via PostgreSQL and a shared filesystem (the `music-store`).

## `gateway`
**Responsibility:** The primary HTTP API and public entry point for BeatVault.

**Key Technical Details:**
- Built with `axum` and `tokio`.
- Handles all user authentication flows, interacting with `bvault-auth` and `bvault-meta`.
- **Database Migrations:** The `gateway` is the authoritative owner of the database schema. It runs `sqlx` migrations on startup before binding the HTTP port.
- **Job Enqueueing:** Accepts requests to analyze tracks or ingest media, creating entries in the `bvault-jobs` queue for worker processes to consume.
- Exposes endpoints to create playlists, list tracks, and initiate USB exports (which are forwarded via a job or dedicated service).

## `analysis-worker`
**Responsibility:** Asynchronous, CPU-heavy audio analysis processor.

**Key Technical Details:**
- Runs a continuous "claim loop" pulling jobs from the PostgreSQL `bvault-jobs` queue.
- Executes the decoding and metadata extraction logic in `bvault-analysis`.
- Writes the resulting `ANLZ` files (waveforms, beat grids) to the `ArtifactStore`.
- Designed to be horizontally scalable; multiple worker pods can run concurrently, governed by PostgreSQL row-level locks on the jobs table.

## `export-builder`
**Responsibility:** Constructs the final USB export directory structure and manifest.

**Key Technical Details:**
- Also an `axum` web service, usually internal-facing or exposed specifically for export requests.
- Accepts a list of playlist IDs, retrieves the required track metadata, and uses `bvault-export` to generate the `.pdb` database and folder layout in a temporary staging directory.
- Serves the generated `Manifest` back to the client (`bvault-cli`), allowing the client to pull the required files (either from the `RawStore` or the staging directory) down to the target USB drive.
- Performs periodic cleanup of stale export directories in the staging area to prevent disk exhaustion.

## `yt-dlp-ingest`
**Responsibility:** A specialized Python service for downloading and ingesting media from external sources like YouTube.

**Key Technical Details:**
- **Why Python?** To leverage the official `yt-dlp` library directly, ensuring maximum compatibility and keeping up-to-date with frequent extractor patches.
- Operates as a worker, pulling ingestion jobs from the `gateway` via an internal endpoint.
- Downloads the media, extracts the audio to MP3 using `ffmpeg`, and uploads it back to the `gateway`'s standard upload endpoint.
- Uses advanced techniques (e.g., impersonation, PO tokens) to bypass bot-detection measures on streaming platforms. Limits concurrency internally to prevent memory/CPU starvation.
