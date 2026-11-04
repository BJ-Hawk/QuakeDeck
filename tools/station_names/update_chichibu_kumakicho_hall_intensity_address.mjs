import fs from 'node:fs';
import assert from 'node:assert/strict';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const before = structuredClone(data);
const code = '1120734';
const station = data.stations.find((row) => row.code === code);
assert.ok(station);
assert.equal(station.nameJa, '秩父市熊木町');
assert.equal(station.municipalityCode, '1120700');
assert.equal(station.providerJa, '地方公共団体');
assert.equal(data.stations.filter((row) => row.municipalityCode === '1120700' && row.nameJa.includes('熊木町')).length, 1);
assert.equal(station.placementPrecision, 'municipality_or_ward');
assert.ok(!station.facilityNameJa && !station.facilityNameEn && !station.publishedAddressJa);
Object.assign(station, {
  facilityNameJa: '秩父市役所',
  facilityNameEn: 'Chichibu City Hall',
  publishedAddressJa: '埼玉県秩父市熊木町8番15号',
  placementLocalityJa: '秩父市熊木町8番15号',
  placementPrecision: 'exact_address',
  metadataStatus: 'Direct adopted municipal disaster-plan instrument-host statement and current official hall address',
});
station.note += ' The currently published Chichibu disaster plan, fiscal 2023 revision, volume 3 災害応急対策計画, printed page 3-37 / PDF page 39, explicitly states that local intensity is obtained from the intensity meter installed at City Hall. The entire 218-page volume was text-searched; its complete relevant page was text-read and visually inspected. The separate cover/contents volume cover was text-read and visually inspected and identifies 令和5年度修正. Although the PDF metadata title contains working-document wording, the live official index 4102.html links this file as the revised adopted plan, not a public-comment draft. The current official department directory 6214.html independently names 秩父市役所 and publishes 秩父市熊木町8番15号 in its facility/location section, with the full prefecture also published on the municipal site. Composite matching uses the explicit City Hall instrument-host statement and the uniquely matching 熊木町 locality among bundled Chichibu stations, not coordinates, proximity, or a department contact alone. This does not assign the separate Yoshida, Arakawa or Otaki stations to their branch offices. The source supports the City Hall site address, not a room, floor, specific building wing, exact within-site sensor position, installation date or physical transfer date. No source is claimed to print the seven-digit code, and this is not a fresh 2026 physical inspection. The English facility label is descriptive; approved station names remain unchanged.';
const base = 'https://www.city.chichibu.lg.jp/secure/6657/';
for (const url of [
  base + encodeURIComponent('【R5改正反映】03 秩父市地域防災計画（応急対策）.pdf'),
  base + encodeURIComponent('【R5改正反映】00 秩父市地域防災計画（表紙目次）.pdf'),
  'https://www.city.chichibu.lg.jp/4102.html',
  'https://www.city.chichibu.lg.jp/6214.html',
]) if (!station.sourceUrls.includes(url)) station.sourceUrls.push(url);

const allowed = new Set(['facilityNameJa', 'facilityNameEn', 'publishedAddressJa', 'placementLocalityJa', 'placementPrecision', 'metadataStatus', 'note', 'sourceUrls']);
assert.equal(data.stations.length, 4360);
assert.equal(new Set(data.stations.map((row) => row.code)).size, 4360);
assert.deepEqual(Object.keys(data), Object.keys(before));
for (const key of Object.keys(data)) if (key !== 'stations') assert.deepEqual(data[key], before[key]);
for (let i = 0; i < data.stations.length; i++) {
  const row = data.stations[i], old = before.stations[i];
  assert.equal(row.code, old.code);
  if (row.code !== code) assert.deepEqual(row, old);
  else {
    for (const key of new Set([...Object.keys(row), ...Object.keys(old)])) if (!allowed.has(key)) assert.deepEqual(row[key], old[key]);
    for (const url of old.sourceUrls) assert.ok(row.sourceUrls.includes(url));
    assert.ok(row.note.startsWith(old.note));
  }
  assert.equal(row.placementPrecision === 'exact_address', Boolean(row.publishedAddressJa), `Address/precision mismatch ${row.code}`);
}
const apply = process.argv.includes('--apply');
if (apply) fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`${apply ? 'Applied' : 'Would apply'} ${code} Chichibu City Hall exact address; protected-field and address/precision checks passed.`);
