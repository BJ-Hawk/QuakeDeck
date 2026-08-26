import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const sourceUrl = 'https://www.city.osaka.lg.jp/shobo/page/0000018935.html';

const updates = [
  ['2710231', '大阪市都島消防署', '大阪市都島区都島本通2-1-8'],
  ['2710330', '大阪市福島消防署上福島出張所', '大阪市福島区福島4-5-32'],
  ['2710630', '大阪市西消防署', '大阪市西区九条南1-12-54'],
  ['2710730', '大阪市水上消防署', '大阪市港区築港3-1-47'],
  ['2710831', '大阪市大正消防署泉尾出張所', '大阪市大正区泉尾1-26-4'],
  ['2710930', '大阪市天王寺消防署', '大阪市天王寺区上本町8-5-10'],
  ['2711131', '大阪市浪速消防署', '大阪市浪速区元町1-14-20'],
  ['2711430', '大阪市東淀川消防署井高野出張所', '大阪市東淀川区北江口1-2-10'],
  ['2711530', '大阪市東成消防署中本出張所', '大阪市東成区東中本2-1-9'],
  ['2711630', '大阪市生野消防署', '大阪市生野区舎利寺1-13-8'],
  ['2711731', '大阪市旭消防署', '大阪市旭区大宮1-1-11'],
  ['2711831', '大阪市城東消防署放出出張所', '大阪市城東区放出西1-1-17'],
  ['2711930', '大阪市阿倍野消防署', '大阪市阿倍野区松崎町4-4-30'],
  ['2712030', '大阪市住吉消防署', '大阪市住吉区遠里小野1-1-9'],
  ['2712130', '大阪市東住吉消防署杭全出張所', '大阪市東住吉区杭全8-1-16'],
  ['2712230', '大阪市西成消防署', '大阪市西成区岸里1-4-26'],
  ['2712330', '大阪市淀川消防署', '大阪市淀川区木川東4-10-12'],
  ['2712430', '大阪市鶴見消防署', '大阪市鶴見区横堤5-5-45'],
  ['2712531', '大阪市住之江消防署', '大阪市住之江区御崎4-11-6'],
  ['2712630', '大阪市平野消防署', '大阪市平野区平野南1-2-9'],
  ['2712730', '大阪市北消防署', '大阪市北区茶屋町19-41'],
];

for (const [code, facility, address] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa !== facility) {
    throw new Error(`${code}: expected ${facility}, found ${station.facilityNameJa}`);
  }
  if (station.publishedAddressJa) throw new Error(`${code}: address already present`);

  station.publishedAddressJa = address;
  station.metadataStatus = 'Official prefectural placement and official address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${facility} as the host site; Osaka City Fire Bureau publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
