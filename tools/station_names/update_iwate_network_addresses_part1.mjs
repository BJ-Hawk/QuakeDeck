import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.fdma.go.jp/bousaikeikaku/hokkaido_tohoku/iwate/items/02iwate_shiryou.pdf';
const placements = new Map([
  ['0320831', '岩手県遠野市宮守町下宮守29地割73-1'],
  ['0320935', '岩手県一関市花泉町涌津字一ノ町29'],
  ['0320936', '岩手県一関市千厩町千厩字北方174'],
  ['0320937', '岩手県一関市東山町長坂字西本町105-1'],
  ['0320938', '岩手県一関市室根町折壁字八幡沖345'],
  ['0320939', '岩手県一関市藤沢町藤沢字町裏187'],
  ['0320940', '岩手県一関市川崎町薄衣字諏訪前137'],
  ['0321033', '岩手県陸前高田市高田町字栃ケ沢210-2'],
  ['0321330', '岩手県二戸市浄法寺町下前田37-4'],
  ['0321431', '岩手県八幡平市叺田70'],
  ['0321433', '岩手県八幡平市野駄第21地割170'],
  ['0321630', '岩手県滝沢市中鵜飼55'],
  ['0330330', '岩手県岩手郡岩手町大字五日市第10地割44'],
  ['0332132', '岩手県紫波郡紫波町紫波中央駅前2-3-1'],
  ['0332232', '岩手県紫波郡矢巾町大字南矢幅第13地割123'],
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
