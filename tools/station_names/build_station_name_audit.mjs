import fs from "node:fs/promises";
import { createReadStream } from "node:fs";
import path from "node:path";
import readline from "node:readline";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const ROOT = path.resolve(import.meta.dirname, "..", "..");
const STATIONS_PATH = path.join(ROOT, "app", "src", "main", "res", "raw", "jma_intensity_stations.json");
const DAABR_PATH = path.join(ROOT, "tools", "source", "mt_town_all.csv");
const OUTPUT_DIR = path.join(ROOT, "outputs", "station-name-audit");
const CACHE_PATH = path.join(import.meta.dirname, "gsi_reverse_geocoder_cache.json");
const PROVIDER_CACHE_PATH = path.join(import.meta.dirname, "official_station_metadata_cache.json");
const OUTPUT_PATH = path.join(OUTPUT_DIR, "ambiguous_station_name_audit.xlsx");
const PREVIEW_PATH = path.join(OUTPUT_DIR, "ambiguous_station_name_audit_preview.png");
const MAPPING_PREVIEW_PATH = path.join(OUTPUT_DIR, "ambiguous_station_name_mapping_preview.png");
const CANDIDATES_PREVIEW_PATH = path.join(OUTPUT_DIR, "ambiguous_station_candidates_preview.png");
const RESEARCH_PREVIEW_PATH = path.join(OUTPUT_DIR, "ambiguous_station_research_preview.png");
const GSI_ENDPOINT = "https://mreversegeocoder.gsi.go.jp/reverse-geocoder/LonLatToAddress";
const GSI_INFO_URL = "https://www.gsi.go.jp/";
const JMA_STATION_URL = "https://www.data.jma.go.jp/eqev/data/kyoshin/jma-shindo.html";
const NIED_STATION_URL = "https://www.kyoshin.bosai.go.jp/ja/stationlist/";
const NIED_STATION_API_URL = "https://www.kyoshin.bosai.go.jp/ja/stationlist/api/";

const MANUAL_CONFIRMATIONS = new Map([
  [
    "4320231",
    {
      localityJa: "鏡町内田",
      localityEn: "Kagamimachi Uchida",
      confidence: "Address confirmed",
      ready: "Yes",
      evidenceUrl: "https://www.town.mashiki.lg.jp/bousai/kiji0036632/3_6632_19153_up_cu4d7aru.pdf",
      note: "Official seismic-observation facility listing identifies 鏡町内田453-1 鏡支所; Yatsushiro City independently gives the same branch-office address.",
    },
  ],
]);

function parseCsvLine(line) {
  const values = [];
  let value = "";
  let quoted = false;
  for (let i = 0; i < line.length; i += 1) {
    const char = line[i];
    if (char === '"') {
      if (quoted && line[i + 1] === '"') {
        value += '"';
        i += 1;
      } else {
        quoted = !quoted;
      }
    } else if (char === "," && !quoted) {
      values.push(value);
      value = "";
    } else {
      value += char;
    }
  }
  values.push(value);
  return values;
}

function parseKanjiNumber(value) {
  const digits = { 零: 0, 〇: 0, 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 };
  const units = { 十: 10, 百: 100, 千: 1000 };
  if (![...value].some((char) => units[char])) {
    return [...value].map((char) => digits[char]).join("");
  }
  let total = 0;
  let pending = 0;
  for (const char of value) {
    if (char in digits) {
      pending = digits[char];
    } else if (char in units) {
      total += (pending || 1) * units[char];
      pending = 0;
    }
  }
  return String(total + pending);
}

function normalize(value) {
  return String(value ?? "")
    .normalize("NFKC")
    .replace(/[零〇一二三四五六七八九十百千]+/g, parseKanjiNumber)
    .replace(/[\s　]/g, "")
    .replace(/^大字/, "")
    .replace(/^字/, "")
    .trim();
}

function deriveLocalPart(stationName, admin) {
  const name = normalize(stationName);
  const tokens = [admin?.ward, admin?.city]
    .map(normalize)
    .filter(Boolean);
  let bestEnd = -1;
  for (const token of tokens) {
    const index = name.lastIndexOf(token);
    if (index >= 0) bestEnd = Math.max(bestEnd, index + token.length);
  }
  return bestEnd >= 0 ? name.slice(bestEnd) : name;
}

function columnIndexes(header) {
  return Object.fromEntries(header.map((name, index) => [name, index]));
}

