# BeatVault Documentation

Welcome to the BeatVault documentation. BeatVault is a self-hosted music infrastructure for DJs, designed to ingest audio files, analyze them (BPM, waveforms, beat grids), and export directly to Pioneer CDJ-compatible USB drives without the need for proprietary desktop software like Rekordbox.

## User Guide
* [Architecture & Workflow](user_architecture.md) - High-level overview of how BeatVault operates, including visual flowcharts for ingestion and USB export.

## Technical Documentation
For maintainers and contributors:
* [Crates Technical Docs](technical_crates.md) - Deep dive into the distinct Rust library crates (`bvault-core`, `bvault-hash`, etc.) that make up the core logic of the application.
* [Services Technical Docs](technical_services.md) - Deep dive into the deployable backend services (`gateway`, `analysis-worker`, `export-builder`, `yt-dlp-ingest`) that orchestrate the cluster.

