# BeatVault documentation

BeatVault is a self-hosted music platform for DJs: ingest audio from anywhere, analyse it
(BPM, beat grids, waveforms), and export directly to Pioneer CDJ-compatible USB drives
without rekordbox or any proprietary desktop software. It runs on a home-built,
GitOps-managed Kubernetes cluster.

This folder documents how the system works. For an overview of the whole project and the
three repositories that make it up, start at the [top-level README](../README.md).

## Understanding the system

- **[Architecture & workflow](user_architecture.md)** — the components, and how ingestion
  and USB export flow through them, with sequence diagrams. The best starting point.

## Internals

For maintainers and contributors working on the application:

- **[Crates](technical_crates.md)** — the Rust library crates that hold BeatVault's core
  logic: the rekordbox format model (`bvault-core`), audio analysis (`bvault-analysis`),
  content-addressed storage (`bvault-store`), the metadata and job layers, auth, and more.
- **[Services](technical_services.md)** — the deployable backend services
  (`gateway`, `analysis-worker`, `export-builder`, `yt-dlp-ingest`) that compose those crates.

## By layer

Each repository carries its own README with depth on that layer:

- **[bvault-app](../bvault-app/README.md)** — the application and CLI.
- **[bvault-infra](../bvault-infra/README.md)** — how the cluster is provisioned and configured.
- **[bvault-manifests](../bvault-manifests/README.md)** — how workloads are deployed via GitOps.