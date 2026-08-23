import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.pref.ishikawa.lg.jp/bousai/nenpo/documents/h30-sbn-00zentai.pdf';
const currentStationSource = 'https://www.library.pref.ishikawa.lg.jp/shosho/content/item/bib/BDT20000168.0003/rawdip/BDT20000168.0003.pdf';
const station = '1720632';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '加賀市山中温泉支所';
  target.publishedAddressJa = '石川県加賀市山中温泉湯の出町夕33番地';
  target.metadataStatus = 'Official prefectural facility address';
  target.note = 'The prefectural seismic-network table places the municipal intensity meter at the Yamanaka Onsen Branch Office; the current prefectural station map identifies this as 加賀市山中温泉本町.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, currentStationSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
