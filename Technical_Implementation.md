# Technical Implementation Report

**Project Name:** Virtual Coverage Map  
**Report Date:** 19 February 2026  
**Prepared For:** Developers, System Architects, and DevOps  
**Version:** 1.0.0  
**Application ID:** `com.virtualcoverage.signalmap`  

---

## 1. System Architecture & Tech Stack

### 1.1 Platform & Build Configuration

| Parameter | Value |
|---|---|
| **Platform** | Android (native) |
| **Language** | Kotlin 1.9.22 |
| **Min SDK** | 29 (Android 10) |
| **Target SDK** | 35 (Android 15) |
| **Compile SDK** | 35 |
| **JVM Target** | Java 17 |
| **Build Tool** | Gradle 8.2.2 (Kotlin DSL) |
| **Annotation Processing** | KSP 1.9.22-1.0.17 |

### 1.2 Architecture Pattern

The project follows **Clean Architecture** with **MVVM** (Model-View-ViewModel):

```
presentation/       → Activities, Fragments, ViewModels (UI layer)
domain/usecase/      → Business logic (PrivacyManager)
data/repository/     → Repository abstraction
data/local/          → Room database, DAOs, entities (data layer)
data/remote/         → Retrofit API service + DTOs (remote data layer)
service/             → Android foreground service
sync/                → WorkManager SyncWorker (background data upload)
util/                → H3Android utility
di/                  → Hilt DI modules (Database, Network)
```

### 1.3 Dependency Graph

| Category | Library | Version | Purpose |
|---|---|---|---|
| **DI** | Dagger Hilt | 2.51 | Constructor injection across all layers |
| **Database** | Room | 2.6.1 | SQLite abstraction with compile-time query validation |
| **Networking** | Retrofit + OkHttp | 2.9.0 / 4.12.0 | HTTP client for backend API communication |
| **Background Sync** | WorkManager | 2.9.0 | Periodic background data upload (every 15 min) |
| **Hilt WorkManager** | AndroidX Hilt Work | 1.2.0 | DI support for WorkManager workers |
| **Maps** | osmdroid | 6.1.18 | OpenStreetMap tiles, no API key required |
| **Location** | Google Play Services Location | 21.1.0 | FusedLocationProvider for GPS |
| **UI** | Material Design Components | 1.11.0 | BottomNavigationView, Chips, Cards, FAB |
| **Navigation** | AndroidX Navigation | 2.7.7 | Fragment navigation (partially used) |
| **Async** | Kotlin Coroutines | 1.8.0 | Structured concurrency on IO and Main dispatchers |
| **Lifecycle** | Lifecycle + ViewModel + LiveData | 2.7.0 | Lifecycle-aware data observation |
| **Serialization** | Gson | 2.10.1 | JSON serialization for API payloads |
| **Spatial** | H3Android (custom) | N/A | Pure-Kotlin H3 hexagonal indexing |

