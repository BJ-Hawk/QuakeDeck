import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.city.chikugo.lg.jp/shisei/_5644/_7347/_31889.html?media=pc';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '4021130');

if (!station) throw new Error('Station 4021130 was not found.');

station.metadataStatus = 'Official Chikugo City locality-only confirmation';
station.note = 'Chikugo City confirms that its Fukuoka Prefecture meter is published only as Chikugo City Yamanoui, withholding the exact installation location to prevent tampering.';
station.placementLocalityJa = '筑後市山ノ井';
station.placementPrecision = 'municipality_or_ward';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
