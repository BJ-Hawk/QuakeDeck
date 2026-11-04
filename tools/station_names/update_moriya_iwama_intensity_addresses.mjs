import fs from 'node:fs';

const apply = process.argv.includes('--apply');
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = [
  {
    code: '0822431', nameJa: '守谷市大柏',
    facilityNameJa: '守谷市役所', facilityNameEn: 'Moriya City Hall',
    publishedAddressJa: '茨城県守谷市大柏950番地の1',
    placementLocalityJa: '守谷市大柏950番地の1',
    metadataStatus: 'Official council-response facility placement',
    note: "Moriya City Council's 15 June 2015 record, Watanabe Hidekazu's instrument-location question, includes Economic and Living Affairs Department Director Saka Hiroshi's explicit answer that the prefecture's intensity meter is installed at City Hall, sends its measurements via the prefecture to reporting organizations, and was replaced in fiscal 2011. His second answer explains that the prefectural communications equipment at City Hall determined the placement; this is the official answer, not the councillor's speculation about nearby ground conditions. The city's current City Hall facility page publishes 茨城県守谷市大柏950番地の1. No within-site position is inferred.",
    sourceUrls: [
      'https://moriya.gijiroku.com/g07_Shitsumon.asp?KAIGI=43&Sflg=2',
      'https://moriya.gijiroku.com/voices/cgi/voiweb.exe?ACT=200&KGNO=236&FINO=573&HUID=84263&UNID=K_H2706150004229',
      'https://moriya.gijiroku.com/voices/cgi/voiweb.exe?ACT=203&KENSAKU=0&SORT=0&KTYP=2,3,2&KGTP=1,2,1&FINO=573&HATSUGENMODE=1&HYOUJIMODE=0&HUID=84263&STYLE=0',
      'https://www.city.moriya.ibaraki.jp/shisetsu/shiyakusho/1004217/1004218.html',
    ],
  },
  {
    code: '0821632', nameJa: '笠間市下郷',
    facilityNameJa: '笠間市役所岩間支所', facilityNameEn: 'Kasama City Hall Iwama Regional Branch',
    publishedAddressJa: '茨城県笠間市下郷5140番地',
    placementLocalityJa: '笠間市下郷5140番地',
    metadataStatus: 'Official council-response facility placement',
    note: "Kasama City Council's 11 June 2010 record, printed pages 66-67 (PDF pages 20-21), directly identifies the prefectural intensity instruments at the main hall and both regional branches. General Affairs Director Hanawa Sakae's answer explicitly confirms all three installations; the identification is not based only on the preceding question. The contemporary September 2011 office guide places Iwama Branch inside Citizen Center Iwama at 笠間市下郷5140番地, and the city's current transport guide and English office directory publish the same address. The official English directory calls this office Iwama Regional Branch. This evidence supports the Shimogo station, not the relocated Kasama branch station.",
    sourceUrls: [
      'https://www.city.kasama.lg.jp/data/doc/1314784015_doc_56_2.pdf',
      'https://www.city.kasama.lg.jp/data/doc/1315359550_doc_13_0.pdf',
      'https://www.city.kasama.lg.jp/page/page000167.html',
      'https://www.city.kasama.lg.jp/page/page011843.html',
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
  Object.assign(station, placement, { placementPrecision: 'exact_address' });
  for (const url of sourceUrls) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);
}
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${updates.length} Moriya/Iwama intensity-address updates.`);