### 1.4 Directory Structure

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/virtualcoverage/signalmap/
│   │   │   ├── SignalMapApp.kt                          # @HiltAndroidApp + WorkManager Configuration.Provider
│   │   │   ├── di/
│   │   │   │   ├── DatabaseModule.kt                    # Hilt @Module (Room DB + DAO providers)
│   │   │   │   └── NetworkModule.kt                     # Hilt @Module (OkHttp + Retrofit + SignalApiService)
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── SignalDatabase.kt                # Room @Database (v1)
│   │   │   │   │   ├── dao/SignalMeasurementDao.kt      # @Dao with 12 query methods
│   │   │   │   │   └── entity/SignalMeasurementEntity.kt # @Entity with 23 columns
│   │   │   │   ├── remote/
│   │   │   │   │   └── SignalApiService.kt               # Retrofit API interface + DTOs
│   │   │   │   └── repository/SignalRepository.kt       # @Singleton repository
│   │   │   ├── domain/
│   │   │   │   └── usecase/PrivacyManager.kt            # DPDP compliance, H3, hashing
│   │   │   ├── presentation/
│   │   │   │   ├── MainActivity.kt                      # Single Activity, bottom nav
│   │   │   │   ├── collect/CollectFragment.kt           # Signal stats + start/stop
│   │   │   │   └── map/
│   │   │   │       ├── MapFragment.kt                   # osmdroid + H3 hex rendering
│   │   │   │       └── MapViewModel.kt                  # @HiltViewModel
│   │   │   ├── service/
│   │   │   │   └── SignalCollectionService.kt           # Foreground service (479 LOC)
│   │   │   ├── sync/
│   │   │   │   └── SyncWorker.kt                        # HiltWorker - background data upload
│   │   │   └── util/
│   │   │       └── H3Android.kt                         # Pure-Kotlin H3 implementation
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml                    # CoordinatorLayout + BottomNav
│   │   │   │   ├── fragment_map.xml                     # MapView + ChipGroup + Legend + FAB
│   │   │   │   └── fragment_collect.xml                 # Stats cards + Start/Stop button
│   │   │   └── menu/bottom_nav_menu.xml                 # Map | Collect
│   │   └── AndroidManifest.xml                           # 8 permissions, 1 Activity, 1 Service
│   └── build.gradle.kts                                  # App-level build config
├── build.gradle.kts                                      # Root plugins
├── settings.gradle.kts                                   # rootProject.name = "VirtualCoverageMap"
└── README.md
```

---

## 2. Technical Feature Mapping

### 2.1 Background Signal Collection

> **Non-Technical Reference:** § 3.1 — "Background Signal Collection"

**Core File:** `service/SignalCollectionService.kt` (479 lines)

**Implementation:**
- Android **foreground service** (`Service` subclass) annotated with `@AndroidEntryPoint` for Hilt injection.
- Uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION` for Android 10+ compliance.
- `START_STICKY` return ensures the OS restarts the service after kills.
- A `Handler` + `Runnable` loop on `Looper.getMainLooper()` schedules data collection every **10,000ms**.
- `FusedLocationProviderClient` provides GPS coordinates at `PRIORITY_HIGH_ACCURACY` with 10-second intervals.

**Key Methods:**
```kotlin
// Main collection loop
private fun collectSignalData()           // Iterates all SIMs, reads CellInfo
private fun processNrCell(...)            // Extracts 5G NR metrics
private fun processLteCell(...)           // Extracts 4G LTE metrics
private fun processGsmCell(...)           // Extracts 2G/3G GSM metrics
private fun saveMeasurement(entity)       // Persists via SignalRepository on Dispatchers.IO
private fun detect5GType(telephonyMgr)    // SA vs NSA classification
```

**Lifecycle:**
```
onCreate()        → Init FusedLocationClient + LocationCallback
onStartCommand()  → createNotificationChannel() → startForeground() → schedule first collection
onDestroy()       → Remove handler callbacks, stop location updates, cancel serviceJob
```

**Coroutine Scope:** `CoroutineScope(Dispatchers.IO + SupervisorJob())` — fault-tolerant; individual save failures don't kill the service.

---

### 2.2 Dual-SIM Support

> **Non-Technical Reference:** § 3.2 — "Dual-SIM Support"

**Core File:** `service/SignalCollectionService.kt`, lines 199–247

**Implementation:**
- Uses `SubscriptionManager.activeSubscriptionInfoList` to enumerate all active SIM subscriptions.
- For each `SubscriptionInfo`, creates a subscription-specific `TelephonyManager` via `createForSubscriptionId(subId)`.
- Calls `TelephonyManager.allCellInfo` per subscription to get SIM-specific cell towers.
- Filters to `isRegistered == true` cells only (ignores neighboring cells).

**Data Captured Per SIM:**
```kotlin
carrierName: String       // From SubscriptionInfo.carrierName
subscriptionId: Int       // Unique SIM identifier
```

**Permission Required:** `Manifest.permission.READ_PHONE_STATE`

---

### 2.3 5G Network Intelligence (SA vs. NSA Detection)

> **Non-Technical Reference:** § 3.3 — "5G Network Intelligence"

**Core File:** `service/SignalCollectionService.kt`, method `detect5GType()`, lines 457–477

