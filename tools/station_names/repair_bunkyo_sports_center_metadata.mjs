import { readFileSync, writeFileSync } from 'node:fs';

const path = new URL('../../outputs/station-name-audit/station_metadata_sources.json', import.meta.url);
const data = JSON.parse(readFileSync(path, 'utf8'));
const yamanashiNetworkSource = 'https://www.pref.yamanashi.jp/shobo/documents/88770017458.pdf';
const yamanashiNetworkCodes = new Set(['1920531', '1920533', '1920534', '1920834', '1920835', '1920836', '1920837', '1920838', '1921131', '1921135', '1921136', '1921139', '1921141', '1921232', '1921332', '1921335', '1921430', '1921431', '1921432', '1938430']);
const repairs = new Map([
  ['1310531', 'The official JMA observation-point label identifies Bunkyo Sports Center; the ward publishes the facility address.'],
  ['1920730', 'The official city notice states that this seismic-intensity meter is beside the city hall’s southwest entrance; the city publishes the facility address.']
  ,['1920834', 'The city’s seismic-network agreement identifies the Kōsai Branch as a host site; the official facility directory gives its exact address.']
  ,['1920835', 'The city’s seismic-network agreement identifies the Shirane Branch as a host site; the official facility page gives its exact address.']
  ,['1920836', 'The city’s seismic-network agreement identifies city hall as a host site; the official facility page gives its exact address.']
  ,['1920837', 'The city’s seismic-network agreement identifies the Hatta Branch as a host site; the official facility directory gives its exact address.']
  ,['1920838', 'The city’s seismic-network agreement identifies the Wakakusa Branch as a host site; the official facility directory gives its exact address.']
  ,['1920531', 'The prefectural seismic-network diagram identifies the Makioka Branch as a host site; the city publishes the facility address.']
  ,['1920533', 'The prefectural seismic-network diagram identifies Yamanashi City Hall as a host site; the city publishes the facility address.']
  ,['1920534', 'The prefectural seismic-network diagram identifies the Mitomi Branch as a host site; the city publishes the facility address.']
  ,['1943037', 'The JMA monthly report states that the seismic-intensity meter is installed at Ashiwada Branch Office; the town publishes the facility address.']
  ,['1921430', 'The prefectural seismic-network diagram identifies the Toyotomi Branch as a host site; the city publishes the facility address.']
  ,['1921431', 'The prefectural seismic-network diagram identifies the Tamaho Branch as a host site; the city publishes the facility address.']
  ,['1921432', 'The prefectural seismic-network diagram identifies Central City Hall as a host site; the city publishes the facility address.']
  ,['1921131', 'The prefectural seismic-network diagram identifies the Ichinomiya Branch as a host site; the city publishes the facility address.']
  ,['1921135', 'The prefectural seismic-network diagram identifies the Sakaigawa Branch as a host site; the city publishes the facility address.']
  ,['1921136', 'The prefectural seismic-network diagram identifies the Misaka Branch as a host site; the city publishes the facility address.']
  ,['1921139', 'The prefectural seismic-network diagram identifies the Yatsushiro Branch as a host site; the city publishes the facility address.']
  ,['1921141', 'The prefectural seismic-network diagram identifies the Kasugai Branch as a host site; the city publishes the facility address.']
  ,['1921332', 'The prefectural seismic-network diagram identifies the Yamato Branch as a host site; the city publishes the facility address.']
  ,['1921335', 'The prefectural seismic-network diagram identifies the Katsunuma Branch as a host site; the city publishes the facility address.']
  ,['1921232', 'The prefectural seismic-network diagram identifies the Akiyama Branch as a host site; the city publishes the facility address.']
  ,['1936832', 'An official JMA strong-motion table identifies this observation point as Katsuzawa Elementary School; the town publishes the school address.']
  ,['1936530', 'An official JMA strong-motion table identifies this observation point as Minobu Town Hall; the locality label corresponds to the present Minobu Branch, whose exact address the town publishes.']
  ,['1938430', 'The prefectural seismic-network diagram identifies Showa Town as a host site; its only unresolved locality-labelled station is at the town hall locality, and the town publishes the facility address.']
]);
for (const [code, note] of repairs) {
  const station = data.stations.find((item) => item.code === code);
  if (!station) throw new Error(`Station ${code} not found`);
  station.metadataStatus = 'Official facility address';
  station.note = note;
  if (code === '1943037') {
    station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.data.jma.go.jp/eqev/data/gaikyo/monthly/201103/monthly201103.pdf'])];
  }
  if (yamanashiNetworkCodes.has(code)) {
    station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), yamanashiNetworkSource])];
  }
  delete station.placementLocalityJa;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`repaired ${repairs.size} station metadata record(s)`);
