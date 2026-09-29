import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '4446131';
const placementSource = 'https://www.jma.go.jp/jma/kishou/books/saigaiji/saigaiji_2016/saigaiji_201601.pdf';
const addressSource = 'https://www.town.kokonoe.oita.jp/docs/2017012000018/';
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
const station = data.stations.find((item) => item.code === code);
if (!station || station.nameJa !== '九重町後野上' || station.prefectureJa !== '大分県') throw new Error(`Unexpected station identity for ${code}`);
if (station.facilityNameJa !== null || station.publishedAddressJa !== null || station.placementPrecision !== 'municipality_or_ward') throw new Error(`Unexpected starting placement fields for ${code}`);

const replacement = {
  ...station,
  facilityNameJa: '九重町役場',
  facilityNameEn: 'Kokonoe Town Hall',
  publishedAddressJa: '大分県玖珠郡九重町大字後野上8-1',
  metadataStatus: 'Official seismic-intensity-meter facility placement',
  sourceUrls: [...new Set([...station.sourceUrls, placementSource, addressSource])],
  note: 'JMA identifies the Kokonoe Town Hall building as the seismic-intensity-meter site for the station. Kokonoe Town confirms that its current town hall is at 後野上8-1.',
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
