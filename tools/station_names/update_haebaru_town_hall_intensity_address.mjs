import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const stationCode = '4735030';
const sourceUrls = [
  'https://www.town.haebaru.lg.jp/soshiki/2/1307.html',
  'https://www.town.haebaru.lg.jp/life/sub/4/29/',
];

const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === stationCode);
if (!station) throw new Error(`Missing station ${stationCode}.`);
const expected = {
  facilityNameJa: '南風原町役場',
  facilityNameEn: 'Haebaru Town Hall',
  publishedAddressJa: '沖縄県島尻郡南風原町字兼城686番地',
  metadataStatus: 'Official municipal facility placement',
  note: 'A Haebaru Town council record identifies the town intensity meter on the west side of the town-hall visitor parking area. The town publishes the town hall address as 沖縄県島尻郡南風原町字兼城686番地.',
  placementLocalityJa: '南風原町字兼城686番地',
  placementPrecision: 'exact_address',
};
const changed = Object.entries(expected).some(([key, value]) => station[key] !== value)
  || sourceUrls.some((url) => !station.sourceUrls.includes(url));
if (!changed) throw new Error('No update needed; refusing an unguarded rewrite.');
Object.assign(station, expected);
for (const url of sourceUrls) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Haebaru Town Hall intensity-address update.`);