**Algorithm:**
```kotlin
fun detect5GType(telephonyManager: TelephonyManager): String {
    // Android 11+ (API 30):
    val dataNetworkType = telephonyManager.dataNetworkType
    val voiceNetworkType = telephonyManager.voiceNetworkType

    return when {
        dataNetworkType == NETWORK_TYPE_NR && voiceNetworkType == NETWORK_TYPE_NR -> "5G_SA"
        dataNetworkType == NETWORK_TYPE_NR -> "5G_NSA"  // Data NR, voice LTE
        dataNetworkType == NETWORK_TYPE_LTE -> "4G_LTE"
        else -> "Unknown"
    }
    // Android 10 (API 29): Presence of CellInfoNr → "5G"
}
```

**Rationale:** True SA has both voice and data on NR. NSA anchors voice on LTE while data uses NR. This is the standard detection method using public Android APIs.

---

### 2.4 Multi-Generation Network Support

> **Non-Technical Reference:** § 3.4 — "Multi-Generation Network Support"

**Core File:** `service/SignalCollectionService.kt`

**Cell Type Dispatch (Kotlin `when` pattern matching):**

| Android Class | Handler Method | Network Type Label | Metrics Extracted |
|---|---|---|---|
| `CellInfoNr` | `processNrCell()` | `5G_SA` / `5G_NSA` | SS-RSRP, SS-SINR, CSI-RSRP, PCI, TAC, NCI |
| `CellInfoLte` | `processLteCell()` | `4G_LTE` | RSRP, RSRQ, SINR (RSSNR), PCI, TAC, CI |
| `CellInfoGsm` | `processGsmCell()` | `2G_GSM` | dBm, ASU Level, LAC, CID |

**Signal Metrics Glossary:**
- **RSRP** (Reference Signal Received Power) — Signal strength in dBm
- **RSRQ** (Reference Signal Received Quality) — Signal quality in dB
- **SINR** (Signal-to-Interference-plus-Noise Ratio) — Channel quality in dB
- **SS-RSRP / SS-SINR** — Synchronization Signal variants for 5G NR
- **CSI-RSRP** — Channel State Information RSRP for 5G NR
- **PCI** — Physical Cell ID (cell tower identifier)
- **TAC** — Tracking Area Code
- **NCI** — NR Cell Identity (5G)
- **CI** — Cell Identity (LTE)
- **LAC / CID** — Location Area Code / Cell ID (GSM legacy)

---

### 2.5 Interactive Coverage Heatmap

> **Non-Technical Reference:** § 3.5 — "Interactive Coverage Heatmap"

**Core Files:** `presentation/map/MapFragment.kt` (232 lines), `presentation/map/MapViewModel.kt` (95 lines)

**Map Engine:** osmdroid `MapView` with `TileSourceFactory.MAPNIK` (OpenStreetMap Mapnik tiles).

**Rendering Pipeline:**
1. **Data Query:** `MapViewModel.loadHeatmapForBounds()` calls `SignalRepository.getHeatmapData(minLat, maxLat, minLng, maxLng)`.
2. **SQL Aggregation (Room DAO):**
   ```sql
   SELECT h3IndexRes9,
          AVG(COALESCE(rsrp, ssRsrp, dbm)) AS avgRsrp,
          COUNT(*) AS count
   FROM signal_measurements
   WHERE latitude BETWEEN :minLat AND :maxLat
     AND longitude BETWEEN :minLng AND :maxLng
   GROUP BY h3IndexRes9
   ```
   Returns `List<H3AggregateResult>` — one entry per H3 cell with average RSRP and sample count.
3. **Hex Boundary Calculation:** `PrivacyManager.getH3Boundary(h3Index)` → `H3Android.cellToBoundary()` returns 6 `GeoPoint` vertices.
4. **Polygon Rendering:** Each H3 cell is rendered as an osmdroid `Polygon` with:
   - `fillPaint.color` set by the RSRP color ladder (Green → Lime → Amber → Orange → Red)
   - `outlinePaint` white at 40% opacity, 1.5f stroke width
   - Tap handler shows a tooltip with average RSRP, sample count, and quality label.

