# Non-Technical Project Status Report

**Project Name:** Virtual Coverage Map  
**Report Date:** 19 February 2026  
**Prepared For:** Stakeholders, Business Analysts, and Clients  
**Version:** 1.0.0  

---

## 1. Executive Summary

**Virtual Coverage Map** is a mobile application designed to crowdsource mobile network signal strength data across India. The app empowers everyday smartphone users to passively contribute real-world cellular coverage data — including 5G, 4G, and 2G/3G — simply by installing the app on their Android devices.

### Business Objectives

- **Transparent Network Coverage:** Build an independent, user-driven coverage map that goes beyond the claims of telecom carriers, showing actual signal quality in every neighborhood.
- **Carrier Comparison:** Enable consumers by giving them the ability to compare network performance between carriers (e.g., Jio, Airtel, Vi, BSNL) in specific areas before choosing a plan.
- **India-First Focus:** Address a critical gap in the Indian telecom market where reliable, granular coverage information is scarce, especially in tier-2 and tier-3 cities and rural areas.
- **Privacy by Design:** Comply fully with India's Digital Personal Data Protection (DPDP) Act, 2023. No personal identifiers leave the user's device — all data is anonymized before upload.

### Product Vision

The end-goal is a publicly accessible, continuously-updated heatmap of real mobile network quality across India — a "Google Maps for Signal Strength" — powered entirely by community contributions.

---

## 2. Project Milestones & Progress

| Milestone | Status | Description |
|---|---|---|
| **Phase 1: Project Setup** | ✅ Complete | Android project scaffolding, build system, and dependency configuration. |
| **Phase 2: Signal Collection Engine** | ✅ Complete | Background data collection service that reads signal metrics from all active SIM cards. |
| **Phase 3: Privacy & Compliance** | ✅ Complete | Full DPDP Act 2023 compliance module — anonymized device IDs, location fuzzing, and H3 hex indexing. |
| **Phase 4: Local Data Storage** | ✅ Complete | Offline-first database for storing all signal measurements on-device. |
| **Phase 5: Map Visualization** | ✅ Complete | Interactive map displaying a color-coded signal strength heatmap with carrier filtering. |
| **Phase 6: Collection Dashboard** | ✅ Complete | User-facing screen showing live signal stats, carrier information, and collection controls. |
| **Phase 7: Backend Infrastructure** | ✅ Complete | Node.js/TypeScript + Express backend with PostgreSQL, PostGIS, and RESTful API endpoints. |
| **Phase 8: Automated Data Sync** | ✅ Complete | Android SyncWorker uploads data from phone to backend every 15 minutes over Wi-Fi. |
| **Phase 9: H3 Aggregation Pipeline** | ✅ Complete | Server-side heatmap aggregation, carrier stats, PostGIS spatial queries, and materialized views. |
| **Phase 10: Carrier Comparison UI** | ⏳ Pending | Dedicated screen for side-by-side carrier performance comparison in a given area. |

### Summary

- **9 of 10 milestones complete (~90%)**
- The full end-to-end pipeline is operational: phone collects signals → stores locally → syncs to backend → PostgreSQL stores with PostGIS spatial indexing → API serves heatmap/stats data.
- **343 real signal measurements** have been successfully synced from a physical device to the central database.
- The remaining milestone is the Carrier Comparison UI for side-by-side analysis.

---

## 3. Feature Breakdown (Business Logic)

### 3.1 Background Signal Collection

**What it does:** Once the user opens the app and grants permissions, signal data is collected automatically in the background every 10 seconds. The user does not need to interact with the app further — it works silently.

**Why it's valuable:** Eliminates user friction entirely. Data collection is passive, which maximizes the volume of crowdsourced measurements with minimal user effort.

---

### 3.2 Dual-SIM Support

**What it does:** The app detects all active SIM cards in a dual-SIM device and collects signal data for **each SIM independently**. For example, if a user has both Jio and Airtel, the app captures both carriers' signal quality at the same time and location.

