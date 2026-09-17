import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const incorrectSource = 'https://www.data.jma.go.jp/eqev/data/kyoshin/jma-shindo.html';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20260217061734_0_Z__J_JPSP_20260217061600_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const codes = ['1110130', '1123230', '1123231', '1123232', '1123731', '1138530', '4630446', '4630448'];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const code of codes) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station || !station.sourceUrls.includes(incorrectSource) || !station.publishedAddressJa) {
    throw new Error(`Unexpected starting state for ${code}.`);
  }

  station.sourceUrls = [...new Set(station.sourceUrls.map((url) => url === incorrectSource ? source : url))];
  station.metadataStatus = 'Official JMA current station-code notice and published address';
  station.note = station.note.replace('JMA’s current observation-point list', 'JMA’s current station-code notice');
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