**Color Coding Implementation:**
```kotlin
fun getColorForRsrp(rsrp: Int): Int = when {
    rsrp > -70  -> Color.argb(160, 34, 197, 94)   // Green - Excellent
    rsrp > -85  -> Color.argb(160, 132, 204, 22)  // Lime - Good
    rsrp > -100 -> Color.argb(160, 245, 158, 11)  // Amber - Fair
    rsrp > -110 -> Color.argb(160, 249, 115, 22)  // Orange - Poor
    else         -> Color.argb(160, 220, 38, 38)   // Red - Very Poor
}
```

**Carrier Filtering:** `ChipGroup` with `singleSelection=true`. Selecting a chip calls `viewModel.selectCarrier(carrier)` which currently filters data load (the filter is applied in-memory — SQL-level carrier filtering not yet implemented in the DAO).

**Refresh Mechanism:** `FloatingActionButton` triggers `viewModel.refreshData()` which re-queries the DB for the current map bounds.

---

### 2.6 Live Signal Dashboard

> **Non-Technical Reference:** § 3.6 — "Live Signal Dashboard"

**Core File:** `presentation/collect/CollectFragment.kt` (94 lines)

**Data Sources:**
- `MapViewModel.totalCount` — `StateFlow<Int>` from `SignalRepository.getTotalCount()` → `signalMeasurementDao.getTotalCount()` (Room `Flow` → `stateIn`)
- `MapViewModel.unsyncedCount` — same pattern via `getUnsyncedCount()`
- `MapViewModel.latestSignals` — `LiveData<Map<String, SignalMeasurementEntity>>` loaded on init via `getAllCarriers()` → per-carrier `getLatestForCarrier()`.

**Signal Quality Display Logic:**
```kotlin
fun getQuality(rsrp: Int): String = when {
    rsrp > -70  -> "Excellent 🟢"
    rsrp > -85  -> "Good 🟡"
    rsrp > -100 -> "Fair 🟠"
    else        -> "Poor 🔴"
}
```

**Collection Control:** Start/Stop button toggles `MainActivity.startSignalCollection()` / `stopSignalCollection()` which starts/stops `SignalCollectionService` via `startForegroundService()` / `stopService()`.

---

### 2.7 Privacy Protection (DPDP Act 2023 Compliance)

> **Non-Technical Reference:** § 3.7 — "Privacy Protection"

**Core Files:** `domain/usecase/PrivacyManager.kt` (135 lines), `util/H3Android.kt` (174 lines)

#### 2.7.1 H3 Hexagonal Indexing

**Implementation:** `H3Android` — a pure-Kotlin implementation of Uber's H3 spatial indexing.

**Rationale:** The standard `com.uber:h3:4.1.1` Java library uses JNI native code (`libh3-java.so`) that does not ship ARM64 binaries for Android. This caused fatal `UnsatisfiedLinkError` crashes on all physical devices (documented in `crash_after_fix.txt`). A pure-Kotlin implementation was written to eliminate the native dependency.

**Core Algorithm (`latLngToCell`):**
1. Validate inputs (lat: -90..90, lng: -180..180, resolution: 0..15).
2. Find the closest icosahedron face (0–19) via haversine distance to 20 face center points.
3. Calculate grid row/col using the hex edge length for the requested resolution.
4. Encode face, resolution, row, col into a 64-bit integer in H3-compatible format: `[4-bit mode][4-bit resolution][7-bit face][22-bit row][22-bit col]`.
5. Format as 16-char hex string.

**Resolutions Used:**
- **Resolution 11** (~0.003 km², room-level) — Fine-grained local storage.
- **Resolution 9** (~0.1 km², neighborhood-level) — Aggregation for heatmap + nighttime privacy.

**Boundary Computation (`cellToBoundary`):** Decodes the H3 index, recovers center lat/lng, and generates 6 vertices at 60° intervals offset by 30° (flat-top hexagon orientation).

#### 2.7.2 Device ID Anonymization

```kotlin
fun getHashedDeviceId(): String {
    val androidId = Settings.Secure.getString(contentResolver, ANDROID_ID)
    return sha256("$androidId:$salt")  // salt = "vcm_salt_2026"
}
```

- Uses `ANDROID_ID` (per-app, resettable) — not IMEI or IMSI.
- SHA-256 hash with static salt. **Technical Debt:** Salt is hardcoded; should be fetched from backend.