**Why it's valuable:** India is one of the world's largest dual-SIM markets. Supporting both SIMs doubles the data density and allows direct, same-device carrier comparisons — the most accurate way to evaluate relative network quality.

---

### 3.3 5G Network Intelligence (SA vs. NSA Detection)

**What it does:** The app not only detects 5G coverage but distinguishes between **True 5G (Standalone / SA)** and **5G-over-LTE (Non-Standalone / NSA)**. This is critical because the performance characteristics differ dramatically between the two.

**Why it's valuable:** As India's 5G rollout accelerates, consumers and enterprises need to know whether they're getting genuine standalone 5G or simply an upgraded LTE connection marketed as 5G.

---

### 3.4 Multi-Generation Network Support

**What it does:** The app collects signal metrics for all network generations: **5G NR**, **4G LTE**, and **2G/3G GSM**. Each technology captures its most relevant signal quality indicators.

**Why it's valuable:** India still has significant 2G/3G infrastructure in rural and semi-urban areas. Capturing all generations ensures the coverage map is comprehensive and representative of the full user experience.

---

### 3.5 Interactive Coverage Heatmap

**What it does:** A full-screen map displays hexagonal cells colored by signal quality:
- 🟢 **Green** — Excellent signal (better than -70 dBm)
- 🟡 **Yellow/Lime** — Good signal (-70 to -85 dBm)
- 🟠 **Orange** — Fair signal (-85 to -100 dBm)
- 🔴 **Red** — Poor signal (worse than -100 dBm)

Users can tap any hexagon to view details (average signal strength and sample count). Carrier-specific filter chips allow viewing one carrier at a time or all carriers together.

**Why it's valuable:** Transforms raw data into an intuitive, visual experience. Users can immediately identify coverage dead zones, strong signal areas, and compare carriers geographically.

---

### 3.6 Live Signal Dashboard

**What it does:** A dedicated "Collect" screen shows:
- Total number of measurements collected.
- Number of measurements pending upload to the server.
- Per-carrier live signal status, including network type, signal strength, and a human-readable quality rating (Excellent / Good / Fair / Poor).
- A start/stop button to control data collection.

**Why it's valuable:** Gives users transparency and control. They can see that the app is working, understand their own signal quality in real time, and choose when to contribute data.

---

### 3.7 Privacy Protection (DPDP Act 2023 Compliance)

**What it does:**
- **No personal identifiers are transmitted.** Device IDs are hashed with SHA-256 and salted; raw IDs never leave the device.
- **Location data is anonymized** using a hexagonal grid system (H3). Precise GPS coordinates are converted to hex cell IDs, which represent small geographic zones without exposing exact addresses.
- **Home location protection:** During nighttime hours (10 PM – 6 AM), location precision is automatically reduced to neighborhood level to prevent home address inference.

**Why it's valuable:** Trust is essential for crowdsourced applications. Users need confidence that contributing data does not compromise their privacy. This feature ensures compliance with Indian data protection law and builds user trust.

---

### 3.8 Offline-First Data Storage

**What it does:** All signal measurements are stored locally on the device in a structured database. Data persists even when the user is offline or in areas with no connectivity. A sync mechanism tracks which records have been uploaded and which are still pending.

**Why it's valuable:** In India, intermittent connectivity is common. Offline-first ensures that data collected in low-connectivity areas — often the most valuable data points — is never lost.

---

### 3.9 Backend Infrastructure (Node.js + PostgreSQL)

**What it does:** A server-side backend built with Node.js/TypeScript and Express receives signal data from all phones and stores it in a PostgreSQL database with PostGIS (spatial queries) extensions. The backend provides RESTful API endpoints for:
- **Signal ingestion** — Receives batches of up to 500 measurements per request.
- **Heatmap data** — Returns H3-aggregated signal quality for any geographic bounding box.
- **Carrier statistics** — Shows total measurements, unique devices, and coverage hexagons per carrier.
- **Nearby signal lookup** — PostGIS spatial queries find measurements within a radius of any point.

