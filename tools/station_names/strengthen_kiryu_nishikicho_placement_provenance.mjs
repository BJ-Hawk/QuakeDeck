import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const code = '1020301';
const jmaSource = 'https://www.data.jma.go.jp/eqev/data/kyoshin/jma-shindo.html';
const municipalSource = 'https://www.city.kiryu.lg.jp/anzen/bousai/joho/1020089.html';
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
const station = data.stations.find((item) => item.code === code);
if (!station || station.nameJa !== '桐生市錦町' || station.prefectureJa !== '群馬県') throw new Error(`Unexpected station identity for ${code}`);
if (station.facilityNameJa !== '桐生市役所公用車駐車場' || station.publishedAddressJa !== '桐生市錦町2-10（桐生市役所公用車駐車場）' || station.placementPrecision !== 'exact_address') throw new Error(`Unexpected starting placement fields for ${code}`);
if (station.metadataStatus !== 'Official JMA published address' || station.note !== 'Current JMA observation-point table publishes this address.' || !station.sourceUrls.includes(jmaSource) || station.sourceUrls.includes(municipalSource)) throw new Error(`Unexpected starting provenance for ${code}`);

const replacement = {
  ...station,
  metadataStatus: 'Official JMA and municipal facility placement',
  sourceUrls: [...new Set([...station.sourceUrls, municipalSource])],
  note: 'JMA publishes the full address. Kiryu City directly identifies the station at the Kiryu City Hall vehicle-parking site.',
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
if (!verified || verified.metadataStatus !== replacement.metadataStatus || verified.note !== replacement.note || !verified.sourceUrls.includes(jmaSource) || !verified.sourceUrls.includes(municipalSource)) throw new Error(`Post-write validation failed for ${code}`);
if (apply) writeFileSync(inputPath, output);
console.log(`${apply ? 'Updated' : 'Validated'} ${code}: provenance strengthened.`);
