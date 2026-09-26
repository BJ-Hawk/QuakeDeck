import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '4543133';
const placementSource = 'https://www.jma.go.jp/jma/kishou/books/saigaiji/saigaiji_2016/saigaiji_201601.pdf';
const addressSource = 'https://www.town.miyazaki-misato.lg.jp/kiji003214/index.html';
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
const station = data.stations.find((item) => item.code === code);
if (!station || station.nameJa !== '宮崎美郷町田代' || station.prefectureJa !== '宮崎県') throw new Error(`Unexpected station identity for ${code}`);
if (station.facilityNameJa !== null || station.publishedAddressJa !== null || station.placementPrecision !== 'municipality_or_ward') throw new Error(`Unexpected starting placement fields for ${code}`);

const replacement = {
  ...station,
  facilityNameJa: '美郷町役場',
  facilityNameEn: 'Misato Town Hall',
  publishedAddressJa: '宮崎県東臼杵郡美郷町西郷田代1',
  metadataStatus: 'Official intensity-meter facility placement',
  sourceUrls: [...new Set([...station.sourceUrls, placementSource, addressSource])],
  note: 'JMA’s 2016 Kumamoto Earthquake field report states that the intensity meter for 宮崎美郷町田代 is installed in Misato Town Hall. Misato Town’s official address-change notice identifies the current address as 西郷田代1.',
  placementPrecision: 'exact_address',
};
const marker = `"code": "${code}"`;
const codeIndex = raw.indexOf(marker);
if (codeIndex < 0 || raw.indexOf(marker, codeIndex + 1) >= 0) throw new Error(`Could not uniquely locate ${code}`);
const startIndex = raw.lastIndexOf('    {', codeIndex);
const endIndex = raw.indexOf('\n    }', codeIndex);
if (startIndex < 0 || endIndex < 0) throw new Error(`Could not find ${code} record boundaries`);
const newline = raw.includes('\r\n') ? '\r\n' : '\n';
const output = `${raw.slice(0, startIndex)}${JSON.stringify(replacement, null, 2).replaceAll('\n', newline)}${raw.slice(endIndex + '\n    }'.length)}`;
const verified = JSON.parse(output).stations.find((item) => item.code === code);
if (!verified || verified.facilityNameJa !== replacement.facilityNameJa || verified.publishedAddressJa !== replacement.publishedAddressJa || verified.placementPrecision !== 'exact_address') throw new Error(`Post-write validation failed for ${code}`);
if (apply) writeFileSync(inputPath, output);
console.log(`${apply ? 'Updated' : 'Validated'} ${code}: ${replacement.facilityNameEn}`);
