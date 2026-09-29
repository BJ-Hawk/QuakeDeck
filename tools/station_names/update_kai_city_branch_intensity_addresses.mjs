import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.pref.yamanashi.jp/shobo/documents/88770017458.pdf';
const addressSource = 'https://www.city.kai.yamanashi.jp/page/4087.html';
const updates = {
  '1921033': ['甲斐市役所双葉庁舎', 'Kai City Futaba Office', '山梨県甲斐市下今井171'],
  '1921034': ['甲斐市役所敷島庁舎', 'Kai City Shikishima Office', '山梨県甲斐市島上条2254-1'],
};

const data = JSON.parse(fs.readFileSync(path, 'utf8'));
let changed = 0;
for (const [code, [facilityNameJa, facilityNameEn, publishedAddressJa]] of Object.entries(updates)) {
  const station = data.stations.find((item) => item.code === code);
  if (!station) throw new Error(`Missing station ${code}.`);
  if (station.publishedAddressJa) throw new Error(`Station ${code} already has an address.`);
  Object.assign(station, {
    facilityNameJa,
    facilityNameEn,
    publishedAddressJa,
    metadataStatus: 'Official prefectural facility placement',
    note: `Yamanashi Prefecture's earthquake-network documentation identifies this station at ${facilityNameJa}. Kai City publishes that office's address as ${publishedAddressJa}.`,
    placementLocalityJa: publishedAddressJa.replace(/^山梨県/, ''),
    placementPrecision: 'exact_address',
  });
  for (const url of [placementSource, addressSource]) {
    if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
  }
  changed += 1;
}
if (changed !== Object.keys(updates).length) throw new Error('Incomplete guarded update.');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${changed} Kai City intensity-address updates.`);
