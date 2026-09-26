import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const updates = [
  {
    code: '4520336',
    nameJa: '延岡市東本小路',
    facilityNameJa: '延岡市役所',
    facilityNameEn: 'Nobeoka City Hall',
    publishedAddressJa: '宮崎県延岡市東本小路2-1',
    addressSource: 'https://www.city.nobeoka.miyazaki.jp/soshiki/6/1698.html',
    note: 'The Miyazaki seismic-network catalogue places the station on Nobeoka City Hall grounds at 東本小路2-1. Nobeoka City confirms that its current city hall remains at the same address.',
  },
  {
    code: '4540531',
    nameJa: '川南町川南',
    facilityNameJa: '川南町役場',
    facilityNameEn: 'Kawaminami Town Hall',
    publishedAddressJa: '宮崎県児湯郡川南町大字川南13680-1',
    addressSource: 'https://www.town.kawaminami.miyazaki.jp/soshiki/5/',
    note: 'The Miyazaki seismic-network catalogue places the station on Kawaminami Town Hall grounds at 川南13680-1. Kawaminami Town confirms the current town hall address as 川南13680-1.',
  },
];
const raw = readFileSync(inputPath, 'utf8');
const data = JSON.parse(raw);
let output = raw;
for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa || station.prefectureJa !== '宮崎県') throw new Error(`Unexpected station identity for ${update.code}`);
  if (station.facilityNameJa !== null || station.publishedAddressJa !== null || station.placementPrecision !== 'municipality_or_ward') throw new Error(`Unexpected starting placement fields for ${update.code}`);
  const replacement = {
    ...station,
    facilityNameJa: update.facilityNameJa,
    facilityNameEn: update.facilityNameEn,
    publishedAddressJa: update.publishedAddressJa,
    metadataStatus: 'Official seismic-network facility placement',
    sourceUrls: [...new Set([...station.sourceUrls, placementSource, update.addressSource])],
    note: update.note,
    placementPrecision: 'exact_address',
  };
  const marker = `"code": "${update.code}"`;
  const codeIndex = output.indexOf(marker);
  if (codeIndex < 0 || output.indexOf(marker, codeIndex + 1) >= 0) throw new Error(`Could not uniquely locate ${update.code}`);
  const startIndex = output.lastIndexOf('    {', codeIndex);
  const endIndex = output.indexOf('\n    }', codeIndex);
  if (startIndex < 0 || endIndex < 0) throw new Error(`Could not find ${update.code} record boundaries`);
  const newline = output.includes('\r\n') ? '\r\n' : '\n';
  output = `${output.slice(0, startIndex)}${JSON.stringify(replacement, null, 2).replaceAll('\n', newline)}${output.slice(endIndex + '\n    }'.length)}`;
}
const verified = JSON.parse(output).stations;
for (const update of updates) {
  const station = verified.find((item) => item.code === update.code);
  if (!station || station.facilityNameJa !== update.facilityNameJa || station.publishedAddressJa !== update.publishedAddressJa || station.placementPrecision !== 'exact_address') throw new Error(`Post-write validation failed for ${update.code}`);
}
if (apply) writeFileSync(inputPath, output);
console.log(`${apply ? 'Updated' : 'Validated'} ${updates.map((update) => update.code).join(', ')}`);
