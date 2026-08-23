import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.pref.aichi.jp/uploaded/attachment/542306.pdf';
const station = '2322831';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '岩倉市消防本部';
  target.publishedAddressJa = '愛知県岩倉市北穴田119番地';
  target.metadataStatus = 'Official prefectural facility address';
  target.note = 'The current Aichi prefectural network table lists one intensity meter for Iwakura City, at the Fire Headquarters. The active JMA label for that municipal meter remains 岩倉市川井町.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), source])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
