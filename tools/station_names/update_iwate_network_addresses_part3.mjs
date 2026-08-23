import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.fdma.go.jp/bousaikeikaku/hokkaido_tohoku/iwate/items/02iwate_shiryou.pdf';
const placements = new Map([
  ['0348330', '岩手県下閉伊郡岩泉町岩泉字惣畑59-5'],
  ['0348532', '岩手県下閉伊郡普代村第9地割字銅屋13-2'],
  ['0350330', '岩手県九戸郡野田村大字野田第20地割14'],
  ['0350731', '岩手県九戸郡洋野町大野第8地割47-2'],
  ['0344131', '岩手県気仙郡住田町世田米字川向96-1'],
  ['0346133', '岩手県上閉伊郡大槌町小鎚第32地割126'],
  ['0336631', '岩手県和賀郡西和賀町沢内字太田2地割81-1'],
  ['0338131', '岩手県胆沢郡金ケ崎町西根南町22-1'],
  ['0340231', '岩手県西磐井郡平泉町平泉字志羅山45-2'],
  ['0350131', '岩手県九戸郡軽米町大字軽米第10地割85'],
  ['0350631', '岩手県九戸郡九戸村大字伊保内第10地割11-6'],
  ['0352430', '岩手県二戸郡一戸町高善寺字大川鉢24-9'],
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
