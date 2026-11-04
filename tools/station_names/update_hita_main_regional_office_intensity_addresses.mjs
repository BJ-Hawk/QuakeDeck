import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const plan = 'https://www.city.hita.oita.jp/uploaded/attachment/8733.pdf';
const planIndex = 'https://www.city.hita.oita.jp/site/bousai/2331.html';
const branchGuide = 'https://www.city.hita.oita.jp/soshiki/1/2179.html';
const updates = [
  {
    code: '4420433', nameJa: '日田市上津江町',
    facilityNameJa: '日田市上津江振興局', facilityNameEn: 'Hita City Kamitsue Regional Office',
    publishedAddressJa: '大分県日田市上津江町川原2710番地',
    placementLocalityJa: '日田市上津江町川原2710番地',
    addressUrl: branchGuide,
    qualification: 'The branch guide directly lists Kamitsue Regional Office at 上津江町川原2710番地.',
  },
  {
    code: '4420435', nameJa: '日田市田島',
    facilityNameJa: '日田市役所本庁舎', facilityNameEn: 'Hita City Hall Main Office',
    publishedAddressJa: '大分県日田市田島2丁目6番1号',
    placementLocalityJa: '日田市田島2丁目6番1号',
    addressUrl: 'https://www.city.hita.oita.jp/soshiki/1/1976.html',
    qualification: 'The current City Hall access guide directly publishes 田島2丁目6番1号 for the main hall, distinct from the separate JMA station in Sanbonmatsu.',
  },
  {
    code: '4420438', nameJa: '日田市中津江村栃野',
    facilityNameJa: '日田市中津江振興局', facilityNameEn: 'Hita City Nakatsue Regional Office',
    publishedAddressJa: '大分県日田市中津江村栃野2357番地1',
    placementLocalityJa: '日田市中津江村栃野2357番地1',
    addressUrl: branchGuide,
    qualification: 'The branch guide publishes 中津江村栃野2357番地1 and explicitly identifies the office move to the former Nakatsue Elementary School site on 1 August 2015. The February 2026 meter-placement statement postdates that move; the former 栃野353 office address is not used. The separate JMA Aise station is not assigned from this evidence.',
  },
  {
    code: '4420439', nameJa: '日田市大山町',
    facilityNameJa: '日田市大山振興局', facilityNameEn: 'Hita City Oyama Regional Office',
    publishedAddressJa: '大分県日田市大山町西大山3494番地1',
    placementLocalityJa: '日田市大山町西大山3494番地1',
    addressUrl: branchGuide,
    qualification: 'The branch guide publishes 大山町西大山3494番地1 and explicitly identifies the office move to the former Oyama Elementary School site on 1 July 2016. The February 2026 meter-placement statement postdates that move; the former 西大山3545-1 office address is not used.',
  },
  {
    code: '4420440', nameJa: '日田市前津江町',
    facilityNameJa: '日田市前津江振興局', facilityNameEn: 'Hita City Maetsue Regional Office',
    publishedAddressJa: '大分県日田市前津江町大野2189番地5',
    placementLocalityJa: '日田市前津江町大野2189番地5',
    addressUrl: branchGuide,
    qualification: 'The branch guide publishes 前津江町大野2189番地5 and identifies the office move to the former Maetsue Community Center site on 26 April 2021. The fiscal 2021 activity results, printed page 102 (PDF page 62), item 6 前津江振興局震度計移設事業, explicitly state that relocation of the meter from the old office to the new office was completed and record 1,172,600 yen of work. The former 2189-1 office address is not used. The results report is retained at its current municipal download URL; its former /material/files/ URL returned 404 during verification.',
    extraUrls: ['https://www.city.hita.oita.jp/uploaded/attachment/6897.pdf'],
  },
];
for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa) throw new Error(`Identity mismatch: ${update.code}`);
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Unexpected existing placement: ${update.code}`);
  }
  const { code, nameJa, addressUrl, qualification, extraUrls = [], ...placement } = update;
  Object.assign(station, placement, {
    placementPrecision: 'exact_address',
    metadataStatus: 'Official current network placement statement and municipal facility address',
    note: `Hita City's February 2026 earthquake disaster plan, part 2 chapter 4, printed page 57 (PDF page 46 of the separate prevention volume), item (2) 震度計の設置, explicitly states that Oita Prefecture's network intensity meters are installed at City Hall's main office and each regional office (市役所本庁及び各振興局に震度計が設置). The city's plan index, updated 26 March 2026, links this current prevention volume. This is a direct, comprehensive host-class placement statement covering the named main and regional offices, not a generic statement of network participation or a proximity inference. The current municipal facility guides, updated 8 December 2025, supply the individual published addresses. ${qualification} English facility names are descriptive translations of the municipal identities. No current room, floor, or exact within-site meter position is asserted.`,
  });
  for (const url of [plan, planIndex, addressUrl, ...extraUrls]) {
    if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
  }
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Hita main/regional-office intensity-address updates.`);