async function loadInputs() {
  const stationRoot = JSON.parse(await fs.readFile(STATIONS_PATH, "utf8"));
  const stations = stationRoot.stations.map((row) => ({
    code: String(row[0]),
    nameJa: String(row[1]),
    prefectureJa: String(row[2]),
    latitudeText: String(row[3]),
    longitudeText: String(row[4]),
    latitude: Number(row[3]),
    longitude: Number(row[4]),
    providerJa: String(row[5]),
    areaCode: String(row[6]),
    areaNameJa: String(row[7]),
    municipalityCode: String(row[8]),
    municipalityKey: String(row[8]).slice(0, 5),
  }));

  const municipalityKeys = new Set(stations.map((station) => station.municipalityKey));
  const admins = new Map();
  const places = new Map();
  const stream = createReadStream(DAABR_PATH, { encoding: "utf8" });
  const lines = readline.createInterface({ input: stream, crlfDelay: Infinity });
  let indexes;

  for await (const line of lines) {
    const row = parseCsvLine(line.replace(/^\uFEFF/, ""));
    if (!indexes) {
      indexes = columnIndexes(row);
      continue;
    }
    if (row[indexes.status_flg] !== "1") continue;
    const lgCode = row[indexes.lg_code];
    const municipalityKey = lgCode.slice(0, 5);
    if (!municipalityKeys.has(municipalityKey)) continue;

    if (!admins.has(municipalityKey)) {
      admins.set(municipalityKey, {
        lgCode,
        prefecture: row[indexes.pref],
        city: row[indexes.city],
        ward: row[indexes.ward],
      });
    }

    const localityJa = row[indexes.oaza_cho];
    if (!localityJa) continue;
    if (!places.has(municipalityKey)) places.set(municipalityKey, new Map());
    const municipalityPlaces = places.get(municipalityKey);
    const localityKey = normalize(localityJa);
    const existing = municipalityPlaces.get(localityKey);
    const candidate = {
      localityJa,
      localityKana: row[indexes.oaza_cho_kana],
      localityEn: row[indexes.oaza_cho_roma],
      lgCode,
      machiazaId: row[indexes.machiaza_id],
    };
    if (!existing || (!existing.localityEn && candidate.localityEn)) {
      municipalityPlaces.set(localityKey, candidate);
    }
  }

  return { stations, admins, places };
}

function buildAmbiguousRows({ stations, admins, places }) {
  const rows = [];
  for (const station of stations) {
    const admin = admins.get(station.municipalityKey);
    const localPart = deriveLocalPart(station.nameJa, admin);
    const localKey = normalize(localPart);
    if (!localKey) continue;
    const municipalityPlaces = places.get(station.municipalityKey);
    if (!municipalityPlaces) continue;
    const allPlaces = [...municipalityPlaces.values()];
    const exactCandidates = allPlaces.filter(
      (candidate) => normalize(candidate.localityJa) === localKey,
    );
    const candidates = (exactCandidates.length
      ? exactCandidates
      : allPlaces.filter((candidate) => normalize(candidate.localityJa).startsWith(localKey)))
      .sort((a, b) => a.localityJa.localeCompare(b.localityJa, "ja"));
    if (candidates.length <= 1) continue;
    rows.push({ ...station, admin, localPart, candidates });
  }
  return rows;
}

async function loadCache() {
  try {
    return JSON.parse(await fs.readFile(CACHE_PATH, "utf8"));
  } catch {
    return {};
  }
}

async function saveCache(cache) {
  const ordered = Object.fromEntries(Object.entries(cache).sort(([a], [b]) => a.localeCompare(b)));
  await fs.writeFile(CACHE_PATH, `${JSON.stringify(ordered, null, 2)}\n`, "utf8");
}

function decodeHtml(value) {
  return String(value ?? "")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;|&#160;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)))
    .replace(/\s+/g, " ")
    .trim();
}

async function loadProviderCache() {
  try {
    return JSON.parse(await fs.readFile(PROVIDER_CACHE_PATH, "utf8"));
  } catch {
    return {};
  }
}

async function saveProviderCache(cache) {
  await fs.writeFile(PROVIDER_CACHE_PATH, `${JSON.stringify(cache, null, 2)}\n`, "utf8");
}

