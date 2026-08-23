import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = new Map([
  ['0746131', {
    facilityNameJa: '西郷村役場',
    publishedAddressJa: '福島県西白河郡西郷村大字熊倉字折口原40',
    sourceUrls: [
      'https://www.vill.nishigo.fukushima.jp/material/files/group/5/chiikibousaikeikakusinnsaitaisakuhen.pdf',
      'https://www.vill.nishigo.fukushima.jp/material/files/group/18/R3nisigoumuranokyouiku.pdf',
    ],
    note: 'Nishigo Village states that its Fukushima Prefecture seismic-network meter is installed at the Village Hall; the village publishes the hall address.',
  }],
  ['1122231', {
    facilityNameJa: '越谷市役所',
    publishedAddressJa: '埼玉県越谷市越ヶ谷四丁目2番1号',
    sourceUrls: [
      'https://www.city.koshigaya.saitama.jp/kurashi_shisei/shisei/koho/kocho/siminnnoteiannseido_files_28kaitoushuuH27.pdf',
      'https://www.city.koshigaya.saitama.jp/toiawase/map.html',
    ],
    note: 'Koshigaya City states that the prefectural intensity meter is installed on the City Hall grounds; the city publishes the hall address.',
  }],
]);
const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;

for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update || station.publishedAddressJa || station.facilityNameJa) continue;
  station.facilityNameJa = update.facilityNameJa;
  station.publishedAddressJa = update.publishedAddressJa;
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = update.note;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), ...update.sourceUrls])];
  updated += 1;
}

if (updated) writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} station(s).`);
