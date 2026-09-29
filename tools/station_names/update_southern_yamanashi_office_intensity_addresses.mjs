import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.pref.yamanashi.jp/shobo/documents/88770017458.pdf';
const updates = {
  '1934630': {
    facilityNameJa: '市川三郷町役場三珠支所',
    facilityNameEn: 'Ichikawamisato Town Hall Mitama Branch',
    publishedAddressJa: '山梨県西八代郡市川三郷町上野2714-2',
    addressSource: 'https://www.town.ichikawamisato.yamanashi.jp/reiki_int/reiki_honbun/r2090011001.html',
  },
  '1936533': {
    facilityNameJa: '身延町下部支所',
    facilityNameEn: 'Minobu Town Shimobe Branch',
    publishedAddressJa: '山梨県南巨摩郡身延町常葉1093',
    addressSource: 'https://www.town.minobu.lg.jp/page/1054.html',
  },
  '1936630': {
    facilityNameJa: '南部町役場南部分庁舎',
    facilityNameEn: 'Nanbu Town Hall Nanbu Branch Office',
    publishedAddressJa: '山梨県南巨摩郡南部町内船4473-1',
    addressSource: 'https://www.town.nanbu.yamanashi.jp/kakuka/koutsuubousai/aed-shisetsu.html',
  },
  '1936631': {
    facilityNameJa: '南部町役場本庁舎',
    facilityNameEn: 'Nanbu Town Hall Main Office',
    publishedAddressJa: '山梨県南巨摩郡南部町福士28505-2',
    addressSource: 'https://www.town.nanbu.yamanashi.jp/kakuka/koutsuubousai/aed-shisetsu.html',
  },
};

const data = JSON.parse(fs.readFileSync(path, 'utf8'));
let changed = 0;
for (const [code, update] of Object.entries(updates)) {
  const station = data.stations.find((item) => item.code === code);
  if (!station) throw new Error(`Missing station ${code}.`);
  if (station.publishedAddressJa) throw new Error(`Station ${code} already has an address.`);
  const { addressSource, ...expected } = update;
  Object.assign(station, {
    ...expected,
    metadataStatus: 'Official prefectural facility placement',
    note: `Yamanashi Prefecture's earthquake-network documentation identifies this station at ${expected.facilityNameJa}. The municipality publishes that office's address as ${expected.publishedAddressJa}.`,
    placementLocalityJa: expected.publishedAddressJa.replace(/^山梨県/, ''),
    placementPrecision: 'exact_address',
  });
  for (const url of [placementSource, addressSource]) {
    if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
  }
  changed += 1;
}
if (changed !== Object.keys(updates).length) throw new Error('Incomplete guarded update.');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${changed} southern-Yamanashi intensity-address updates.`);
