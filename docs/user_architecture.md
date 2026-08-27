# BeatVault Architecture & Workflow

BeatVault is designed to let you self-host your music collection and export it to a Pioneer CDJ-compatible USB without needing proprietary desktop software. Here's a high-level overview of how the system operates.

## System Architecture

The BeatVault architecture consists of several interconnected components, separating the heavy lifting of audio analysis from the fast, responsive API Gateway.

```mermaid
graph TD
    %% User Interfaces
    User([User]) -.-> CLI[bvault CLI]
    CLI --> Gateway[Gateway Service]
    CLI -.-> USB[(USB Drive)]

    %% Services
    subgraph Kubernetes Cluster
        Gateway
        AnalysisWorker[Analysis Worker]
        ExportBuilder[Export Builder]
        YTDLP[yt-dlp Ingest]
        
        Gateway -- Dispatches Jobs --> JobQueue[(PostgreSQL Jobs)]
        AnalysisWorker -- Consumes Jobs --> JobQueue
        YTDLP -- Consumes Jobs --> JobQueue
        Gateway -- HTTP --> ExportBuilder
    end
    
    %% Storage
    subgraph Storage
        DB[(PostgreSQL Meta)]
        MusicStore[Music Store PVC]
    end

    %% Connections
    Gateway --> DB
    Gateway --> MusicStore
    AnalysisWorker --> MusicStore
    ExportBuilder --> MusicStore
    ExportBuilder -- Staging PDB --> MusicStore
    
    %% Export Flow
    ExportBuilder -.-> |Manifest| CLI
    CLI -- Pulls Files --> MusicStore
```

### Components
1. **bvault CLI:** Your main tool for interacting with BeatVault. You can search, ingest, build playlists, and export to USB directly from your terminal.
2. **Gateway:** The brain of the operation. It handles your logins, saves your playlists, and orchestrates tasks.
3. **PostgreSQL:** The database storing your user accounts, track metadata, and the queue of pending background jobs.
4. **Music Store (PVC):** The persistent volume on your server where all your raw MP3/FLAC files and analyzed waveform data are saved.
5. **Workers:** 
    - **Analysis Worker:** Constantly looks for new music, analyzes the BPM, beat grid, and waveforms, and saves that data to the Music Store.
    - **yt-dlp Ingest:** Safely downloads tracks from external services (like YouTube) and passes them to the Gateway.
6. **Export Builder:** When you request a USB export, this service quickly generates the proprietary Pioneer database (`.pdb`) and tells your CLI exactly which files to copy to your USB.

## The Ingestion Workflow

When you ingest a new track (e.g., via `bvault ingest youtube <url>`), it goes through several steps to ensure it's ready for a CDJ.

```mermaid
sequenceDiagram
    participant CLI
    participant Gateway
    participant YTDLP as yt-dlp Ingest
    participant DB as PostgreSQL
    participant Worker as Analysis Worker
    participant Store as Music Store

    CLI->>Gateway: Ingest Request
    Gateway->>DB: Create Job (yt_dlp_ingest)
    Gateway-->>CLI: Job ID
    
    YTDLP->>DB: Claim Job
    YTDLP->>YTDLP: Download & Extract MP3
    YTDLP->>Gateway: Upload MP3
    Gateway->>Store: Save to RawStore
    Gateway->>DB: Create Job (analysis)
    
    Worker->>DB: Claim Job
    Worker->>Store: Read Raw MP3
    Worker->>Worker: Detect BPM & Waveforms
    Worker->>Store: Save ANLZ Data (ArtifactStore)
```

## The Export Workflow

BeatVault's export process is unique. Instead of generating the entire USB on the server and forcing you to download a massive ZIP file, BeatVault acts as a synchronization engine.

```mermaid
sequenceDiagram
    participant CLI
    participant Gateway
    participant ExportBuilder
    participant Store as Music Store
    participant USB

    CLI->>Gateway: Request Export (Playlist X)
    Gateway->>ExportBuilder: Build Request
    ExportBuilder->>Store: Read Waveforms & Meta
    ExportBuilder->>ExportBuilder: Generate rekordbox PDB
    ExportBuilder->>Store: Save PDB to Staging
    ExportBuilder-->>CLI: Return Manifest (List of files)
    
    CLI->>CLI: Analyze USB contents
    loop For each file in Manifest
        alt File missing on USB
            CLI->>Store: Download File
            CLI->>USB: Write to USB
        else File exists on USB
            CLI->>CLI: Skip (Deduplication)
        end
    end
    CLI->>USB: Write success!
```

**Why this is great:** If you add 5 new songs to a 1,000-song playlist, the CLI will only download and transfer those 5 new songs to your USB, making exports lightning fast!
