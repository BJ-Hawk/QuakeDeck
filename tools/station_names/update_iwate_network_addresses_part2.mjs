import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.fdma.go.jp/bousaikeikaku/hokkaido_tohoku/iwate/items/02iwate_shiryou.pdf';
const placements = new Map([
  ['0320130', '岩手県盛岡市渋民字泉田360'],
  ['0320230', '岩手県宮古市茂市第2地割112-1'],
  ['0320535', '岩手県花巻市材木町12-6'],
  ['0320537', '岩手県花巻市東和町土沢8区60'],
  ['0320731', '岩手県久慈市山形町川井第8地割31'],
  ['0321530', '岩手県奥州市江刺大通り1-8'],
  ['0321531', '岩手県奥州市前沢字七日町裏71'],
  ['0321535', '岩手県奥州市胆沢南都田字加賀谷地270'],
  ['0321536', '岩手県奥州市衣川古戸53-1'],
]);
const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const [code, publishedAddressJa] of placements) {
  const station = data.stations.find((item) => item.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa || station.publishedAddressJa) continue;
  station.publishedAddressJa = publishedAddressJa;
  station.metadataStatus = 'Official FDMA seismic-network address';
  station.note = 'The FDMA-hosted Iwate disaster-plan annex lists this prefectural intensity meter at the recorded street address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