#### 2.7.3 Home Location Fuzzing

```kotlin
fun applyHomeLocationFuzzing(lat: Double, lng: Double, hourOfDay: Int): Pair<String, String> {
    val h3Res11 = getH3RoomLevel(lat, lng)
    val h3Res9 = getH3NeighborhoodLevel(lat, lng)
    return if (isNightTime(hourOfDay)) {  // 10 PM – 6 AM
        Pair(h3Res9, h3Res9)  // Both Res 9 → neighborhood precision only
    } else {
        Pair(h3Res11, h3Res9)  // Daytime: full room-level precision
    }
}
```

This reduces precision during sleeping hours to prevent home address inference from dense data clusters.

---

### 2.8 Offline-First Data Storage

> **Non-Technical Reference:** § 3.8 — "Offline-First Data Storage"

**Core Files:**
- `data/local/SignalDatabase.kt` — Room `@Database(version = 1)`
- `data/local/entity/SignalMeasurementEntity.kt` — Room `@Entity`
- `data/local/dao/SignalMeasurementDao.kt` — Room `@Dao`
- `data/repository/SignalRepository.kt` — Repository abstraction
- `di/DatabaseModule.kt` — Hilt `@Module`

#### Database Schema: `signal_measurements`

| Column | Type | Description |
|---|---|---|
| `id` | `Long` (PK, auto) | Primary key |
| `timestamp` | `Long` | Unix epoch ms |
| `carrierName` | `String` | e.g., "Jio", "Airtel" |
| `subscriptionId` | `Int` | SIM subscription ID |
| `networkType` | `String` | `5G_SA`, `5G_NSA`, `4G_LTE`, `2G_GSM` |
| `rsrp` | `Int?` | LTE RSRP (dBm) |
| `rsrq` | `Int?` | LTE RSRQ (dB) |
| `sinr` | `Int?` | LTE SINR (dB) |
| `ssRsrp` | `Int?` | 5G NR SS-RSRP (dBm) |
| `ssSinr` | `Int?` | 5G NR SS-SINR (dB) |
| `csiRsrp` | `Int?` | 5G NR CSI-RSRP (dBm) |
| `dbm` | `Int?` | GSM dBm |
| `asuLevel` | `Int?` | GSM ASU level |
| `pci` | `Int?` | Physical Cell ID |
| `tac` | `Int?` | Tracking Area Code |
| `ci` | `Long?` | Cell Identity |
| `lac` | `Int?` | Location Area Code (GSM) |
| `h3Index` | `String` | H3 Res 11 index |
| `h3IndexRes9` | `String` | H3 Res 9 index |
| `latitude` | `Double` | Raw GPS lat (local only) |
| `longitude` | `Double` | Raw GPS lng (local only) |
| `hashedDeviceId` | `String` | SHA-256 of device ID |
| `deviceModel` | `String` | e.g., "realme RMX3430" |
| `androidVersion` | `Int` | API level |
| `isSynced` | `Boolean` | Upload status flag |
| `syncAttempts` | `Int` | Failed upload counter |

**Indices:**
```kotlin
Index(value = ["h3Index"])                  // Fast spatial lookup
Index(value = ["carrierName", "timestamp"]) // Carrier + time range queries
Index(value = ["isSynced"])                 // Upload queue queries
```

#### DAO Operations (12 methods)

| Method | Type | Purpose |
|---|---|---|
| `insert()` | Suspend | Single record insert (REPLACE conflict) |
| `insertAll()` | Suspend | Batch insert |
| `getUnsyncedMeasurements()` | Suspend | Get top 100 unsynced records (for upload batch) |
| `markAsSynced(ids)` | Suspend | Mark records as uploaded |
| `incrementSyncAttempts(ids)` | Suspend | Increment retry counter on upload failure |
| `deleteSyncedBefore(timestamp)` | Suspend | Cleanup synced records older than threshold |
| `deleteFailedRecords(maxAttempts)` | Suspend | Purge records that failed > 5 times |
| `getUnsyncedCount()` | Flow | Live unsynced record count |
| `getTotalCount()` | Flow | Live total record count |
| `getLatestForCarrier(carrier)` | Suspend | Latest measurement per carrier |
| `getAllCarriers()` | Suspend | Distinct carrier names |
| `getHeatmapData(minLat, maxLat, ...)` | Suspend | H3-aggregated avg RSRP per cell |
| `getRecentMeasurements()` | Flow | Latest 20 measurements (live) |
| `getMeasurementsForH3(h3Index)` | Suspend | Up to 50 records per H3 cell |

