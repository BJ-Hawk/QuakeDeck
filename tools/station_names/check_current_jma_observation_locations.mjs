import fs from 'node:fs';

const htmlPath = `${process.env.TEMP}\\quakedeck_jma_shindo.html`;
const jsonPath = 'outputs/station-name-audit/station_metadata_sources.json';
const html = fs.readFileSync(htmlPath, 'utf8');
const clean = (value) => value
  .replace(/<[^>]*>/g, '')
  .replace(/&nbsp;/g, ' ')
  .replace(/&amp;/g, '&')
  .replace(/&#x3000;/g, ' ')
  .replace(/\s+/g, ' ')
  .trim();
const rows = [];
for (const match of html.matchAll(/<tr>([\s\S]*?)<\/tr>/g)) {
  const cells = [...match[1].matchAll(/<td[^>]*>([\s\S]*?)<\/td>/g)].map((cell) => clean(cell[1]));
  if (cells.length === 9 && cells[1] && cells[2]) rows.push({ nameJa: cells[1], address: cells[2], ended: cells[8] });
}
const stations = JSON.parse(fs.readFileSync(jsonPath, 'utf8')).stations;
const byName = new Map();
for (const station of stations) {
  const matches = byName.get(station.nameJa) ?? [];
  matches.push(station);
  byName.set(station.nameJa, matches);
}
const candidates = rows
  .filter((row) => !row.ended)
  .flatMap((row) => (byName.get(row.nameJa) ?? []).map((station) => ({ station, row })))
  .filter(({ station }) => station.placementPrecision !== 'exact_address');
const matched = rows
  .filter((row) => !row.ended)
  .flatMap((row) => (byName.get(row.nameJa) ?? []).map((station) => ({ station, row })));
const staleProvenance = matched.filter(({ station }) => station.metadataStatus === 'Catalogue only');
const missingFacilities = matched.filter(({ station, row }) => /[（(].+[）)]/.test(row.address) && !station.facilityNameJa);
console.log(JSON.stringify({ rows: rows.length, activeRows: rows.filter((row) => !row.ended).length, matched: matched.length, candidates: candidates.length, staleProvenance: staleProvenance.length, missingFacilities: missingFacilities.length }, null, 2));
for (const { station, row } of candidates) {
  console.log([station.code, station.nameJa, station.providerJa, row.address].join('\t'));
}
for (const { station, row } of staleProvenance) {
  console.log(['STALE', station.code, station.nameJa, station.publishedAddressJa ?? '', station.facilityNameJa ?? '', row.address].join('\t'));
}
for (const { station, row } of missingFacilities) {
  console.log(['FACILITY', station.code, station.nameJa, station.publishedAddressJa ?? '', row.address].join('\t'));
}
