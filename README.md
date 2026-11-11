# FusionLite — minimal FusionSolar client

Replacement Android app (Kotlin + Jetpack Compose) for the Huawei FusionSolar
Android app (`com.huawei.smartpvms`), implementing: login, real-time PV
production, battery SOC and energy flow (home consumption).

## Reverse-engineered API map (source: REA + JADX 0.7.1 decompilation of the APK)

| Purpose | Endpoint | Evidence |
|---|---|---|
| Login (OAuth-style token) | `POST /rest/neteco/appauthen/v1/smapp/app/token` | `UrlConstant.URL_LOGIN` → `RequestManager.login` |
| Station list (production + SOC) | `POST /rest/pvms/web/station/v1/station/station-list` | `UrlConstant.URL_HOME_STATION_LIST` |
| Total real-time KPI | `POST /rest/pvms/web/station/v1/station/total-real-kpi` | `URL_HOME_STATION_TOTAL_KPI` |
| Per-device real KPI | `POST /rest/pvms/web/device/v1/device-real-kpi` | `URL_DEVICE_REAL_KPI` |
| Energy flow (PV/grid/battery/home) | `GET /rest/pvms/web/station/v1/overview/energy-flow?stationDn=…` | `URL_STATION_ENERGY_FLOW` + `RequestApi.queryStationEnergyFlow` |
| Energy flow v2 / v3 fallback | `GET /rest/pvms/web/station/v{2,3}/overview/energy-flow?stationDn=…` | `URL_STATION_ENERGY_FLOW_V2/V3` |
| Battery cluster detail | `POST /rest/neteco/bms/v1/energy-storage/battery-cluster-data` | `URL_BATTERY_CLUSTER_DATA` |
| Logout | `POST /rest/pvms/web/appuser/v1/logout` | `URL_APP_USER_LOGOUT` |
| Real KPI (day/month/year/lifetime) | `POST /rest/pvms/web/station/v1/overview/station-real-kpi` | `URL_STATION_REAL_KPI` → `RequestApi.queryOneStationRealKpi` |
| Historical energy series | `POST /rest/pvms/web/station/v1/overview/energy-balance` | `URL_STATION_ENERGY_BALANCE` → `RequestApi.queryStationEnergyBalance` |
| Historical energy series v2 | `POST /rest/pvms/web/station/v2/overview/energy-balance` | `URL_STATION_ENERGY_BALANCE_V2` |
| Income/earnings chart | `POST /rest/pvms/web/report/v1/station/home-station-kpi-chart` | `URL_STATION_HOME_STATION_KPI_CHART` → `RequestApi.queryStationDetailRealKpiChart` |

Default server (`assets/app_config.properties`): `intl.fusionsolar.huawei.com:32800`
(`https://`, port constant `DEFAULT_PORT=32800` in `IpAddressUtil`).

### Login payload — `LoginConditionArg`
```json
{ "userName": "…", "value": "<password>", "grantType": "password",
  "appClientId": "<android-id>", "isNeedTwoVerify": false,
  "verifyCode": "", "isRenew": true, "refreshToken": null }
```
Response `BaseEntityBo<LoginBo>` → `data.accessToken`, `data.refreshToken`,
`data.roaRand` (CSRF), `data.regionList` (regional server IPs).

### Silent renewal (shipped mechanism)

`SessionOutInterceptor` does **not** use the password to recover. An expired
session is signalled by a `302` whose `Location` contains `/unisess/v1/logout`
or `/unisess/v1/auth` (or a `401/403`). The app then posts
`{grantType:"refreshtoken", orgName:"default", userName, appClientId,
refreshToken}` to the **current data host** + `URL_LOGIN` and replaces
`accessToken`/`roaRand` (and `refreshToken` when a new one is issued).

FusionLite follows the same order (`data/FusionRepository.renewWithRefreshToken`
→ password login as fallback), and keeps the `302` visible by disabling OkHttp
redirects so it is classified as auth-expired instead of becoming an HTML/JSON
parse error. Both tokens are persisted; only explicit sign-out clears them.

### Session headers (`NetConfigConstant.getHeaders` + `iph`)
```
Cookie: locale=<lang>;data-theme=<light|dark>;bspsession=<accessToken>;dp-session=<accessToken>
csrf-token: <roaRand>
roarand: <roaRand>
x-timezone-offset: <+HHmm>
Content-Type: application/json
```

### Response envelope — `BaseBeanBo` / `BaseEntityBo`
`{ failCode, failMsg, data, … }`; paged lists use `data.list`, `data.total`.

### Key response fields
- `StationListItemBo`: `dn`, `name`, `currentPower`, `dailyEnergy`,
  `cumulativeEnergy`, `batterySoc`, `installedCapacity`, `status`, `plantStatus`.
- `StationEnergyFlowBo.flow.nodes[].description.{label,value}` — labelled
  live values for each node (PV, grid, battery, home load).

### Statistics contract (REA evidence)

Filters reuse the shipped app's `StationKpiChartArgBean.StatDim` dex codes:
`DAY="2"`, `MONTH="4"`, `YEAR="5"`, `ALL="6"` (lifetime).

`NormalPowerFragment.queryKpiManage` / `qv2.k` build the energy-balance body:

```json
{ "stationDn": "NE=…", "timeDim": "2|4|5|6",
  "dateStr": "yyyy-MM-dd 00:00:00 | yyyy-MM-01 00:00:00 | yyyy-01-01 00:00:00",
  "queryTime": 1699920000000, "timeZone": "8", "timeZoneStr": "…", "_": 1699920000000 }
```

