import https from 'node:https';
import { readFileSync, writeFileSync } from 'node:fs';

const SOURCE_URL = 'https://data.bodik.jp/api/3/action/datastore_search?resource_id=dad15967-bb1a-487f-ab7b-908ff373156d&limit=200';
const catalog = JSON.parse(readFileSync('outputs/station-name-audit/station_metadata_sources.json', 'utf8'));

function fetchJson(url) {
  return new Promise((resolve, reject) => https.get(url, (response) => {
    let text = '';
    response.setEncoding('utf8');
    response.on('data', (chunk) => { text += chunk; });
    response.on('end', () => response.statusCode === 200 ? resolve(JSON.parse(text)) : reject(new Error(`HTTP ${response.statusCode}`)));
  }).on('error', reject));
}

const source = (await fetchJson(SOURCE_URL)).result.records;
const matches = [];
const ambiguous = [];
const unmatched = [];
for (const record of source) {
  const candidates = catalog.stations.filter((station) => station.prefectureJa === '熊本県' && station.nameJa === record['発表地点名称']);
  if (candidates.length === 1) matches.push({ station: candidates[0], record });
  else if (candidates.length > 1) ambiguous.push({ nameJa: record['発表地点名称'], codes: candidates.map((station) => station.code) });
  else unmatched.push(record['発表地点名称']);
}

const updates = matches.filter(({ station }) => !station.publishedAddressJa);
if (process.argv.includes('--apply')) {
  for (const { station, record } of updates) {
    station.publishedAddressJa = record['住　　所'];
    station.facilityNameJa = record['設置場所'];
    station.placementPrecision = 'exact_address';
    delete station.placementLocalityJa;
    station.metadataStatus = 'Kumamoto Prefecture seismic-intensity installation list (2022)';
    if (!station.sourceUrls.includes(SOURCE_URL)) station.sourceUrls.push(SOURCE_URL);
    const note = 'Exact placement address and facility sourced from Kumamoto Prefecture seismic-intensity installation list (2022).';
    station.note = station.note ? `${station.note} ${note}` : note;
  }
  catalog.coverage = {
    ...catalog.coverage,
    publishedAddresses: catalog.coverage.publishedAddresses + updates.length,
    exactPlacementAddressUpdates: catalog.coverage.exactPlacementAddressUpdates + updates.length,
    localityPlacementRecords: catalog.coverage.localityPlacementRecords - updates.length,
  };
  writeFileSync('outputs/station-name-audit/station_metadata_sources.json', `${JSON.stringify(catalog, null, 2)}\n`);
}

console.log(JSON.stringify({
  sourceRecords: source.length,
  exactNameMatches: matches.length,
  addresslessMatches: matches.filter(({ station }) => !station.publishedAddressJa).length,
  ambiguous,
  unmatched,
  updates: updates.map(({ station, record }) => ({
    code: station.code,
    stationNameJa: station.nameJa,
    addressJa: record['住　　所'],
    facilityNameJa: record['設置場所'],
  })),
}, null, 2));