#### Repository Layer

- `SignalRepository` is `@Singleton`, injected via Hilt constructor injection.
- Wraps all DAO methods with clean public API.
- `cleanupOldRecords()` removes synced data older than 7 days and records that failed > 5 upload attempts.

#### Hilt DI Module

```kotlin
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideSignalDatabase(@ApplicationContext ctx: Context): SignalDatabase {
        return Room.databaseBuilder(ctx, SignalDatabase::class.java, "signal_database")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides @Singleton
    fun provideSignalMeasurementDao(db: SignalDatabase): SignalMeasurementDao {
        return db.signalMeasurementDao()
    }
}
```

**Note:** `fallbackToDestructiveMigration()` means schema version upgrades will wipe data. Proper migration strategies should be implemented before production release.

---

## 3. Backend Infrastructure

### 2.9 Backend Server (Node.js / TypeScript)

> **Non-Technical Reference:** § 3.9 — "Backend Infrastructure"

**Core Files:**

```
backend/
├── src/
│   ├── server.ts          # Express app setup + startup
│   ├── config.ts          # Environment variable loading
│   ├── db.ts              # PostgreSQL connection pool + transactions
│   ├── migrations.ts      # Auto-run table/index/view creation
│   ├── schemas.ts         # Zod validation schemas
│   └── routes/signal.ts   # API route handlers (354 LOC)
├── package.json
├── tsconfig.json
├── .env                   # DB credentials + server config
└── .env.example
```

**Tech Stack:**

| Component | Technology | Purpose |
|---|---|---|
| **Runtime** | Node.js + TypeScript | Server-side JavaScript with type safety |
| **Framework** | Express 4.x | HTTP server + routing |
| **Database** | PostgreSQL 18 + PostGIS | Spatial data storage + geographic queries |
| **Validation** | Zod | Runtime request body validation |
| **Security** | Helmet + CORS + Rate Limiting | HTTP headers, cross-origin policy, abuse prevention |
| **Dev Tools** | nodemon + ts-node | Hot-reload during development |

**API Endpoints:**

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/ingest/signal` | Batch ingest up to 500 measurements per request |
| `GET` | `/api/v1/heatmap` | H3-aggregated signal data for a bounding box |
| `GET` | `/api/v1/stats` | Per-carrier measurement counts and signal averages |
| `GET` | `/api/v1/carriers` | List all carriers with measurement counts |
| `GET` | `/api/v1/hexagon/:h3Index` | Detailed measurements for a specific H3 cell |
| `GET` | `/api/v1/nearby` | PostGIS `ST_DWithin` proximity search |
| `GET` | `/health` | Server + database health check |

**Database Schema (`signal_measurements` table):**
- 26 columns matching the Android Entity schema
- `timestamp` stored as `TIMESTAMPTZ` (converted from Unix ms via `to_timestamp($1 / 1000.0)`)
- `location` column: PostGIS `GEOGRAPHY(POINT, 4326)` auto-populated via trigger from lat/lng
- Indexes: `h3_index`, `h3_index_res9`, `hashed_device_id`, `carrier_name + timestamp`, `batch_id`
- Materialized view: `signal_stats` for pre-aggregated carrier statistics

**Zod Validation (`schemas.ts`):**
- `SignalMeasurementSchema`: Validates all fields with type checking, nullable optionals for signal metrics
- `BatchIngestSchema`: Wraps measurements array (1–500) + `device_id` string
- `network_type`: Flexible string (accepts `5G_SA`, `5G_NSA`, `4G_LTE`, `2G_GSM`, `Unknown`)

---

### 2.10 SyncWorker (Background Data Upload)

> **Non-Technical Reference:** § 3.10 — "Automated Data Sync"

**Core Files:**
- `sync/SyncWorker.kt` (153 LOC) — `@HiltWorker` + `CoroutineWorker`
- `data/remote/SignalApiService.kt` (68 LOC) — Retrofit interface + DTOs
- `di/NetworkModule.kt` (66 LOC) — Hilt module providing OkHttp + Retrofit
- `SignalMapApp.kt` — `Configuration.Provider` for WorkManager + periodic scheduling

**Architecture:**

```
SignalMapApp.kt
  └── WorkManager.enqueueUniquePeriodicWork()
        └── SyncWorker (every 15 min, requires CONNECTED network)
              ├── SignalRepository.getUnsyncedMeasurements() → Room DB
              ├── Entity.toDto() → snake_case DTO mapping
              ├── SignalApiService.ingestSignals(BatchIngestRequest) → Retrofit POST
              ├── On 201: SignalRepository.markAsSynced(ids)
              ├── On 4xx: break (server rejecting → stop retrying)
              ├── On network error: Result.retry() (WorkManager backoff)
              └── SignalRepository.cleanupOldRecords() (synced > 7 days)
