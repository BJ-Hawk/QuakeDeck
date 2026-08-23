import { readFileSync, writeFileSync } from 'node:fs';

const sourceUrl = 'https://www.data.jma.go.jp/eqev/data/kyoshin/jma-shindo.html';
const data = JSON.parse(readFileSync('outputs/station-name-audit/station_metadata_sources.json', 'utf8'));
const html = await (await fetch(sourceUrl)).text();
const decode = (value) => value
  .replace(/<[^>]+>/g, '')
  .replace(/&nbsp;/g, ' ')
  .replace(/&amp;/g, '&')
  .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)))
  .replace(/\s+/g, ' ')
  .trim();
const rows = [...html.matchAll(/<tr[^>]*>([\s\S]*?)<\/tr>/gi)]
  .map((match) => [...match[1].matchAll(/<t[dh][^>]*>([\s\S]*?)<\/t[dh]>/gi)].map((cell) => decode(cell[1])))
  .filter((cells) => cells.length >= 3 && cells[1] !== '震度観測点名称' && cells[2] !== '観測点所在地')
  .map((cells) => ({
    nameJa: cells[1],
    address: cells[2],
    latitude: Number(cells[3]) + Number(cells[4]) / 60,
    longitude: Number(cells[5]) + Number(cells[6]) / 60
  }));
const normalize = (value) => value
  .normalize('NFKC')
  .replace(/[\s　]/g, '')
  .replace(/[＊*]/g, '')
  .replace(/（旧[^）]*）/g, '')
  .replace(/（臨時）/g, '')
  .replace(/\(旧[^)]*\)/g, '');
const byName = new Map();
for (const row of rows) {
  const key = normalize(row.nameJa);
  if (!byName.has(key)) byName.set(key, []);
  byName.get(key).push(row);
}
const matches = data.stations
  .filter((station) => !station.publishedAddressJa)
  .map((station) => ({ station, candidates: byName.get(normalize(station.nameJa)) ?? [] }))
  .filter(({ candidates }) => candidates.length === 1 && candidates[0].address && candidates[0].address !== '不明');
const coordinateCandidates = data.stations
  .filter((station) => !station.publishedAddressJa && Number.isFinite(station.catalogueLatitude) && Number.isFinite(station.catalogueLongitude))
  .map((station) => {
    const nearby = rows.filter((row) => row.address && row.address !== '不明' && Math.abs(row.latitude - station.catalogueLatitude) <= 0.006 && Math.abs(row.longitude - station.catalogueLongitude) <= 0.006);
    return { station, nearby };
  })
  .filter(({ nearby }) => nearby.length === 1);
if (process.argv.includes('--apply')) {
  let changed = 0;
  for (const { station, candidates } of matches) {
    if (station.publishedAddressJa) continue;
    const published = candidates[0].address;
    const facility = published.match(/[（(]([^（）()]+)[）)]/u)?.[1] ?? null;
    station.publishedAddressJa = published.replace(/[（(][^（）()]+[）)]/gu, '').trim();
    if (facility) station.facilityNameJa = facility;
    station.metadataStatus = 'JMA official station address';
    station.sourceUrls = [...new Set([...station.sourceUrls, sourceUrl])];
    station.note = 'Exact station-name match to the Japan Meteorological Agency published intensity-station address list.';
    station.placementPrecision = 'exact_address';
    changed += 1;
  }
  if (changed) {
    data.coverage.publishedAddresses += changed;
    data.coverage.exactPlacementAddressUpdates += changed;
    data.coverage.localityPlacementRecords -= changed;
    writeFileSync('outputs/station-name-audit/station_metadata_sources.json', `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  }
  console.log(`applied ${changed} station(s)`);
}
console.log(JSON.stringify({ rows: rows.length, matches: matches.length, coordinateCandidates: coordinateCandidates.length, samples: matches.slice(0, 30).map(({ station, candidates }) => ({ code: station.code, nameJa: station.nameJa, address: candidates[0].address })), coordinateSamples: coordinateCandidates.slice(0, 50).map(({ station, nearby }) => ({ code: station.code, nameJa: station.nameJa, locality: station.placementLocalityJa, candidateNameJa: nearby[0].nameJa, candidateAddress: nearby[0].address, deltaLat: +(nearby[0].latitude - station.catalogueLatitude).toFixed(4), deltaLon: +(nearby[0].longitude - station.catalogueLongitude).toFixed(4) })) }, null, 2));
