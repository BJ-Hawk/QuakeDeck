import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.pref.yamanashi.jp/shobo/documents/88770017458.pdf';
const addressSource = 'https://www.city.hokuto.yamanashi.jp/docs/1374.html';
const updates = {
  '1920930': ['北杜市明野総合支所', 'Hokuto City Akeno General Branch', '山梨県北杜市明野町上手5219-1'],
  '1920932': ['北杜市高根総合支所', 'Hokuto City Takane General Branch', '山梨県北杜市高根町村山北割3261'],
  '1920938': ['北杜市武川総合支所', 'Hokuto City Mukawa General Branch', '山梨県北杜市武川町山高1457-3'],
  '1920939': ['北杜市長坂総合支所', 'Hokuto City Nagasaka General Branch', '山梨県北杜市長坂町長坂上条2575-19'],
  '1920940': ['北杜市白州総合支所', 'Hokuto City Hakushu General Branch', '山梨県北杜市白州町白須312'],
  '1920942': ['北杜市大泉総合支所', 'Hokuto City Oizumi General Branch', '山梨県北杜市大泉町西井出3164-1'],
  '1920943': ['北杜市小淵沢総合支所', 'Hokuto City Kobuchizawa General Branch', '山梨県北杜市小淵沢町7711'],
};

const data = JSON.parse(fs.readFileSync(path, 'utf8'));
let changed = 0;
for (const [code, [facilityNameJa, facilityNameEn, publishedAddressJa]] of Object.entries(updates)) {
  const station = data.stations.find((item) => item.code === code);
  if (!station) throw new Error(`Missing station ${code}.`);
  if (station.publishedAddressJa) throw new Error(`Station ${code} already has an address.`);
  const expected = {
    facilityNameJa,
    facilityNameEn,
    publishedAddressJa,
    metadataStatus: 'Official prefectural facility placement',
    note: `Yamanashi Prefecture's earthquake-network documentation identifies this station at ${facilityNameJa}. Hokuto City publishes that branch's address as ${publishedAddressJa}.`,
    placementLocalityJa: publishedAddressJa.replace(/^山梨県/, ''),
    placementPrecision: 'exact_address',
  };
  Object.assign(station, expected);
  for (const url of [placementSource, addressSource]) {
    if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
  }
  changed += 1;
}
if (changed !== Object.keys(updates).length) throw new Error('Incomplete guarded update.');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${changed} Hokuto general-branch intensity-address updates.`);