```

**DTO Mapping (Entity → API):**

| Entity Field (camelCase) | DTO Field (snake_case) | Notes |
|---|---|---|
| `timestamp` | `timestamp` | Unix epoch ms |
| `carrierName` | `carrier_name` | |
| `networkType` | `network_type` | `4G_LTE`, `5G_SA`, etc. |
| `h3Index` | `h3_index` | H3 resolution 11 |
| `h3IndexRes9` | `h3_index_res9` | H3 resolution 9 |
| `latitude` / `longitude` | `latitude` / `longitude` | |
| `hashedDeviceId` | `hashed_device_id` | SHA-256 hash |
| `rsrp`, `rsrq`, `sinr` | `rsrp`, `rsrq`, `sinr` | LTE metrics |
| `ssRsrp`, `ssSinr` | `ss_rsrp`, `ss_sinr` | 5G NR metrics |
| `pci`, `tac`, `ci` | `pci`, `tac`, `ci` | Cell identity |
| `ci` | `nci` | Reused for NR NCI |
| `subscriptionId` | `sim_slot` | SIM subscription ID |

**NetworkModule Configuration:**

```kotlin
private const val BASE_URL = "http://192.168.0.109:3000"  // Local Wi-Fi IP

OkHttpClient:
  - HttpLoggingInterceptor (Level.BODY)
  - connectTimeout: 30s
  - readTimeout: 30s
  - writeTimeout: 60s (large batch uploads)
  - retryOnConnectionFailure: true
```

**WorkManager Configuration:**

```kotlin
// In SignalMapApp.kt
PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()

