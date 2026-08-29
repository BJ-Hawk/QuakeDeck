import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const code = '0821432';
const station = data.stations.find((item) => item.code === code);

if (!station || station.nameJa !== '高萩市本町') {
  throw new Error(`Expected ${code} 高萩市本町`);
}
if (station.publishedAddressJa || station.facilityNameJa) {
  throw new Error(`${code} already has placement data`);
}

const sourceUrl = 'https://www.city.takahagi.ibaraki.jp/kurashi/shoubou_bousai_bouhan/bousai/page005533.html';
station.publishedAddressJa = '茨城県高萩市本町1丁目100番地1';
station.facilityNameJa = '高萩市役所';
station.metadataStatus = 'Official municipal seismic-meter placement and address';
station.placementPrecision = 'exact_address';
delete station.placementLocalityJa;
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
station.note = 'Takahagi City states that Ibaraki Prefecture installed the city’s intensity meter at City Hall (Honcho 1-100-1).';

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