**Why it's valuable:** Centralizes all crowdsourced data from every user's device into a single, queryable database. This is the foundation for the public coverage map.

---

### 3.10 Automated Data Sync (SyncWorker)

**What it does:** Every 15 minutes, the app automatically uploads all unsynced signal measurements from the phone to the backend server, without any user intervention. The sync is:
- **Automatic** — Runs in the background via Android WorkManager.
- **Network-aware** — Only syncs when the device has network connectivity.
- **Resilient** — Retries with exponential backoff on failure.
- **Efficient** — Uploads in batches of 100 measurements at a time.
- **Self-cleaning** — Removes synced data older than 7 days from the phone to free storage.

**Why it's valuable:** Users don't need to do anything — data flows from their phone to the central database automatically. Even if the app is closed, WorkManager ensures the sync happens reliably.

---

## 4. User Journeys / Workflows

### 4.1 First-Time User Setup

1. User installs the app from the Android device.
2. On first launch, the app requests three categories of permissions:
   - **Location** (to geo-tag signal measurements)
   - **Phone State** (to read SIM and signal data)
   - **Notifications** (to show the background collection indicator)
3. Upon granting permissions, signal collection begins automatically.
4. A persistent notification appears: *"Collecting signal data..."* — confirming background operation.
5. The user is taken to the **Map** screen by default.

### 4.2 Browsing the Coverage Map

1. User navigates to the **Map** tab via the bottom navigation bar.
2. An interactive map centered on India loads with OpenStreetMap tiles.
3. As data is collected, colored hexagons appear on the map showing signal quality.
4. User can tap on any hexagon to view: average signal strength (in dBm), number of samples, and a Quality rating.
5. User can filter by carrier using the chip buttons at the top (e.g., "Jio", "Airtel", "All Carriers").
6. A refresh button (bottom-right) reloads the latest data.

### 4.3 Viewing Live Signal Stats

1. User navigates to the **Collect** tab via the bottom navigation bar.
2. A statistics card shows total measurements collected and uploads pending.
3. A live signal status card shows each active carrier with its network type (e.g., "5G_SA ⚡ True 5G") and signal quality (e.g., "-92 dBm — Good 🟡").
4. A start/stop button allows the user to pause or resume data collection at will.

### 4.4 Continuous Background Operation

1. After initial setup, the app runs as a background service.
2. Every 10 seconds, the service reads signal data from all active SIMs, captures GPS coordinates, and saves the measurement to local storage.
3. The user may close the app; the service continues running.
4. When the user opens the app again, the map and stats reflect all data collected in the interim.

---

## 5. Outstanding Blockers / Decisions

| # | Item | Type | Impact |
|---|---|---|---|
| 1 | ~~**Backend API not yet built.**~~ | ✅ Resolved | Backend is live with Express + PostgreSQL + PostGIS. Data syncs automatically from phones. |
| 2 | ~~**Backend sync strategy undecided.**~~ | ✅ Resolved | SyncWorker uploads batches of 100 every 15 minutes with retry logic and cleanup. |
| 3 | **Cloud deployment pending.** Backend currently runs on local machine (same Wi-Fi only). | Decision Required | Needs cloud hosting (Railway/Render/AWS) for public access. |
| 4 | **Carrier comparison UI design not finalized.** No mockups or UX direction for the comparison screen. | Decision Required | Blocks Phase 10 implementation. |
| 5 | **ProGuard / R8 not configured for release.** Code shrinking and obfuscation are disabled. | Blocker for Production | APK size will be larger than necessary; no code protection. |
| 6 | **App not published on Google Play Store.** Currently distributed via debug APK only. | Blocker for Distribution | Limits reach of crowdsourcing effort. |
| 7 | **Hardcoded privacy salt.** The salt used for device ID hashing is a static string in the source code. | Risk | Should be fetched from the server in production for security. |

---

*End of Non-Technical Report*