// ExistingPeriodicWorkPolicy.KEEP → doesn't restart if already enqueued
```

**AndroidManifest.xml Change:**
```xml
<!-- Disabled default WorkManager auto-init (using custom Configuration.Provider) -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        tools:node="remove" />
</provider>
```

---

## 3. Current Technical Debt & Known Bugs

### 3.1 Resolved: H3 JNI Crash (Critical — Fixed)

- **Issue:** `com.uber:h3:4.1.1` caused `UnsatisfiedLinkError: No native resource found at /android-arm64/libh3-java.so` on all ARM64 Android devices.
- **Root Cause:** The H3 Java library bundles `.so` files only for desktop architectures (x86_64, aarch64 Linux/macOS), not Android ARM64.
- **Fix:** Replaced with `H3Android.kt`, a pure-Kotlin reimplementation of the essential H3 operations. Crash logs archived in `crash_after_fix.txt`.
- **Residual Risk:** The custom H3 implementation is simplified and may produce slightly different cell IDs vs. the official H3 library for edge-case coordinates. Backend must use the same algorithm or a compatible mapping.

### 3.2 Open: Hardcoded Privacy Salt

- **File:** `PrivacyManager.kt`, line 25
- **Issue:** `val salt = "vcm_salt_2026"` — If leaked or reverse-engineered, the hashed device IDs become predictable.
- **Recommendation:** Fetch salt from backend on first launch and store in Android Keystore.

### 3.3 Open: Destructive DB Migration

- **File:** `DatabaseModule.kt`, line 28
- **Issue:** `fallbackToDestructiveMigration()` will drop all local data on schema version bump.
- **Recommendation:** Implement `Migration` objects for each schema version change.

### 3.4 Open: No ProGuard / R8 Configuration

- **File:** `build.gradle.kts`, line 24
- **Issue:** `isMinifyEnabled = false` — No code shrinking, obfuscation, or resource shrinking for release builds.
- **Impact:** Larger APK (~5-10 MB excess), no reverse-engineering protection.

### 3.5 Open: Carrier Filter Not SQL-Level

- **File:** `MapViewModel.kt` — `selectCarrier()` stores the selected carrier but no DAO query filters by carrier.
- **Impact:** Heatmap always shows all carriers regardless of chip selection.

### 3.6 Open: No Error Recovery in Service

- The `SignalCollectionService` does not handle `SecurityException` from permission revocation mid-collection.
- `currentLocation` may be null for an extended period if GPS is slow to acquire, resulting in dropped measurements.

### 3.7 Resolved: Retrofit/OkHttp Now Active

- **Previous status:** Retrofit 2.9.0 and OkHttp 4.12.0 were in the dependency graph but unused.
- **Current status:** ✅ Fully integrated. `NetworkModule.kt` provides `OkHttpClient` → `Retrofit` → `SignalApiService` via Hilt DI. `SyncWorker` uses the service to POST batches to the backend.

---

## 4. Deployment & Infrastructure Status

### 4.1 Build Pipeline

| Step | Method |
|---|---|
| **Build System** | Gradle 8.2.2 with Kotlin DSL (`build.gradle.kts`) |
| **Build Command** | `./gradlew assembleDebug` |
| **Install Command** | `./gradlew installDebug` (USB debugging) |
| **IDE** | Android Studio Ladybug or later |

### 4.2 Current Build Status

- **Debug build:** Compiles and runs on physical ARM64 devices (tested on realme RMX3430, Android 13).
- **Release build:** Not configured (ProGuard disabled, no signing key).
- **No CI/CD pipeline** — All builds are manual via Android Studio or CLI.

### 4.3 Testing

| Type | Status | Details |
|---|---|---|
| **Unit Tests** | ⚠️ Scaffolded only | JUnit 4.13.2 is in dependencies; no test classes exist. |
| **Instrumentation Tests** | ⚠️ Scaffolded only | Espresso 3.5.1 in dependencies; no test classes exist. |
| **Manual Testing** | ✅ Active | Tested on physical device with real SIM cards. Logcat-based verification via `adb logcat -s SignalCollectionService`. |

### 4.4 Infrastructure

| Component | Status |
|---|---|
| **Backend API** | ✅ Running (Node.js/TypeScript + Express on port 3000) |
| **Database** | ✅ PostgreSQL 18 with PostGIS extension |
| **Data Pipeline** | ✅ Phone → SyncWorker → Express API → PostgreSQL (343 measurements synced) |
| **Cloud Deployment** | ❌ Local only (same Wi-Fi required) — Railway/Render planned |
| **CDN / Tile Server** | N/A (uses public OSM Mapnik tiles) |
| **Play Store Listing** | ❌ Not created |
| **Crash Reporting (Firebase Crashlytics)** | ❌ Not integrated |
| **Analytics** | ❌ Not integrated |

### 4.5 Permissions Declared in AndroidManifest.xml

```xml
ACCESS_FINE_LOCATION          <!-- GPS for geo-tagging -->
ACCESS_COARSE_LOCATION        <!-- Fallback location -->
READ_PHONE_STATE              <!-- SIM + signal data -->
FOREGROUND_SERVICE            <!-- Background service -->
FOREGROUND_SERVICE_LOCATION   <!-- Android 10+ service type -->
POST_NOTIFICATIONS            <!-- Android 13+ notification permission -->
INTERNET                      <!-- Map tiles + future API -->
ACCESS_NETWORK_STATE          <!-- Connectivity checks -->
WRITE_EXTERNAL_STORAGE        <!-- osmdroid tile cache (scoped storage exempt) -->
```

---

*End of Technical Report*
