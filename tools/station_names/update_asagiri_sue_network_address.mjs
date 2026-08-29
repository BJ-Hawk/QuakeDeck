import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://data.bodik.jp/dataset/430005_00245/resource/dad15967-bb1a-487f-ab7b-908ff373156d';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '4351433');

if (!station) throw new Error('Station 4351433 was not found.');

station.publishedAddressJa = 'あさぎり町須恵1227';
station.facilityNameJa = 'あさぎり町須恵支所';
station.metadataStatus = 'Kumamoto Prefecture seismic-intensity installation list (2022)';
station.note = 'Kumamoto Prefecture’s official installation list directly identifies the station as installed at Asagiri Town Sue Branch Office and publishes its address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
delete station.placementLocalityJa;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
