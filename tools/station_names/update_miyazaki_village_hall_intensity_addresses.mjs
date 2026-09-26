import { readFileSync, writeFileSync } from 'node:fs';

const inputPath = 'outputs/station-name-audit/station_metadata_sources.json';
const apply = process.argv.includes('--apply');
const placementSource = 'https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf';
const updates = [
  {
    code: '4540332', nameJa: '西米良村村所', facilityNameJa: '西米良村役場', facilityNameEn: 'Nishimera Village Office',
    publishedAddressJa: '宮崎県児湯郡西米良村大字村所15',
    addressSource: 'https://www.vill.nishimera.lg.jp/village/aboutus',
  },
  {
    code: '4542930', nameJa: '諸塚村家代', facilityNameJa: '諸塚村役場', facilityNameEn: 'Morotsuka Village Office',
    publishedAddressJa: '宮崎県東臼杵郡諸塚村大字家代2683',
    addressSource: 'https://www.vill.morotsuka.miyazaki.jp/',
  },
  {
    code: '4543032', nameJa: '椎葉村下福良', facilityNameJa: '椎葉村役場', facilityNameEn: 'Shiiba Village Office',
    publishedAddressJa: '宮崎県東臼杵郡椎葉村大字下福良1747-20',
    addressSource: 'https://www.vill.shiiba.miyazaki.jp/affair/pdf/kouhou-674.pdf',
  },
];

let raw = readFileSync(inputPath, 'utf8');
const newline = raw.includes('\r\n') ? '\r\n' : '\n';
for (const update of updates) {
  const data = JSON.parse(raw);
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
    note: `The Miyazaki seismic-network catalogue places this station on ${update.facilityNameJa} grounds and gives this address; the municipality publishes the same facility address.`,
    placementPrecision: 'exact_address',
  };
  const marker = `"code": "${update.code}"`;
  const codeIndex = raw.indexOf(marker);
  if (codeIndex < 0 || raw.indexOf(marker, codeIndex + 1) >= 0) throw new Error(`Could not uniquely locate ${update.code}`);
  const startIndex = raw.lastIndexOf('    {', codeIndex);
  const endIndex = raw.indexOf('\n    }', codeIndex);
  if (startIndex < 0 || endIndex < 0) throw new Error(`Could not find ${update.code} record boundaries`);
  raw = `${raw.slice(0, startIndex)}${JSON.stringify(replacement, null, 2).replaceAll('\n', newline)}${raw.slice(endIndex + '\n    }'.length)}`;
}

const verified = JSON.parse(raw);
for (const update of updates) {
  const station = verified.stations.find((item) => item.code === update.code);
  if (!station || station.facilityNameJa !== update.facilityNameJa || station.publishedAddressJa !== update.publishedAddressJa || station.placementPrecision !== 'exact_address') throw new Error(`Post-write validation failed for ${update.code}`);
}
if (apply) writeFileSync(inputPath, raw);
console.log(`${apply ? 'Updated' : 'Validated'} ${updates.length} Miyazaki village-office placements`);
