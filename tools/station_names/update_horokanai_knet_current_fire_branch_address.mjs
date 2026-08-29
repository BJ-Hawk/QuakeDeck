import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '0147220');

if (!station) throw new Error('Station 0147220 was not found.');
if (station.publishedAddressJa !== '雨竜郡幌加内町字平和4608-9(士別地方消防組合深川消防署\n幌加内支署)') {
  throw new Error(`Station 0147220 has an unexpected current address: ${station.publishedAddressJa}`);
}

station.facilityNameJa = '士別地方消防事務組合消防署幌加内支署';
station.publishedAddressJa = '北海道雨竜郡幌加内町字平和4608番地74';
station.metadataStatus = 'Current official Hokkaido seismic-observation and fire-authority confirmation';
station.note = 'Hokkaido’s current regional seismic-observation table places this K-NET station at the Horokanai Fire Branch. The fire authority publishes the branch at the same address; this corrects the older Deep Chikuma Fire Department address in the prior station table.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.kamikawa.pref.hokkaido.lg.jp/fs/8/2/6/5/3/9/9/_/03_%E4%B8%8A%E5%B7%9D%E7%B7%8F%E5%90%88%E6%8C%AF%E8%88%88%E5%B1%80%E5%9C%B0%E5%9F%9F%E7%81%BD%E5%AE%B3%E5%AF%BE%E7%AD%96%E8%A6%81%E7%B6%B1(%E8%B3%87%E6%96%99%E7%B7%A8)%E3%80%90R5.4%E6%94%B9%E6%AD%A3%E3%80%91.pdf', 'https://www.town.wassamu.hokkaido.jp/fire-department/introduction/'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