async function fetchJmaAddresses(providerCache) {
  try {
    const response = await fetch(JMA_STATION_URL, { headers: { "User-Agent": "QuakeDeck station-name audit" } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const html = await response.text();
    const current = {};
    for (const rowMatch of html.matchAll(/<tr[^>]*>([\s\S]*?)<\/tr>/gi)) {
      const cells = [...rowMatch[1].matchAll(/<td[^>]*>([\s\S]*?)<\/td>/gi)]
        .map((match) => decodeHtml(match[1]));
      if (cells.length < 9 || !cells[1] || !cells[2]) continue;
      const endDate = cells[8];
      if (!endDate) current[cells[1]] = cells[2];
    }
    providerCache.jma = {
      fetchedAt: new Date().toISOString(),
      sourceUrl: JMA_STATION_URL,
      addresses: current,
    };
    await saveProviderCache(providerCache);
  } catch (error) {
    if (!providerCache.jma?.addresses) {
      providerCache.jma = { sourceUrl: JMA_STATION_URL, addresses: {}, error: String(error?.message ?? error) };
    }
  }
  return providerCache.jma?.addresses ?? {};
}

function csrfCookieFromHeaders(headers) {
  const setCookie = headers.get("set-cookie") ?? "";
  const match = setCookie.match(/csrftoken=([^;]+)/i);
  return match ? `csrftoken=${match[1]}` : "";
}

async function fetchNiedStations(providerCache) {
  try {
    const pageResponse = await fetch(NIED_STATION_URL, { headers: { "User-Agent": "QuakeDeck station-name audit" } });
    if (!pageResponse.ok) throw new Error(`Station page HTTP ${pageResponse.status}`);
    const html = await pageResponse.text();
    const token = html.match(/name="csrfmiddlewaretoken"\s+value="([^"]+)"/i)?.[1] ?? "";
    const cookie = csrfCookieFromHeaders(pageResponse.headers);
    if (!token || !cookie) throw new Error("NIED CSRF token or cookie missing");
    const form = new URLSearchParams({ csrfmiddlewaretoken: token, datakind: "3" });
    const apiResponse = await fetch(NIED_STATION_API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Cookie": cookie,
        "Referer": NIED_STATION_URL,
        "X-CSRFToken": token,
        "User-Agent": "QuakeDeck station-name audit",
      },
      body: form,
    });
    if (!apiResponse.ok) throw new Error(`Station API HTTP ${apiResponse.status}`);
    const payload = await apiResponse.json();
    const items = (payload.items ?? []).map((item) => ({
      network: String(item.data_type_name ?? ""),
      siteCode: String(item.sitecode ?? ""),
      nameJa: String(item.sitename_j ?? ""),
      nameEn: String(item.sitename_e ?? ""),
      latitude: Number(item.lat_jgd),
      longitude: Number(item.lon_jgd),
      prefectureJa: String(item.prefname ?? ""),
    })).filter((item) => item.siteCode && Number.isFinite(item.latitude) && Number.isFinite(item.longitude));
    providerCache.nied = {
      fetchedAt: new Date().toISOString(),
      sourceUrl: NIED_STATION_URL,
      items,
    };
    await saveProviderCache(providerCache);
  } catch (error) {
    if (!providerCache.nied?.items) {
      providerCache.nied = { sourceUrl: NIED_STATION_URL, items: [], error: String(error?.message ?? error) };
    }
  }
  return providerCache.nied?.items ?? [];
}

function haversineKm(latitude1, longitude1, latitude2, longitude2) {
  const radians = (degrees) => degrees * Math.PI / 180;
  const earthRadiusKm = 6371.0088;
  const deltaLatitude = radians(latitude2 - latitude1);
  const deltaLongitude = radians(longitude2 - longitude1);
  const a = Math.sin(deltaLatitude / 2) ** 2
    + Math.cos(radians(latitude1)) * Math.cos(radians(latitude2)) * Math.sin(deltaLongitude / 2) ** 2;
  return 2 * earthRadiusKm * Math.asin(Math.sqrt(a));
}

