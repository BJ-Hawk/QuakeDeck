import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.city.takahagi.ibaraki.jp/kurashi/shoubou_bousai_bouhan/bousai/page005533.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '0821420');

if (!station) throw new Error('Station 0821420 was not found.');
if (station.nameJa !== '高萩市安良川') {
  throw new Error(`Station 0821420 identity mismatch: ${station.nameJa}`);
}
if (station.publishedAddressJa !== '茨城県高萩市大字安良川1002') {
  throw new Error(`Station 0821420 address mismatch: ${station.publishedAddressJa}`);
}

station.facilityNameJa = '防災倉庫（市民体育館西側）';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
