import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = [
  {
    code: '4420834', nameJa: '竹田市会々',
    facilityNameJa: '竹田市役所', facilityNameEn: 'Taketa City Hall',
    publishedAddressJa: '大分県竹田市大字会々1650番地',
    placementLocalityJa: '竹田市大字会々1650番地',
    note: "Taketa City's earthquake disaster plan, part 2 chapter 3 section 1, printed page 震災２部-46 (PDF page 93), explicitly identifies the instrumental intensity meter within Taketa City Hall (竹田市役所内の計測震度計) as the trigger for public warnings. The city's current City Hall contact guide publishes 大分県竹田市大字会々1650番地. This identifies the local-government Aiai station, not the separate Taketa Elementary School instrument at 1637. The host identity comes from the explicit meter placement statement, not the station locality or coordinate proximity. No floor, room, or precise within-site position is asserted.",
    sourceUrls: [
      'https://www.city.taketa.oita.jp/material/files/group/2/bousai0004.pdf',
      'https://www.city.taketa.oita.jp/shisei_machizukuri/annai/1/index.html',
    ],
  },
  {
    code: '4420231', nameJa: '別府市上野口町',
    facilityNameJa: '別府市役所敷地', facilityNameEn: 'Beppu City Hall grounds',
    publishedAddressJa: '大分県別府市上野口町1番15号',
    placementLocalityJa: '別府市上野口町1番15号',
    note: "Beppu City's earthquake/tsunami disaster plan, chapter 1 section 3 item (3), printed/PDF page 9, explicitly distinguishes three measurement hosts: Miyazono housing-estate park and Amama Elementary School for JMA, and the City Hall grounds for Oita Prefecture's meter installed in October 1996. This uniquely identifies the local-government Kaminoguchicho station. The city's current City Hall facility guide, updated 14 September 2026, publishes 上野口町1番15号, with the full prefectural address in its municipal contact block. The record is based on the explicit installation list, not a councillor's question, matching locality alone, or proximity. No current within-site position is asserted.",
    sourceUrls: [
      'https://www.city.beppu.oita.jp/doc/bousai_syoubou/bousaijyouhou/bousaikeikaku/01/01.pdf',
      'https://www.city.beppu.oita.jp/sisetu/siyakusyo_syuttyousyo/04siyakusyo_04-01beppu.html',
    ],
  },
];
for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa) throw new Error(`Identity mismatch: ${update.code}`);
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Unexpected existing placement: ${update.code}`);
  }
  const { code, nameJa, sourceUrls, ...placement } = update;
  Object.assign(station, placement, {
    placementPrecision: 'exact_address',
    metadataStatus: 'Official disaster-plan instrument placement and municipal facility address',
  });
  for (const url of sourceUrls) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Taketa/Beppu City Hall intensity-address updates.`);