- `dateStr` formatting comes from `jqn.a` (day → the date, month → first day,
  year/ALL → Jan 1), `queryTime` from `qv2.j` (midnight local), and `timeZone`
  from `brn.c` (east positive without a `+`, west with `-`).
- `energy-balance` returns `BaseBeanBo<StationEnergyManageBean>`: `xAxis` plus
  parallel series `productPower`, `usePower`, `onGridPower`, `buyPower`,
  `selfUsePower`, `chargePower`, `dischargePower`, and server totals
  `totalProductPower`, `totalOnGridPower`, ….
- Units follow `PhotovoltaicStatisticsViewModel.getLeftUnit`: the DAY series is
  instantaneous **power (kW)** sampled across the day, while MONTH/YEAR/ALL
  buckets are **energy (kWh)**; the day's kWh headline comes from
  `station-real-kpi.dailyEnergy`.
- `station-real-kpi` returns `BaseBeanBo<StationRealKpiBean>` with
  `dailyEnergy`, `monthEnergy`, `yearEnergy`, `cumulativeEnergy`,
  `dailyIncome`, `currency`.
- The lifetime (`ALL`) window sends `timeDim=6` with Jan 1 of the selected
  year, mirroring `PhotovoltaicStatisticsViewModel.updateSelectValue`; the
  exact server bucketing for ALL is inferred (no runtime capture) — the
  breakdown/total cards remain correct because they prefer the server totals.
- Some regional gateways answer the POST form with **HTTP 405**. FusionLite
  retries the same fields as a `GET` query against v1 and v2, and reports every
  attempt (`v1 POST: …; v2 POST: HTTP 405 (Allow: GET); …`) instead of only the
  last version's error. `station-real-kpi` gets the same POST→GET fallback (and
  the working transport is remembered).

## Local persistence

`data/SessionStore.kt` is a single-row SQLite database (`fusionlite.db`) that keeps
the access token, CSRF random, regional data host, selected plant and a UUID-shaped
`appClientId`, so reopening the app goes straight to the dashboard. The username is
kept and the password is encrypted with a non-exportable Android Keystore AES/GCM
key so an expired token can be renewed silently. Only an explicit sign-out clears it.

## UI / refresh

The dashboard is an animated solar scene: panel shimmer, particle count and the
overall tempo (sun rays, specular sweep, panel→house flow) scale with live
production — from a slow idle at zero output up to ~2x speed at full capacity —
while `EnergyFlowDiagram` shows PV → home/battery/grid routing. Auto-refresh runs every 15/30/60 s (selectable), on app
resume, and on manual pull of the refresh glyph.

The Analytics production curve has an expand glyph (Curve mode) that opens a
full-screen landscape chart (`FullscreenChartScreen`): system bars hidden, chart
filling the display, CLOSE or Back restores portrait/unspecified orientation.

Rotation no longer resets navigation: `MainActivity` declares
`orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden` as
handled configuration changes, so Compose keeps `UiState` (including the
current tab) instead of recreating the activity.

A **Costs** destination (`ui/CostsScreen.kt`) turns the historical energy series
into money: enter a purchase price and an export sell price in cents per kWh,
pick Day/Month/Year/Total, and the page shows consumption cost, grid-import
cost, export revenue, self-consumption savings and the net bill. Prices are
stored locally (`data/Tariffs.kt`, SharedPreferences) and are never uploaded.

A liquid bottom navigation bar (`ui/LiquidNavBar.kt`) now floats over the shell
with four destinations: Home, Analytics, Costs and Account. Its selected pill is a
spring-driven, bezier-wobbling blob that stretches/squashes while travelling,
throws two satellite droplets and blends its gradient between SolarGold,
SolarCyan and SolarGreen.

The **Analytics** tab (`ui/StatisticsScreen.kt`, `ui/StatisticsCharts.kt`) is a
custom statistics dashboard:

- Day / Month / Year / Total period pill with ‹ › window navigation.
- Hero production total, peak, server total and three mini KPIs.
- Hand-drawn chart modes: gradient **Curve** (tap a bucket to inspect it),
  grouped **Bars** (production vs consumption) and **Mix** (animated energy
  donut with self-use/export/charge/import segments).
- Breakdown bars for PV, load, export, import and battery flow, plus highlight
  tiles (best bucket, average, battery cycled, self-sufficiency) and the
  day/month/year/lifetime values from `station-real-kpi`.

### Energy-flow semantics

`FlowMetrics.allocation()` derives a fully signed balance from the live nodes:
the grid direction comes from `PV + battery discharge − load − battery charge`, so
"importing" vs "exporting" is unambiguous even when the API's grid sign differs
between hosts. It also auto-detects the battery sign convention by matching the
balance against the reported grid magnitude. The diagram then labels every active
edge with the kW travelling on it (solar→home, solar→battery, solar→grid,
grid→home, battery→home, battery→grid) and adds a grid summary plus a
self-consumption bar.

## Build
```
./gradlew :app:assembleDebug
```
Requires Android SDK 35 / AGP 8.7.2. App targets minSdk 26.

## Limitations / unknowns (from static evidence only)
- Two-factor (`isNeedTwoVerify` + `twoFactorAuthenticationCode` via
  `/rest/neteco/appauthen/v1/smapp/app/twofactorauthentication`) is supported
  server-side but not implemented in FusionLite.
- Password is sent TLS-protected as-is (`value` = plaintext over HTTPS);
  no app-side password crypto was found in the login path.
- `appClientId` derives from the device; Huawei may rate-limit or reject
  unknown client IDs.
- Static decompilation does not establish runtime behavior; a live traffic
  capture is the only way to confirm exact server responses for your account.
