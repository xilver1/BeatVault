# BeatVault Crates Technical Documentation

BeatVault's core logic is split into a series of highly-focused Rust crates, ensuring clear separation of concerns, fast compilation, and preventing circular dependencies. This document details the technical implementation and responsibilities of each crate in `bvault-app/crates/`.

## `bvault-core`
**Responsibility:** The authoritative definition of Rekordbox data structures (PDB format) and track metadata representations.

**Key Technical Details:**
- Uses `binrw` for bit-exact, endian-aware binary serialization and deserialization of the `.pdb` device database format used by Pioneer CDJs.
- Defines the `TrackAnalysis` struct which encapsulates all intrinsic audio metadata (BPM, beat grid, waveform data, cue points) required by CDJs.
- **Why it matters:** This crate *must* accurately model the proprietary Rekordbox database structure. Any changes here directly affect whether a CDJ can read the exported USB. 

## `bvault-analysis`
**Responsibility:** Pure audio-analysis front-end. Derives Rekordbox-compatible metadata from raw audio files.

**Key Technical Details:**
- Uses `symphonia` for audio decoding (supporting MP3, FLAC, WAV, AAC, etc.) without relying on system-level FFmpeg binaries for extraction.
- **Beat detection:** Identifies BPM and the first beat to generate a constant-tempo `BeatGrid`.
- **Waveform generation:** Computes the three waveform granularities required by CDJs.
- **Stateless:** It operates entirely on in-memory buffers or streams and returns a `TrackAnalysis` struct. It doesn't write to the database or know about playlists. Identity is strictly the content hash.

## `bvault-hash`
**Responsibility:** The single source of truth for content identity across the entire system.

**Key Technical Details:**
- Computes identity as a 64-bit `xxh3` hash over the first `1 MiB` of content, appended with the file's total size as a little-endian `u64`.
- The hash is rendered as a 16-character lowercase hex string (`hash_hex`).
- Exposes a `ContentHasher` which implements `std::io::Write` to allow streaming hashing (e.g., hashing a file *while* downloading it) to avoid buffering large audio files in RAM.

## `bvault-transfer`
**Responsibility:** A unified abstraction layer for writing to both standard filesystems and Android's Storage Access Framework (SAF).

**Key Technical Details:**
- **Why it matters:** Android apps cannot simply write to `/mnt/sdcard` anymore; they must use the content resolver (SAF). Desktop clients use standard filesystem paths. This crate abstracts the differences behind a single `UsbWriter` trait.
- It is critical for the `bvault-cli` when running in environments like Termux on Android.

## `bvault-manifest`
**Responsibility:** Defines the schema for a USB export manifest.

**Key Technical Details:**
- A pure data-contract crate containing only `serde` definitions. 
- A `Manifest` describes exactly what a USB export should look like: every file path (`usb_path`) and where its bytes come from (`Source::Staging` or `Source::Raw`).
- This allows the `bvault-cli` (client) to parse the layout and perform the file transfers without needing to parse or link the heavy `.pdb` building logic in `bvault-core`.

## `bvault-export`
**Responsibility:** Rekordbox layout generation. Transforms a list of tracks and playlists into a complete CDJ-ready USB folder structure.

**Key Technical Details:**
- Takes `TrackAnalysis` data and assigns CDJ-specific `id`s and `file_path`s.
- Builds the `export.pdb` binary database using `bvault-core`.
- Writes the `ANLZ` (analysis) files to the correct subdirectories.
- Outputs a `Manifest` detailing the layout. It does *not* write to the USB itself; it writes to a staging directory, and the client uses the manifest to execute the transfer.

## `bvault-store`
**Responsibility:** Manages the content-addressed raw audio and artifact storage system.

**Key Technical Details:**
- **RawStore:** Manages the original audio files.
- **ArtifactStore:** Manages the analyzed outputs (ANLZ files, generated waveforms). It acts as the single source of truth for whether a track has been analyzed.
- Uses a marker-atomic write pattern: files are written to `.tmp`, and upon success, a `.ok` marker file is placed. If the marker is missing, the file is considered incomplete/corrupt.

## `bvault-meta`
**Responsibility:** The relational metadata layer interacting with PostgreSQL.

**Key Technical Details:**
- Uses `sqlx` for compile-time checked SQL queries.
- Manages users, playlists, and maps `hash` -> `raw_location`. 
- **Crucial boundary:** It *does not* track if a track is "analyzed". That state is derived from `bvault-store`.

## `bvault-jobs`
**Responsibility:** The PostgreSQL-backed job queue for asynchronous work (analysis and ingestion).

**Key Technical Details:**
- Used to dispatch work from the `gateway` to the `analysis-worker` and `yt-dlp-ingest` services.
- Employs a lease-based claim mechanism (`Queue::claim`) to prevent multiple workers from processing the same job. Includes heartbeat mechanics for long-running jobs.

## `bvault-auth`
**Responsibility:** Pure security primitives for user authentication.

**Key Technical Details:**
- Uses `Argon2id` for password hashing with random per-password salts.
- Uses 256-bit CSPRNG for session tokens. The tokens are hashed (`SHA-256`) before storage. Only the hash lives in the database to prevent session hijacking if the database is leaked.
- Encrypts cookies via AES-256-GCM.
