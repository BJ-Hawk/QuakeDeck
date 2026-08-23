import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.pref.toyama.jp/documents/25269/02.pdf';
const facilities = new Map([
  ['1620130', '富山市役所'],
  ['1620133', '婦中消防署'],
  ['1620136', '大山消防署'],
  ['1620137', '大沢野消防署'],
  ['1620138', '山田公民館'],
  ['1620139', '細入総合行政センター'],
  ['1620232', '高岡市役所'],
  ['1620233', '福岡総合行政センター'],
  ['1620531', '氷見市消防本部'],
  ['1620630', '滑川市役所'],
  ['1620732', '黒部消防署'],
  ['1620830', '砺波市役所'],
  ['1620832', '庄川支所'],
  ['1621030', '城端庁舎'],
  ['1621031', '平行政センター'],
  ['1621032', '上平行政センター'],
  ['1621034', '井波庁舎'],
  ['1621036', '福野庁舎'],
  ['1632130', '舟橋村役場'],
  ['1632231', '上市町消防署'],
  ['1634231', '入善町役場'],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const facilityNameJa = facilities.get(station.code);
  if (!facilityNameJa) continue;
  if (station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`${station.code} already has placement data.`);
  }
  station.facilityNameJa = facilityNameJa;
  station.metadataStatus = 'Official prefectural seismic-network facility';
  station.note = 'Toyama Prefecture lists this station, its host facility, and its locality in the official earthquake-observation table; no street number is published there.';
  station.placementPrecision = 'municipality_or_ward';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}

if (updated !== facilities.size) throw new Error(`Expected ${facilities.size} updates, made ${updated}.`);
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} Toyama stations.`);
