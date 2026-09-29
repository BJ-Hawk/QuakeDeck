import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const stationCode = '4652534';
const sourceUrls = [
  'https://www.pref.kagoshima.jp/aj01/documents/105080_20260325160126-1.pdf',
  'https://www.ntt-west.co.jp/kagoshima/pdf/tokusetsu20160913.pdf',
];
const note = 'Kagoshima Prefecture\'s seismic-intensity maintenance table places this meter on the grounds of Setouchi Town Yoro Assembly Hall. Separate official listings identify Yoro Remote-Island Residents\' Center at Yoro 383, but neither source establishes that it is the named assembly hall or hosts this meter; no parcel address is recorded.';

const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === stationCode);
if (!station) throw new Error(`Missing station ${stationCode}.`);
const changed = station.note !== note
  || station.metadataStatus !== 'Official prefectural facility placement; current-facility identity unconfirmed'
  || sourceUrls.some((url) => !station.sourceUrls.includes(url));
if (!changed) throw new Error('No update needed; refusing an unguarded rewrite.');

station.note = note;
station.metadataStatus = 'Official prefectural facility placement; current-facility identity unconfirmed';
for (const url of sourceUrls) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Yoro facility-identity evidence update.`);
