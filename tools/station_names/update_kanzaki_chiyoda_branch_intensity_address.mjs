import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const station = data.stations.find((item) => item.code === '4121034');
if (!station || station.nameJa !== '神埼市千代田') throw new Error('Kanzaki Chiyoda station identity mismatch.');
if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
  throw new Error('Unexpected existing Kanzaki Chiyoda placement.');
}
Object.assign(station, {
  facilityNameJa: '神埼市千代田支所（千代田交流センター）',
  facilityNameEn: 'Kanzaki City Chiyoda Branch Office (Chiyoda Community Exchange Center)',
  publishedAddressJa: '佐賀県神埼市千代田町直鳥166番地1',
  placementLocalityJa: '神埼市千代田町直鳥166番地1',
  placementPrecision: 'exact_address',
  metadataStatus: 'Official JMA field-survey host identification and municipal facility address',
  note: "JMA Technical Report No. 135 (2018), chapter 2, section 2.1.3.4 item 74, printed page 88 (PDF page 6 of CHAPTER2.pdf), explicitly identifies the intensity meter within Kanzaki City's Chiyoda General Branch Office (神埼市千代田総合支所内に設置した震度計) in its 2016 Kumamoto-earthquake field survey. Kanzaki City's current office guide directly identifies 千代田交流センター（千代田支所） at 神埼市千代田町直鳥166番地1; its current polling-place list independently publishes that same branch address. The city's March 2019 draft building-reuse plan, printed pages 1 and 3 (PDF pages 3 and 5), documents continued use of the 1995 Chiyoda branch building and publishes the same parcel address, distinguishing reuse of this building from replacement of the separate main City Hall. The instrument host is established by JMA's explicit placement statement, not proximity, the observation locality alone, or a generic network diagram. The survey's nearby-wall description is historical and is not asserted to identify the meter's current room, floor, or exact within-site position. The English facility label is descriptive, not claimed as an official English title. No placement at the separate main City Hall or Sefuri station is inferred.",
});
for (const url of [
  'https://www.jma.go.jp/jma/kishou/books/gizyutu/135/CHAPTER2.pdf',
  'https://www.city.kanzaki.saga.jp/main/25.html',
  'https://www.city.kanzaki.saga.jp/main/10542.html',
  'https://www.city.kanzaki.saga.jp/site_files/file/2018/201808/p1cjsp9f44bdhoa81smf1i843l46.pdf',
]) {
  if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} Kanzaki Chiyoda branch intensity-address update.`);
