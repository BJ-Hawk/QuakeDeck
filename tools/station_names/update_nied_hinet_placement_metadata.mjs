import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const updates = [
  ['2421521', '三重県志摩市志摩町布施田1139', 'https://www.hinet.bosai.go.jp/notice/replacement2007/?LANG=ja&_r=200801'],
  ['3920620', '高知県須崎市東糺町37番', 'https://www.hinet.bosai.go.jp/notice/replacement/?_r=201403'],
  ['4649120', '鹿児島県肝属郡南大隅町佐多伊座敷3465番地', 'https://www.hinet.bosai.go.jp/st_info/st_update/?LANG=ja&r=HFHKS2007&rym=200801']
];
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
let changed = 0;
for (const [code, address, source] of updates) {
  const station = data.stations.find(s => s.code === code);
  if (!station || station.publishedAddressJa) continue;
  station.publishedAddressJa = address;
  station.placementPrecision = 'exact_address';
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls || []), source])];
  station.metadataStatus = 'source_verified';
  station.note = 'Verified against NIED’s official Hi-net station update record.';
  changed++;
}
data.coverage.publishedAddresses += changed;
data.coverage.exactPlacementAddressUpdates += changed;
data.coverage.localityPlacementRecords -= changed;
fs.writeFileSync(path, JSON.stringify(data, null, 2) + '\n');
console.log(`updated ${changed} exact station placements`);
