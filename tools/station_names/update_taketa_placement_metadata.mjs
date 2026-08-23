import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find(s => s.code === '4420820');
if (!station) throw new Error('Station 4420820 not found');
if (!station.publishedAddressJa) {
  station.publishedAddressJa = '大分県竹田市大字会々1637番地';
  station.facilityNameJa = '竹田市立竹田小学校';
  station.placementPrecision = 'exact_address';
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls || []),
    'https://www.jishin.go.jp/main/kansoku/kansoku19/strong_og_2019.html',
    'https://www.city.taketa.oita.jp/section/reiki/reiki_honbun/r335RG00000244.html'
  ])];
  station.metadataStatus = 'source_verified';
  station.note = 'NIED identifies OITH06 at Taketa Elementary School; the city school-location ordinance verifies the facility address.';
  data.coverage.publishedAddresses++;
  data.coverage.exactPlacementAddressUpdates++;
  data.coverage.localityPlacementRecords--;
}
fs.writeFileSync(path, JSON.stringify(data, null, 2) + '\n');
console.log('updated 4420820');