function matchNiedStation(row, niedStations) {
  const ranked = niedStations
    .filter((item) => item.prefectureJa === row.prefectureJa)
    .map((item) => ({
      ...item,
      distanceKm: haversineKm(row.latitude, row.longitude, item.latitude, item.longitude),
    }))
    .sort((a, b) => a.distanceKm - b.distanceKm);
  const nearest = ranked[0];
  const second = ranked[1];
  if (!nearest || nearest.distanceKm > 1.5) return null;
  const nameCompatible = normalize(row.localPart).includes(normalize(nearest.nameJa))
    || normalize(nearest.nameJa).includes(normalize(row.localPart));
  const clearlyNearest = !second || second.distanceKm - nearest.distanceKm >= 0.15;
  return nameCompatible || clearlyNearest ? nearest : null;
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function fetchReverseGeocode(cache, cacheKey, longitudeText, latitudeText) {
  if (cache[cacheKey]) return false;
    const url = new URL(GSI_ENDPOINT);
  url.searchParams.set("lon", longitudeText);
  url.searchParams.set("lat", latitudeText);
    let result;
    try {
      const response = await fetch(url, { headers: { "User-Agent": "QuakeDeck station-name audit" } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const payload = await response.json();
      result = {
        municipalityCode: String(payload?.results?.muniCd ?? ""),
        localityJa: String(payload?.results?.lv01Nm ?? ""),
        url: url.toString(),
        error: "",
      };
    } catch (error) {
      result = {
        municipalityCode: "",
        localityJa: "",
        url: url.toString(),
        error: String(error?.message ?? error),
      };
    }
  cache[cacheKey] = result;
  return true;
}

async function reverseGeocode(rows, niedStations) {
  const cache = await loadCache();
  let fetched = 0;
  for (const row of rows) {
    if (await fetchReverseGeocode(cache, row.code, row.longitudeText, row.latitudeText)) {
      fetched += 1;
      if (fetched % 25 === 0) await saveCache(cache);
      await wait(175);
    }
    if (row.providerJa === "防災科学技術研究所") {
      const niedMatch = matchNiedStation(row, niedStations);
      if (niedMatch && await fetchReverseGeocode(
        cache,
        `nied:${row.code}`,
        String(niedMatch.longitude),
        String(niedMatch.latitude),
      )) {
        fetched += 1;
        if (fetched % 25 === 0) await saveCache(cache);
        await wait(175);
      }
    }
  }
  await saveCache(cache);
  return cache;
}

const ENVELOPE_OFFSETS = [
  [-0.0075, -0.0075], [-0.0075, 0], [-0.0075, 0.0075],
  [0, -0.0075],                         [0, 0.0075],
  [0.0075, -0.0075],  [0.0075, 0],  [0.0075, 0.0075],
];

async function reverseGeocodeEnvelopes(rows, cache) {
  let fetched = 0;
  for (const row of rows) {
    for (let index = 0; index < ENVELOPE_OFFSETS.length; index += 1) {
      const [latitudeOffset, longitudeOffset] = ENVELOPE_OFFSETS[index];
      const latitude = (row.latitude + latitudeOffset).toFixed(6);
      const longitude = (row.longitude + longitudeOffset).toFixed(6);
      if (await fetchReverseGeocode(cache, `env:${row.code}:${index}`, longitude, latitude)) {
        fetched += 1;
        if (fetched % 25 === 0) await saveCache(cache);
        await wait(175);
      }
    }
  }
  await saveCache(cache);
  return cache;
}

function envelopeGeocodes(row, geocodes) {
  return ENVELOPE_OFFSETS.map((_, index) => geocodes[`env:${row.code}:${index}`]).filter(Boolean);
}

function candidateFromOfficialText(row, officialText) {
  const textKey = normalize(officialText);
  const matches = row.candidates.filter((candidate) => textKey.includes(normalize(candidate.localityJa)));
  return matches.length === 1 ? matches[0] : null;
}

function resolveGeocodeCandidate(row, geocode, context = {}) {
  const {
    matchedConfidence = "Coordinate matched",
    matchedMethod = "GSI coordinate + DAABR romanization",
    matchedEvidenceUrl = geocode?.url ?? GSI_INFO_URL,
    matchedNote = "Official GSI reverse-geocoder selects one DAABR candidate; station coordinates may be rounded.",
  } = context;
  if (!geocode || geocode.error || (!geocode.municipalityCode && !geocode.localityJa)) {
    return {
      selected: null,
      localityEn: "",
      confidence: "Unresolved",
      ready: "No",
      evidenceUrl: geocode?.url ?? matchedEvidenceUrl,
      note: geocode?.error ? `GSI lookup failed: ${geocode.error}` : "No GSI locality result.",
      method: "No coordinate result",
    };
  }

  if (geocode.municipalityCode !== row.municipalityKey) {
    return {
      selected: null,
      localityEn: "",
      confidence: "Unresolved",
      ready: "No",
      evidenceUrl: matchedEvidenceUrl,
      note: `GSI municipality ${geocode.municipalityCode} differs from station municipality ${row.municipalityKey}.`,
      method: "Municipality mismatch",
    };
  }

  const gsiKey = normalize(geocode.localityJa);
  const exact = row.candidates.filter((candidate) => normalize(candidate.localityJa) === gsiKey);
  const compatible = exact.length
    ? exact
    : row.candidates.filter((candidate) => {
        const candidateKey = normalize(candidate.localityJa);
        return gsiKey.startsWith(candidateKey) || candidateKey.startsWith(gsiKey);
      });

  if (compatible.length !== 1) {
    return {
      selected: null,
      localityEn: "",
      confidence: "Unresolved",
      ready: "No",
      evidenceUrl: matchedEvidenceUrl,
      note: compatible.length === 0
        ? "GSI locality is not one of the DAABR candidates."
        : "GSI locality still matches multiple DAABR candidates.",
      method: "Coordinate mismatch",
    };
  }

  const selected = compatible[0];
  if (!selected.localityEn) {
    return {
      selected,
      localityEn: "",
      confidence: "Romanization missing",
      ready: "No",
      evidenceUrl: matchedEvidenceUrl,
      note: "Coordinate resolves the locality, but DAABR has no official romanization for it.",
      method: matchedMethod.replace("romanization", "locality"),
    };
  }

  return {
    selected,
    localityEn: selected.localityEn,
    confidence: matchedConfidence,
    ready: "Review",
    evidenceUrl: matchedEvidenceUrl,
    note: matchedNote,
    method: matchedMethod,
  };
}

function resolveEnvelopeCandidate(row, samples) {
  const hits = [];
  for (const sample of samples) {
    if (sample.error || sample.municipalityCode !== row.municipalityKey || !sample.localityJa) continue;
    const sampleKey = normalize(sample.localityJa);
    const compatible = row.candidates.filter((candidate) => {
      const candidateKey = normalize(candidate.localityJa);
      return sampleKey.startsWith(candidateKey) || candidateKey.startsWith(sampleKey);
    });
    if (compatible.length === 1) hits.push({ candidate: compatible[0], sample });
  }
  const unique = new Map(hits.map((hit) => [normalize(hit.candidate.localityJa), hit]));
  if (unique.size !== 1) return null;
  const { candidate, sample } = [...unique.values()][0];
  return {
    selected: candidate,
    localityEn: candidate.localityEn,
    confidence: candidate.localityEn ? "Coordinate envelope matched" : "Romanization missing",
    ready: candidate.localityEn ? "Review" : "No",
    evidenceUrl: sample.url,
    note: `${hits.length} of ${samples.length} uncertainty-envelope samples hit one DAABR candidate and none hit another; source local-government coordinates are minute-scale approximations.`,
    method: candidate.localityEn
      ? "GSI coordinate envelope + DAABR romanization"
      : "GSI coordinate envelope + DAABR locality",
  };
}

function resolveCandidate(row, geocode, jmaAddress, niedMatch, niedGeocode, envelopeSamples = []) {
  const manual = MANUAL_CONFIRMATIONS.get(row.code);
  if (manual) {
    const candidate = row.candidates.find(
      (item) => normalize(item.localityJa) === normalize(manual.localityJa),
    );
    return {
      selected: candidate ?? { localityJa: manual.localityJa, localityEn: manual.localityEn },
      localityEn: manual.localityEn,
      confidence: manual.confidence,
      ready: manual.ready,
      evidenceUrl: manual.evidenceUrl,
      note: manual.note,
      method: "Official installation address",
    };
  }

  if (jmaAddress) {
    const candidate = candidateFromOfficialText(row, jmaAddress);
    if (candidate) {
      return {
        selected: candidate,
        localityEn: candidate.localityEn,
        confidence: candidate.localityEn ? "Address confirmed" : "Romanization missing",
        ready: candidate.localityEn ? "Yes" : "No",
        evidenceUrl: JMA_STATION_URL,
        note: `JMA installation address: ${jmaAddress}`,
        method: "Official JMA installation address",
      };
    }
  }

  if (niedMatch && niedGeocode) {
    const precise = resolveGeocodeCandidate(row, niedGeocode, {
      matchedConfidence: "Provider coordinate matched",
      matchedMethod: "NIED coordinate + GSI + DAABR romanization",
      matchedEvidenceUrl: NIED_STATION_URL,
      matchedNote: `Matched NIED ${niedMatch.network} station ${niedMatch.siteCode} ${niedMatch.nameJa} (${niedMatch.nameEn}) at ${niedMatch.latitude}, ${niedMatch.longitude}; ${niedMatch.distanceKm.toFixed(3)} km from the rounded catalogue coordinate.`,
    });
    if (precise.selected) return precise;
  }

  const sourceResolution = resolveGeocodeCandidate(row, geocode);
  if (sourceResolution.selected) return sourceResolution;
  return resolveEnvelopeCandidate(row, envelopeSamples) ?? sourceResolution;
}

function providerEnglish(providerJa) {
  return {
    気象庁: "JMA",
    防災科学技術研究所: "NIED",
    地方公共団体: "Local government",
  }[providerJa] ?? providerJa;
}

function countBy(rows, selector) {
  const counts = new Map();
  for (const row of rows) {
    const key = selector(row);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0])));
}

