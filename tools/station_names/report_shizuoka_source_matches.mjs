import https from 'node:https';
import { readFileSync, writeFileSync } from 'node:fs';

const SOURCE_URL = 'https://opendata.pref.shizuoka.jp/dataset/fuji-166/resource/32469/content.html';
const catalog = JSON.parse(readFileSync('outputs/station-name-audit/station_metadata_sources.json', 'utf8'));

function plainHtml(value) {
  return value
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&#x([0-9a-f]+);/gi, (_, hex) => String.fromCodePoint(Number.parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, dec) => String.fromCodePoint(Number.parseInt(dec, 10)))
    .trim();
}

function providerMatches(sourceProvider, stationProvider) {
  return (sourceProvider === '気象庁' && stationProvider === '気象庁') ||
    (sourceProvider === '独立行政法人防災科学技術研究所' && stationProvider === '防災科学技術研究所') ||
    ((sourceProvider === '県' || sourceProvider === '市町') && stationProvider === '地方公共団体');
}

function fetchText(url) {
  return new Promise((resolve, reject) => {
    https.get(url, (response) => {
      if (response.statusCode !== 200) {
        reject(new Error(`HTTP ${response.statusCode}`));
        response.resume();
        return;
      }
      let text = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => { text += chunk; });
      response.on('end', () => resolve(text));
    }).on('error', reject);
  });
}

const html = await fetchText(SOURCE_URL);
const rows = [...html.matchAll(/<tr>([\s\S]*?)<\/tr>/g)].map((match) =>
  [...match[1].matchAll(/<td>([\s\S]*?)<\/td>/g)].map((cell) => plainHtml(cell[1]))
).filter((cells) => cells.length === 13 && cells[0] !== '震度観測点名');

const sourceStations = rows.map((cells) => ({
  nameJa: cells[0],
  addressJa: cells[2],
  providerJa: cells[3],
  latitude: Number(cells[6]) + Number(cells[7]) / 60,
  longitude: Number(cells[8]) + Number(cells[9]) / 60,
}));

const matches = [];
for (const source of sourceStations) {
  const exact = catalog.stations.filter((station) => station.prefectureJa === '静岡県' &&
    station.nameJa === source.nameJa && providerMatches(source.providerJa, station.providerJa));
  for (const station of exact) {
    const latitude = station.providerLatitude ?? station.catalogueLatitude;
    const longitude = station.providerLongitude ?? station.catalogueLongitude;
    const delta = Math.hypot(latitude - source.latitude, longitude - source.longitude);
    matches.push({
      code: station.code,
      stationNameJa: station.nameJa,
      currentProvider: station.providerJa,
      sourceProvider: source.providerJa,
      sourceAddressJa: source.addressJa,
      deltaDegrees: Number(delta.toFixed(5)),
      existingAddressJa: station.publishedAddressJa,
      station,
    });
  }
}

const eligible = matches.filter((match) => !match.existingAddressJa && match.deltaDegrees <= 0.05);
if (process.argv.includes('--apply')) {
  for (const match of eligible) {
    const facility = match.sourceAddressJa.match(/[\(（]([^()（）]+)[\)）]/)?.[1] ?? null;
    match.station.publishedAddressJa = match.sourceAddressJa.replace(/[\s　]*[\(（][^()（）]+[\)）]\s*$/, '').trim();
    if (facility) match.station.facilityNameJa = facility;
    match.station.metadataStatus = 'Official Shizuoka prefectural seismic-instrument list (2013)';
    if (!match.station.sourceUrls.includes(SOURCE_URL)) match.station.sourceUrls.push(SOURCE_URL);
    const note = 'Exact placement address sourced from Shizuoka Prefecture seismic-instrument list (2013); station name, provider, and listed position matched to this catalogue record.';
    match.station.note = match.station.note ? `${match.station.note} ${note}` : note;
  }
  catalog.coverage = {
    ...catalog.coverage,
    exactPlacementAddressUpdates: (catalog.coverage.exactPlacementAddressUpdates ?? 0) + eligible.length,
  };
  writeFileSync('outputs/station-name-audit/station_metadata_sources.json', `${JSON.stringify(catalog, null, 2)}\n`);
}

console.log(JSON.stringify({
  exactNameProviderMatches: matches.length,
  eligibleAddressUpdates: eligible.length,
  excludedPositionMismatches: matches.filter((match) => match.deltaDegrees > 0.05).map(({ code, stationNameJa, deltaDegrees }) => ({ code, stationNameJa, deltaDegrees })),
  updates: eligible.map(({ code, stationNameJa, currentProvider, sourceAddressJa }) => ({ code, stationNameJa, currentProvider, sourceAddressJa })),
}, null, 2));
