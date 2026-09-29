import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '0421535';
const source = 'https://www.pref.miyagi.jp/documents/8097/04-02-2_siryou-honnpenn.pdf';
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
const station = data.stations.find((item) => item.code === code);
if (!station || station.nameJa !== '大崎市田尻' || station.prefectureJa !== '宮城県') throw new Error(`Unexpected station identity for ${code}`);
if (station.facilityNameJa !== '大崎市田尻老人福祉センター' || station.publishedAddressJa !== '大崎市田尻沼部富岡166' || station.placementPrecision !== 'exact_address') throw new Error(`Unexpected starting placement fields for ${code}`);
if (station.note !== 'No exact address or precise provider-station metadata is recorded yet. Exact placement address and facility sourced from Miyagi Prefecture seismic-intensity network list (2025).') throw new Error(`Unexpected starting note for ${code}`);

const replacement = {
  ...station,
  facilityNameEn: 'Osaki City Tajiri Elderly Welfare Center',
  metadataStatus: 'Miyagi Prefecture seismic-intensity network facility placement',
  sourceUrls: [...new Set([...station.sourceUrls, source])],
  note: 'Miyagi Prefecture identifies the station at the Osaki City Tajiri Elderly Welfare Center, 田尻沼部富岡166, and records that the station occupies the facility grounds.',
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
if (!verified || verified.facilityNameEn !== replacement.facilityNameEn || verified.note !== replacement.note || verified.publishedAddressJa !== station.publishedAddressJa || verified.placementPrecision !== 'exact_address') throw new Error(`Post-write validation failed for ${code}`);
if (apply) writeFileSync(inputPath, output);
console.log(`${apply ? 'Updated' : 'Validated'} ${code}: ${replacement.facilityNameEn}`);