async function buildWorkbook(rows, geocodes, jmaAddresses, niedStations) {
  const resolvedRows = rows.map((row) => {
    const geocode = geocodes[row.code];
    const jmaAddress = row.providerJa === "気象庁" ? jmaAddresses[row.nameJa] : "";
    const niedMatch = row.providerJa === "防災科学技術研究所" ? matchNiedStation(row, niedStations) : null;
    const niedGeocode = niedMatch ? geocodes[`nied:${row.code}`] : null;
    const resolution = resolveCandidate(
      row,
      geocode,
      jmaAddress,
      niedMatch,
      niedGeocode,
      envelopeGeocodes(row, geocodes),
    );
    return { row, geocode, resolution, jmaAddress, niedMatch, niedGeocode };
  });

  const workbook = Workbook.create();
  const summary = workbook.worksheets.add("Summary");
  const mapping = workbook.worksheets.add("Proposed Mapping");
  const candidates = workbook.worksheets.add("DAABR Candidates");
  const unresolved = workbook.worksheets.add("Needs Research");

  const mappingHeaders = [
    "Station code", "Japanese station name", "Prefecture", "Provider", "Latitude", "Longitude",
    "Municipality code", "Short source label", "Candidate count", "GSI locality", "Selected DAABR locality",
    "Official English locality", "Method", "Confidence", "Ready for app", "Evidence URL", "Notes",
  ];
  const mappingValues = resolvedRows.map(({ row, geocode, resolution }) => [
    Number(row.code),
    row.nameJa,
    row.prefectureJa,
    providerEnglish(row.providerJa),
    row.latitude,
    row.longitude,
    Number(row.municipalityCode),
    row.localPart,
    row.candidates.length,
    geocode?.localityJa ?? "",
    resolution.selected?.localityJa ?? "",
    resolution.localityEn,
    resolution.method,
    resolution.confidence,
    resolution.ready,
    resolution.evidenceUrl,
    resolution.note,
  ]);

  mapping.getRangeByIndexes(0, 0, 1, mappingHeaders.length).values = [mappingHeaders];
  if (mappingValues.length) {
    mapping.getRangeByIndexes(1, 0, mappingValues.length, mappingHeaders.length).values = mappingValues;
  }
  mapping.tables.add(`A1:Q${mappingValues.length + 1}`, true, "ProposedMappingTable");
  mapping.freezePanes.freezeRows(1);
  mapping.freezePanes.freezeColumns(2);
  mapping.showGridLines = false;

  const candidateHeaders = [
    "Station code", "Japanese station name", "Short source label", "Candidate rank", "DAABR locality",
    "DAABR kana", "DAABR official English", "DAABR municipality code", "Machiaza ID", "Selected",
  ];
  const candidateValues = [];
  for (const { row, resolution } of resolvedRows) {
    row.candidates.forEach((candidate, index) => {
      candidateValues.push([
        Number(row.code),
        row.nameJa,
        row.localPart,
        index + 1,
        candidate.localityJa,
        candidate.localityKana,
        candidate.localityEn,
        Number(candidate.lgCode),
        Number(candidate.machiazaId),
        normalize(candidate.localityJa) === normalize(resolution.selected?.localityJa) ? "Yes" : "",
      ]);
    });
  }
  candidates.getRangeByIndexes(0, 0, 1, candidateHeaders.length).values = [candidateHeaders];
  if (candidateValues.length) {
    candidates.getRangeByIndexes(1, 0, candidateValues.length, candidateHeaders.length).values = candidateValues;
  }
  candidates.tables.add(`A1:J${candidateValues.length + 1}`, true, "DaabrCandidatesTable");
  candidates.freezePanes.freezeRows(1);
  candidates.freezePanes.freezeColumns(2);
  candidates.showGridLines = false;

  const unresolvedRows = mappingValues.filter((values) => values[14] !== "Yes" && values[14] !== "Review");
  unresolved.getRangeByIndexes(0, 0, 1, mappingHeaders.length).values = [mappingHeaders];
  if (unresolvedRows.length) {
    unresolved.getRangeByIndexes(1, 0, unresolvedRows.length, mappingHeaders.length).values = unresolvedRows;
  }
  unresolved.tables.add(`A1:Q${unresolvedRows.length + 1}`, true, "NeedsResearchTable");
  unresolved.freezePanes.freezeRows(1);
  unresolved.freezePanes.freezeColumns(2);
  unresolved.showGridLines = false;

  const confidenceCounts = countBy(resolvedRows, ({ resolution }) => resolution.confidence);
  const providerCounts = countBy(resolvedRows, ({ row }) => providerEnglish(row.providerJa));
  summary.getRange("A1:H1").merge();
  summary.getRange("A1").values = [["QuakeDeck ambiguous station-name audit"]];
  summary.getRange("A3:B8").values = [
    ["Metric", "Count"],
    ["Ambiguous source labels", resolvedRows.length],
    ["Address confirmed", resolvedRows.filter(({ resolution }) => resolution.confidence === "Address confirmed").length],
    ["Coordinate-backed", resolvedRows.filter(({ resolution }) => resolution.ready === "Review").length],
    ["Needs further research", unresolvedRows.length],
    ["DAABR candidate rows", candidateValues.length],
  ];
  summary.getRange("D3:E3").values = [["Confidence", "Count"]];
  if (confidenceCounts.length) summary.getRangeByIndexes(3, 3, confidenceCounts.length, 2).values = confidenceCounts;
  summary.getRange("G3:H3").values = [["Provider", "Count"]];
  if (providerCounts.length) summary.getRangeByIndexes(3, 6, providerCounts.length, 2).values = providerCounts;
  summary.getRange("A11:H14").merge();
  summary.getRange("A11").values = [[
    "Method: station labels are matched to all active DAABR oaza/cho candidates in the same municipality. " +
    "The official GSI reverse-geocoder then selects the coordinate locality. DAABR supplies the official English " +
    "romanization. Coordinate-only rows remain marked Review because catalogue coordinates may be rounded; " +
    "documented installation addresses take precedence.",
  ]];
  summary.getRange("A16:H18").merge();
  summary.getRange("A16").values = [[
    "Sources: DAABR 全国 町字マスター (local mt_town_all.csv); bundled QuakeDeck station catalogue; " +
    "GSI reverse-geocoder. Each researched row retains its direct evidence URL in Proposed Mapping.",
  ]];
  summary.showGridLines = false;

  const navy = "#16324F";
  const blue = "#2F75B5";
  const paleBlue = "#DCEAF7";
  const paleYellow = "#FFF2CC";
  const paleRed = "#FCE4D6";
  const paleGreen = "#E2F0D9";
  for (const sheet of [mapping, candidates, unresolved]) {
    const used = sheet.getUsedRange();
    used.format.font = { name: "Aptos", size: 10, color: "#222222" };
    used.format.verticalAlignment = "center";
    const header = sheet.getRangeByIndexes(0, 0, 1, used.columnCount);
    header.format = {
      fill: navy,
      font: { name: "Aptos", size: 10, bold: true, color: "#FFFFFF" },
      verticalAlignment: "center",
      wrapText: true,
      rowHeight: 32,
    };
    used.format.borders = { preset: "inside", style: "thin", color: "#D9E2F3" };
  }

  mapping.getRange(`E2:F${mappingValues.length + 1}`).format.numberFormat = "0.00000";
  mapping.getRange(`I2:I${mappingValues.length + 1}`).format.numberFormat = "0";
  mapping.getRange(`N2:O${mappingValues.length + 1}`).format.wrapText = true;
  mapping.getRange(`Q2:Q${mappingValues.length + 1}`).format.wrapText = true;
  mapping.getRange(`A2:A${mappingValues.length + 1}`).format.numberFormat = "0000000";
  mapping.getRange(`G2:G${mappingValues.length + 1}`).format.numberFormat = "0000000";
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Address confirmed", format: { fill: paleGreen, font: { bold: true, color: "#375623" } },
  });
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "matched", format: { fill: paleBlue, font: { color: blue } },
  });
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Unresolved", format: { fill: paleRed, font: { bold: true, color: "#9C0006" } },
  });
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "missing", format: { fill: paleYellow, font: { color: "#9C6500" } },
  });

  candidates.getRange(`A2:A${candidateValues.length + 1}`).format.numberFormat = "0000000";
  candidates.getRange(`H2:H${candidateValues.length + 1}`).format.numberFormat = "000000";
  candidates.getRange(`I2:I${candidateValues.length + 1}`).format.numberFormat = "0000000";
  candidates.getRange(`J2:J${candidateValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Yes", format: { fill: paleGreen, font: { bold: true, color: "#375623" } },
  });
  unresolved.getRange(`A2:A${unresolvedRows.length + 1}`).format.numberFormat = "0000000";
  unresolved.getRange(`G2:G${unresolvedRows.length + 1}`).format.numberFormat = "0000000";
  unresolved.getRange(`Q2:Q${unresolvedRows.length + 1}`).format.wrapText = true;

  const widths = [12, 25, 12, 18, 11, 11, 15, 20, 11, 22, 25, 28, 27, 26, 14, 48, 58];
  widths.forEach((width, index) => {
    mapping.getRangeByIndexes(0, index, mappingValues.length + 1, 1).format.columnWidth = width;
    unresolved.getRangeByIndexes(0, index, Math.max(1, unresolvedRows.length + 1), 1).format.columnWidth = width;
  });
  const candidateWidths = [12, 25, 20, 12, 24, 24, 30, 18, 14, 12];
  candidateWidths.forEach((width, index) => {
    candidates.getRangeByIndexes(0, index, candidateValues.length + 1, 1).format.columnWidth = width;
  });

  summary.getRange("A1:H1").format = {
    fill: navy,
    font: { name: "Aptos Display", size: 18, bold: true, color: "#FFFFFF" },
    horizontalAlignment: "left",
    verticalAlignment: "center",
    rowHeight: 34,
  };
  for (const headerRange of ["A3:B3", "D3:E3", "G3:H3"]) {
    summary.getRange(headerRange).format = {
      fill: blue,
      font: { bold: true, color: "#FFFFFF" },
      horizontalAlignment: "left",
    };
  }
  summary.getRange("A3:B8").format.borders = { preset: "inside", style: "thin", color: "#D9E2F3" };
  summary.getRange("A11:H14").format = { fill: paleBlue, wrapText: true, verticalAlignment: "top" };
  summary.getRange("A16:H18").format = { fill: "#F2F2F2", wrapText: true, verticalAlignment: "top" };
  summary.getRange("A1:H18").format.font = { name: "Aptos", size: 10, color: "#222222" };
  summary.getRange("A1:H1").format.font = { name: "Aptos Display", size: 18, bold: true, color: "#FFFFFF" };
  [18, 12, 12, 32, 12, 4, 20, 12].forEach((width, index) => {
    summary.getRangeByIndexes(0, index, 18, 1).format.columnWidth = width;
  });

  await fs.mkdir(OUTPUT_DIR, { recursive: true });
  const inspection = await workbook.inspect({
    kind: "table",
    range: "'Proposed Mapping'!A1:Q8",
    include: "values,formulas",
    tableMaxRows: 8,
    tableMaxCols: 17,
    maxChars: 12000,
  });
  const errors = await workbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 100 },
    summary: "final formula error scan",
    maxChars: 3000,
  });
  const preview = await workbook.render({ sheetName: "Summary", range: "A1:H18", scale: 1.5, format: "png" });
  await fs.writeFile(PREVIEW_PATH, new Uint8Array(await preview.arrayBuffer()));
  const mappingPreview = await workbook.render({
    sheetName: "Proposed Mapping",
    range: `A1:Q${Math.min(mappingValues.length + 1, 14)}`,
    scale: 1,
    format: "png",
  });
  await fs.writeFile(MAPPING_PREVIEW_PATH, new Uint8Array(await mappingPreview.arrayBuffer()));
  const candidatesPreview = await workbook.render({
    sheetName: "DAABR Candidates",
    range: `A1:J${Math.min(candidateValues.length + 1, 18)}`,
    scale: 1,
    format: "png",
  });
  await fs.writeFile(CANDIDATES_PREVIEW_PATH, new Uint8Array(await candidatesPreview.arrayBuffer()));
  const researchPreview = await workbook.render({
    sheetName: "Needs Research",
    range: `A1:Q${Math.min(unresolvedRows.length + 1, 14)}`,
    scale: 1,
    format: "png",
  });
  await fs.writeFile(RESEARCH_PREVIEW_PATH, new Uint8Array(await researchPreview.arrayBuffer()));
  const output = await SpreadsheetFile.exportXlsx(workbook);
  await output.save(OUTPUT_PATH);

  return {
    outputPath: OUTPUT_PATH,
    previewPath: PREVIEW_PATH,
    mappingPreviewPath: MAPPING_PREVIEW_PATH,
    candidatesPreviewPath: CANDIDATES_PREVIEW_PATH,
    researchPreviewPath: RESEARCH_PREVIEW_PATH,
    ambiguousRows: resolvedRows.length,
    candidateRows: candidateValues.length,
    unresolvedRows: unresolvedRows.length,
    confidenceCounts: Object.fromEntries(confidenceCounts),
    providerCounts: Object.fromEntries(providerCounts),
    unresolvedMethodCounts: Object.fromEntries(countBy(
      resolvedRows.filter(({ resolution }) => resolution.ready === "No"),
      ({ resolution }) => resolution.method,
    )),
    duplicateStationCodes: resolvedRows.length - new Set(resolvedRows.map(({ row }) => row.code)).size,
    yatsushiro: mappingValues.find((values) => values[0] === 4320231),
    statusByProvider: Object.fromEntries(
      [...new Set(resolvedRows.map(({ row }) => providerEnglish(row.providerJa)))].map((provider) => [
        provider,
        Object.fromEntries(countBy(
          resolvedRows.filter(({ row }) => providerEnglish(row.providerJa) === provider),
          ({ resolution }) => resolution.confidence,
        )),
      ]),
    ),
    unresolvedStations: resolvedRows
      .filter(({ resolution }) => resolution.confidence === "Unresolved")
      .map(({ row, geocode, resolution }) => ({
        code: row.code,
        nameJa: row.nameJa,
        provider: providerEnglish(row.providerJa),
        localPart: row.localPart,
        candidateCount: row.candidates.length,
        gsiLocality: geocode?.localityJa ?? "",
        method: resolution.method,
      })),
    romanizationMissingStations: resolvedRows
      .filter(({ resolution }) => resolution.confidence === "Romanization missing")
      .map(({ row, resolution }) => ({
        code: row.code,
        nameJa: row.nameJa,
        provider: providerEnglish(row.providerJa),
        localityJa: resolution.selected?.localityJa ?? "",
        localityKana: resolution.selected?.localityKana ?? "",
      })),
    inspection: inspection.ndjson,
    errors: errors.ndjson,
  };
}

const inputs = await loadInputs();
const ambiguousRows = buildAmbiguousRows(inputs);
if (process.argv.includes("--analyze")) {
  console.log(JSON.stringify({
    stations: inputs.stations.length,
    ambiguousRows: ambiguousRows.length,
    providerCounts: Object.fromEntries(countBy(ambiguousRows, (row) => providerEnglish(row.providerJa))),
    samples: ambiguousRows.slice(0, 10).map((row) => ({
      code: row.code,
      nameJa: row.nameJa,
      localPart: row.localPart,
      candidates: row.candidates.map((candidate) => candidate.localityJa),
    })),
  }, null, 2));
} else {
  const providerCache = await loadProviderCache();
  const offline = process.argv.includes("--offline");
  const jmaAddresses = offline
    ? providerCache.jma?.addresses ?? {}
    : await fetchJmaAddresses(providerCache);
  const niedStations = offline
    ? providerCache.nied?.items ?? []
    : await fetchNiedStations(providerCache);
  let geocodes = await reverseGeocode(ambiguousRows, niedStations);
  const envelopeRows = ambiguousRows.filter((row) => {
    const jmaAddress = row.providerJa === "気象庁" ? jmaAddresses[row.nameJa] : "";
    const niedMatch = row.providerJa === "防災科学技術研究所"
      ? matchNiedStation(row, niedStations)
      : null;
    const niedGeocode = niedMatch ? geocodes[`nied:${row.code}`] : null;
    return resolveCandidate(row, geocodes[row.code], jmaAddress, niedMatch, niedGeocode).confidence === "Unresolved";
  });
  if (!offline) geocodes = await reverseGeocodeEnvelopes(envelopeRows, geocodes);
  console.log(JSON.stringify(
    await buildWorkbook(ambiguousRows, geocodes, jmaAddresses, niedStations),
    null,
    2,
  ));
  process.exit(0);
}
