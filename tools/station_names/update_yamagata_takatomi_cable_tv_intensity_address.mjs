import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '2121532');
if (!station || station.nameJa !== '岐阜山県市高富') throw new Error('Yamagata Takatomi station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Yamagata Takatomi placement.');
}
Object.assign(station, {
  facilityNameJa: '山県市有線テレビ局敷地',
  facilityNameEn: 'Yamagata Cable Television Station grounds',
  publishedAddressJa: '岐阜県山県市高富2203番地',
  placementLocalityJa: '山県市高富2203番地',
  placementPrecision: 'exact_address',
  metadataStatus: 'Manufacturer instrument-installation case study and published host address',
  note: "Panasonic's primary installation case study 山県市有線テレビ directly identifies an outdoor intensity meter at Yamagata's cable-TV station as part of Gifu Prefecture's intensity-information network and describes the replacement meter's conduit installation; its captions explicitly identify the outdoor meter and wiring from the building. Yamagata City's 2012 municipal profile, printed page 11 (PDF page 12), directly lists 有線テレビ（CCY） at 高富2203番地. CCN's current Yamagata Shop guide and its 2024 event publication independently publish the same parcel address, the latter with the full 岐阜県山県市高富2203 address. CCN's 28 March 2022 signed-transfer announcement documents succession to the municipal cable-TV business from 1 April 2022, rather than closure of that business; the current shop address corroborates continuing use of the host site. The facility label preserves the cable-TV host identity documented by the instrument source, not a claim that the municipality still operates the business or that the seismic instrument's ownership transferred to CCN. The manufacturer case study is not dated here and is not represented as a fresh 2026 inspection. No exact within-site meter position is asserted, and this evidence is not applied to the separate Daimon station.",
});
for (const url of [
  'https://www2.panasonic.biz/jp/works/building/detail/id/120020000/',
  'https://www.city.yamagata.gifu.jp/uploaded/attachment/2708.pdf',
  'https://www.ccn-catv.co.jp/company/showroom/yamagata/',
  'https://www.ccn-catv.co.jp/campaign/032159.html',
  'https://www.ccn-catv.co.jp/company/news/020786.html',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Yamagata Takatomi cable-TV intensity-address update.`);
