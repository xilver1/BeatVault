## rekord-export — Application Design

### Scope & first principles

This document covers the **server-side and client-transfer design** of the music-management and rekordbox-export application, reflecting the actual implemented state of the BeatVault project.

Foundational principles that every decision below rests on:

- **Content hash = identity.** A music file is identified by the hash of its content. The hash is a _name_, not a location. Same file → same hash → same analysis, always (analysis is deterministic and singular).
- **One authoritative source per question.** "What should exist" (the plan) and "what does exist" (reality — the USB, the stores) are different questions, each with exactly one source of truth. Associations are _derived_ via keyed lookups, never duplicated as stored fields that can drift.
- **Persist the small, regenerate the large.** Per-track analysis is durable state. The `.pdb`, the device library, and the `Contents/` audio copies are throwaway artifacts, regenerated fresh at export time. Nothing that can be rebuilt is persisted.

### Repository Structure & Components

The BeatVault monorepo is divided into several domains:

- **bvault-app**: The core application, primarily written in Rust as a Cargo workspace.
  - **Crates (Libraries):** Shared libraries including `bvault-core`, `bvault-hash`, `bvault-auth`, `bvault-jobs` (Postgres-backed job queue), `bvault-store` (blob/DB storage), `bvault-meta`, `bvault-export`, and `bvault-transfer` (SAF and file transfer abstractions).
  - **Services (Binaries):** The scalable microservices: `gateway` (API boundary), `analysis-worker` (Symphonia/rustfft analysis), and `export-builder` (`.pdb` generation).
  - **CLI (`bvault-cli`):** The primary user interface for desktop and Termux environments. Handles interactive flows, local file ingestion, Google Drive scraping, and USB export orchestration.
  - **Python Ingestion (`yt-dlp-ingest`):** A standalone FastAPI microservice specifically isolating `yt-dlp` interactions (downloads and Google OAuth/PO tokens).
- **bvault-infra** & **bvault-manifests**: Infrastructure-as-code (Terraform, Ansible) and Kubernetes deployment manifests.
- **bvault-android-connect**: A thin Android app wrapper facilitating yt-dlp-ingest service usage through user cookies.

---

### Data model

- **Playlist meta-document** — one document _per playlist_. Holds playlist name (mandatory), description (optional), and an **unordered set of content hashes**. A playlist is _membership_, not a physical reordering of files or folders.
- **Raw-file lookup table** — `content hash → raw audio file location` in the music store.
- **Analysis lookup table** — `content hash → location of that track's analyzed artifacts`. Both tables are keyed by the **same** hash.
- **"Analyzed" is derived, not stored.** A track is analyzed if its hash resolves in the analysis lookup table. A playlist is analyzed if _every_ hash it references resolves.
- **Analysis is durable state, not a cache.** It is never evicted and never invalidated.

---

### 1. Ingestion of music files

Fundamentally **batch jobs**.

**User flow**

1. User opens the CLI and uses `bvault ingest`.
2. User selects what to import:
    - YouTube → provides a URL or logs in to scrape playlists.
    - Local storage → provides a folder (optionally grouping subfolders into playlists).
    - Google Drive → provides a Drive path.
3. The tracks are downloaded/processed and uploaded to the `gateway`.

**Design decisions & notes**

- **Client vs. Server Ingestion:** 
  - **Local & GDrive:** Handled directly by the `bvault-cli` running on the user's client device. The CLI traverses directories or queries the Google Drive API, reads the audio blobs, and uploads them over HTTP multipart directly to the `gateway`.
  - **YouTube:** The CLI sends the URL to the `gateway`, which queues a job for the `yt-dlp-ingest` Python microservice. 
- **Python Isolation:** The `yt-dlp-ingest` service is **not Rust**. It runs as an isolated FastAPI app to leverage the Python `yt-dlp` ecosystem and avoid crashing Rust processes due to memory/thread constraints in C-extensions.

---

### 2. Creation of playlists

**User flow**

1. User accesses their library via the CLI (`bvault library` / `bvault playlist`).
2. User chooses a folder, a file, or a mix, from their stored library.
3. User submits the selection and provides a **name** and **description**.
4. The created playlist is saved to the central metadata DB via the gateway.

**Design decisions & notes**

- A playlist produces **one meta-document**: name/description + an unordered set of content hashes. It is **not** a physical reordering of files.

---

### 3. Analysis of playlists

**User flow**

1. User lists playlists and triggers analysis via the CLI.
2. The `gateway` resolves the tracks and pushes async jobs to the `analysis-worker`.
3. User monitors progress via `bvault status analysis`.

**Design decisions & notes**

- **Analysis operates on tracks, not playlists.** The gateway deduplicates track hashes across playlists and filters out already-analyzed hashes before enqueueing jobs.
- **Worker contract:** Input is a set of content hashes. The worker decodes the raw audio (Symphonia) and runs FFT (rustfft) to extract BPM, beat grids, waveforms, and cues. The artifacts are written to the analysis store.
- **Analyze and export are independent operations, not a pipeline.** Analysis finishing does **not** trigger a build. The analysis store is the loose coupling between the two.

---

### 4. Export of playlists

**User flow**

1. User plugs the USB into the Android phone/client.
2. User runs `bvault export <playlist_name> --usb` in the CLI.
3. The CLI orchestrates the target path and Android Storage Access Framework (SAF) checks.
4. A progress bar appears.
5. On completion, the user disconnects the USB.

**Design decisions & notes**

**Build model**

- The `gateway` gathers the **union of the selected playlists' hashes** and their analyzed artifacts. 
- The `export-builder` generates the **`.pdb` + device library fresh** across the combined set.
- The `.pdb`, device library, and `Contents/` are **throwaway** — built at export time and destroyed on the server after transfer.

**Where the build runs & how audio moves**

- The **server builds the export**; the client (CLI) pulls it and writes it to the USB.
- **Transfer order:** `.pdb` + structure **first** → Analyzed-track artifacts (ANLZ) **next** → `.flac`/`.wav`/`.mp3` audio **last**.
- **Android USB Cleanup:** When writing to Android USBs via Termux SAF, Android aggressively auto-creates `Music`, `Pictures`, `Movies`, and `LOST.DIR` folders. The `bvault-transfer` abstraction recursively empties and removes these generated folders during export so they do not clutter the root of the USB drive (though Android may instantly recreate system-protected folders like `LOST.DIR`).

**Transfer as resumable convergence**

- The transfer is a **reconciliation loop toward a declared target**. Pause, resume, and survive-app-death are consequences of the transfer being **idempotent and resumable**.
- **Diff = plan − actual = work remaining.** 
- **Progress unit = per file.** A track is either fully written-and-verified or not.
- **Verification on write:** The file is hash-verified immediately after writing to the USB before marking it complete, catching USB yanked-mid-write corruptions.
- **Verification on resume:** A cheap presence + size match check is used to avoid re-hashing gigabytes on every run.

---

### Cross-cutting notes

- **12-factor config.** Configuration (music-store path, DB connection, API URLs) is injected via Environment Variables in the Kubernetes cluster or Termux shell.
- **Queue mechanism.** Background async processing relies on **`bvault-jobs`**, which uses an in-cluster **Postgres-backed queue** (via `sqlx`). This avoids the memory overhead of a heavy broker like RabbitMQ or Kafka in a RAM-limited cluster, satisfying the project's constraints.
- **Storage boundaries.** Relational metadata (playlists, job states) lives in Postgres. Large immutable audio blobs live in PersistentVolumes/blob storage. Worker pods are stateless.
- **Analysis versioning** — composite `hash + version` key, if/when analysis algorithms change.