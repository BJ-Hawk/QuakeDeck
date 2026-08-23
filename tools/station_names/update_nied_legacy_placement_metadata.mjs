import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  ['0632220', '山形県西村山郡西川町大字大井沢字高野浦822-1', null, 'https://www.hinet.bosai.go.jp/st_info/st_update/?LANG=ja&r=HFHKS2007&rym=200711'],
  ['1936420', '山梨県南巨摩郡早川町保757番地', null, 'https://www.hinet.bosai.go.jp/st_info/st_update/?LANG=ja&r=HFHKS2007&rym=200801'],
  ['2042320', '長野県木曽郡南木曽町読書3668-1', '南木曽小学校（旧読書小学校）', 'https://www.bosai.go.jp/information/tender/supply/pdf/shiyousho.pdf'],
  ['2420920', '三重県尾鷲市大字南浦字小原野1697番地', null, 'https://www.hinet.bosai.go.jp/st_info/st_update/?LANG=ja&r=HFHKS2007&rym=200801'],
  ['4120520', '佐賀県伊万里市立花町1355番地1', null, 'https://www.hinet.bosai.go.jp/st_info/st_update/?LANG=ja&r=HFHKS2007&rym=200802']
];
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
let changed = 0;
for (const [code, address, facility, source] of updates) {
  const station = data.stations.find(s => s.code === code);
  if (!station || station.publishedAddressJa) continue;
  station.publishedAddressJa = address;
  if (facility) station.facilityNameJa = facility;
  station.placementPrecision = 'exact_address';
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls || []), source])];
  station.metadataStatus = 'source_verified';
  station.note = 'Verified against NIED’s official station record.';
  changed++;
}
data.coverage.publishedAddresses += changed;
data.coverage.exactPlacementAddressUpdates += changed;
data.coverage.localityPlacementRecords -= changed;
fs.writeFileSync(path, JSON.stringify(data, null, 2) + '\n');
console.log(`updated ${changed} exact station placements`);
