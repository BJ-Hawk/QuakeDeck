import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));

const updates = [
  {
    code: '2710431',
    facility: '大阪市此花区役所',
    address: '大阪市此花区春日出北1-8-4',
    sourceUrl: 'https://www.city.osaka.lg.jp/konohana/page/0000487404.html',
    publisher: 'Osaka City',
  },
  {
    code: '2711331',
    facility: '大阪府西淀川警察署',
    address: '大阪市西淀川区千舟2丁目6番24号',
    sourceUrl: 'https://www.police.pref.osaka.lg.jp/sogo/keisatsusho/shinai/6401.html',
    publisher: 'Osaka Prefectural Police',
  },
  {
    code: '2714132',
    facility: '堺市役所',
    address: '堺市堺区南瓦町3番1号',
    sourceUrl: 'https://www.city.sakai.lg.jp/shisei/gaiyo/shozaichi.html',
    publisher: 'Sakai City',
  },
  {
    code: '2714330',
    facility: '堺市東区役所',
    address: '堺市東区日置荘原寺町195番地1',
    sourceUrl: 'https://www.city.sakai.lg.jp/shisei/gaiyo/shozaichi.html',
    publisher: 'Sakai City',
  },
  {
    code: '2714431',
    facility: '堺市西区役所',
    address: '堺市西区鳳東町6丁600番地',
    sourceUrl: 'https://www.city.sakai.lg.jp/shisei/gaiyo/shozaichi.html',
    publisher: 'Sakai City',
  },
  {
    code: '2714531',
    facility: '堺市南区役所',
    address: '堺市南区桃山台1丁1番1号',
    sourceUrl: 'https://www.city.sakai.lg.jp/shisei/gaiyo/shozaichi.html',
    publisher: 'Sakai City',
  },
  {
    code: '2714631',
    facility: '堺市北区役所',
    address: '堺市北区新金岡町5丁1番4号',
    sourceUrl: 'https://www.city.sakai.lg.jp/shisei/gaiyo/shozaichi.html',
    publisher: 'Sakai City',
  },
  {
    code: '2714730',
    facility: '堺市消防局美原消防署',
    address: '堺市美原区黒山6番地1',
    sourceUrl: 'https://www.city.sakai.lg.jp/shisei/gaiyo/annai/gyoseikiko/shobo/mihara.html',
    publisher: 'Sakai City Fire Bureau',
  },
];

for (const update of updates) {
  const station = data.stations.find((candidate) => candidate.code === update.code);
  if (!station) throw new Error(`Missing station ${update.code}`);
  if (station.facilityNameJa !== update.facility) {
    throw new Error(`${update.code}: expected ${update.facility}, found ${station.facilityNameJa}`);
  }
  if (station.publishedAddressJa) throw new Error(`${update.code}: address already present`);

  station.publishedAddressJa = update.address;
  station.metadataStatus = 'Official prefectural placement and official address';
  station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies ${update.facility} as the host site; ${update.publisher} publishes the facility address.`;
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), update.sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
