import fs from "node:fs/promises";
import { createReadStream } from "node:fs";
import path from "node:path";
import readline from "node:readline";
import JSZip from "jszip";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const ROOT = path.resolve(import.meta.dirname, "..", "..");
const STATIONS_PATH = path.join(ROOT, "app", "src", "main", "res", "raw", "jma_intensity_stations.json");
const PLACE_NAMES_PATH = path.join(ROOT, "app", "src", "main", "res", "raw", "jma_place_names.json");
const DAABR_PATH = path.join(ROOT, "tools", "source", "mt_town_all.csv");
const OUTPUT_DIR = path.join(ROOT, "outputs", "station-name-audit");
const CACHE_PATH = path.join(import.meta.dirname, "gsi_reverse_geocoder_cache.json");
const PROVIDER_CACHE_PATH = path.join(import.meta.dirname, "official_station_metadata_cache.json");
const OUTPUT_PATH = path.join(OUTPUT_DIR, "ambiguous_station_name_audit.xlsx");
const STATION_SOURCES_PATH = path.join(OUTPUT_DIR, "station_metadata_sources.json");
const PREVIEW_PATH = path.join(OUTPUT_DIR, "ambiguous_station_name_audit_preview.png");
const MAPPING_PREVIEW_PATH = path.join(OUTPUT_DIR, "ambiguous_station_name_mapping_preview.png");
const CANDIDATES_PREVIEW_PATH = path.join(OUTPUT_DIR, "ambiguous_station_candidates_preview.png");
const RESEARCH_PREVIEW_PATH = path.join(OUTPUT_DIR, "ambiguous_station_research_preview.png");
const SOURCES_PREVIEW_PATH = path.join(OUTPUT_DIR, "station_sources_preview.png");
const GSI_ENDPOINT = "https://mreversegeocoder.gsi.go.jp/reverse-geocoder/LonLatToAddress";
const GSI_INFO_URL = "https://www.gsi.go.jp/";
const JMA_STATION_URL = "https://www.data.jma.go.jp/eqev/data/kyoshin/jma-shindo.html";
const NIED_STATION_URL = "https://www.kyoshin.bosai.go.jp/ja/stationlist/";
const NIED_STATION_API_URL = "https://www.kyoshin.bosai.go.jp/ja/stationlist/api/";
const JMA_INTENSITY_MAP_URL = "https://www.data.jma.go.jp/eqev/data/intens-st/";
const JMA_SAPPORO_ENGLISH_URL = "https://www.jma.go.jp/jma/en/photogallery/JICA_training_2024.html";
const SAPPORO_CHUO_FIRE_NAME_URL =
  "https://www.city.sapporo.jp/shobo/chuo/documents/202507mobairu.pdf";
const SAPPORO_FIRE_STATION_ADDRESS_URL =
  "https://www.city.sapporo.jp/ncms/reiki/d1w_reiki_nonframe/H339901010017/H339901010017_j.html";
const SAPPORO_FIRE_METER_URL =
  "https://www.city.sapporo.jp/kensetsu/stn/documents/demaekouza.pdf";
const KUTCHAN_KNET_MAPS_URL = "https://maps.app.goo.gl/4Lo1dhyoBMMEqNRv5";
const JMA_XML_READINGS_URL = "https://xml.kishou.go.jp/20130628_jmaxml_info.pdf";
const JMA_AMEDAS_MASTER_URL = "https://www.jma.go.jp/jma/kishou/know/amedas/ame_master_20250707.pdf";
const DAABR_CATALOG_URL = "https://catalog.registries.digital.go.jp/rc/dataset/ba-o1-000000_g2-000003";

async function repairExcelContentTypes(outputPath) {
  const bytes = await fs.readFile(outputPath);
  const archive = await JSZip.loadAsync(bytes);
  const contentTypes = archive.file("[Content_Types].xml");
  if (!contentTypes) throw new Error("Generated workbook has no [Content_Types].xml part");
  let xml = await contentTypes.async("string");
  xml = xml.replace(
    '<Default Extension="xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml" />',
    '<Default Extension="xml" ContentType="application/xml" /><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml" />',
  );
  archive.file("[Content_Types].xml", xml);
  const workbookRelationships = archive.file("xl/_rels/workbook.xml.rels");
  if (!workbookRelationships) throw new Error("Generated workbook has no workbook relationship part");
  archive.file(
    "xl/_rels/workbook.xml.rels",
    (await workbookRelationships.async("string")).replaceAll('Target="/xl/', 'Target="'),
  );
  for (const fileName of Object.keys(archive.files)) {
    if (!/^xl\/worksheets\/_rels\/sheet\d+\.xml\.rels$/.test(fileName)) continue;
    const relationships = archive.file(fileName);
    archive.file(fileName, (await relationships.async("string")).replaceAll('Target="/xl/tables/', 'Target="../tables/'));
  }
  for (const fileName of Object.keys(archive.files)) {
    if (!/^xl\/worksheets\/sheet\d+\.xml$/.test(fileName)) continue;
    const sheet = archive.file(fileName);
    const sheetXml = await sheet.async("string");
    const repairedSheetXml = sheetXml.replace(
      /(<x:c\b[^>]*?)\s+t="str"([^>]*?)><x:v>([\s\S]*?)<\/x:v><\/x:c>/g,
      '$1 t="inlineStr"$2><x:is><x:t>$3</x:t></x:is></x:c>',
    ).replace(/(<x:c\b[^>]*?)\s+t="str"\s*\/>/g, "$1 />");
    archive.file(fileName, repairedSheetXml);
  }
  await fs.writeFile(outputPath, await archive.generateAsync({ type: "nodebuffer", compression: "DEFLATE" }));
}

function researchedName(localityEn, evidenceUrl, note, method = "Official locality identity") {
  return {
    localityEn,
    confidence: "Researched station identity",
    ready: "Yes",
    evidenceUrl,
    note,
    method,
  };
}

const MANUAL_CONFIRMATIONS = new Map([
  [
    "0110100",
    {
      localityJa: "北２条西",
      localityEn: "Sapporo Regional Meteorological Observatory",
      confidence: "Facility confirmed",
      ready: "Yes",
      evidenceUrl: JMA_SAPPORO_ENGLISH_URL,
      note: "User-approved facility label. JMA publishes the installation address as 札幌市中央区北2条西18-2（札幌管区気象台）; the facility is Sapporo Regional Meteorological Observatory.",
      method: "Official JMA facility identity",
    },
  ],
  [
    "0110140",
    {
      localityJa: "南４条西",
      localityEn: "Sapporo Chuo Fire Station",
      confidence: "Facility confirmed",
      ready: "Yes",
      evidenceUrl: SAPPORO_CHUO_FIRE_NAME_URL,
      note: "User-approved facility label. Sapporo identifies the Chuo Ward intensity as measured at the ward fire station, located at 札幌市中央区南4条西10丁目, and publishes the English name Sapporo Chuo Fire Station.",
      method: "Official Sapporo facility identity",
    },
  ],
  [
    "0140000",
    {
      localityJa: "南１条東",
      localityEn: "Kutchan Special Automated Weather Station",
      confidence: "Facility confirmed",
      ready: "Yes",
      evidenceUrl: JMA_STATION_URL,
      note: "User-approved facility label. JMA publishes the installation address as 虻田郡倶知安町南1条東3-1（倶知安特別地域気象観測所）.",
      method: "User-approved JMA facility identity",
    },
  ],
  [
    "0140020",
    {
      localityJa: "北６条東７丁目",
      localityEn: "K-NET Kutchan",
      confidence: "Station identity confirmed",
      ready: "Yes",
      evidenceUrl: KUTCHAN_KNET_MAPS_URL,
      note: "User-approved station label. NIED identifies the matched provider station as K-NET HKD144 KUCCHAN; Google Street View verifies the physical enclosure on the 北6条東7丁目 parcel.",
      method: "Official NIED station identity",
    },
  ],
  [
    "4320231",
    {
      localityJa: "鏡町内田",
      localityEn: "Kagamimachi Uchida",
      confidence: "Address confirmed",
      ready: "Yes",
      evidenceUrl: "https://www.town.mashiki.lg.jp/bousai/kiji0036632/3_6632_19153_up_cu4d7aru.pdf",
      note: "Official seismic-observation facility listing identifies 鏡町内田453-1 鏡支所; Yatsushiro City independently gives the same branch-office address.",
      method: "Official installation address",
    },
  ],
]);

// These are the official facility identities printed in parentheses in JMA's
// current intensity-observation table.  They are names of installations, not
// operator prefixes; therefore they take precedence over a bare address
// locality when the installation itself is the useful station identity.
const JMA_METEOROLOGICAL_FACILITY_NAMES = new Map([
  ["0110100", "Sapporo Regional Meteorological Observatory"],
  ["0120202", "Hakodate Regional Meteorological Observatory"],
  ["0120401", "Asahikawa Regional Meteorological Observatory"],
  ["0120501", "Muroran Regional Meteorological Observatory"],
  ["0120601", "Kushiro Regional Meteorological Observatory"],
  ["0120700", "Obihiro Weather Observatory"],
  ["0121100", "Abashiri Regional Meteorological Observatory"],
  ["0121402", "Wakkanai Regional Meteorological Observatory"],
  ["0220100", "Aomori Regional Meteorological Observatory"],
  ["0320100", "Morioka Regional Meteorological Observatory"],
  ["0410201", "Sendai Regional Meteorological Observatory"],
  ["0520100", "Akita Regional Meteorological Observatory"],
  ["0620100", "Yamagata Regional Meteorological Observatory"],
  ["0820101", "Mito Regional Meteorological Observatory"],
  ["0820500", "Geomagnetic Observatory"],
  ["0920100", "Utsunomiya Regional Meteorological Observatory"],
  ["1020101", "Maebashi Regional Meteorological Observatory"],
  ["1120200", "Kumagaya Regional Meteorological Observatory"],
  ["1220200", "Choshi Regional Meteorological Observatory"],
  ["1221701", "Meteorological College"],
  ["1410400", "Yokohama Regional Meteorological Observatory"],
  ["1510301", "Niigata Regional Meteorological Observatory"],
  ["1620100", "Toyama Regional Meteorological Observatory"],
  ["1720100", "Kanazawa Regional Meteorological Observatory"],
  ["1820100", "Fukui Regional Meteorological Observatory"],
  ["1920100", "Kofu Regional Meteorological Observatory"],
  ["2020100", "Nagano Regional Meteorological Observatory"],
  ["2120100", "Gifu Regional Meteorological Observatory"],
  ["2210201", "Shizuoka Regional Meteorological Observatory"],
  ["2310100", "Nagoya Regional Meteorological Observatory"],
  ["2321601", "Chubu Airport Meteorological Observatory"],
  ["2420100", "Tsu Regional Meteorological Observatory"],
  ["2520201", "Hikone Regional Meteorological Observatory"],
  ["2610400", "Kyoto Regional Meteorological Observatory"],
  ["2712800", "Osaka Regional Meteorological Observatory"],
  ["2736200", "Kansai Airport Meteorological Observatory"],
  ["2811001", "Kobe Regional Meteorological Observatory"],
  ["2920102", "Nara Regional Meteorological Observatory"],
  ["3020100", "Wakayama Regional Meteorological Observatory"],
  ["3120100", "Tottori Regional Meteorological Observatory"],
  ["3220102", "Matsue Regional Meteorological Observatory"],
  ["3310100", "Okayama Regional Meteorological Observatory"],
  ["3410100", "Hiroshima Regional Meteorological Observatory"],
  ["3520100", "Shimonoseki Regional Meteorological Observatory"],
  ["3620101", "Tokushima Regional Meteorological Observatory"],
  ["3720102", "Takamatsu Regional Meteorological Observatory"],
  ["3820101", "Matsuyama Regional Meteorological Observatory"],
  ["3920100", "Kochi Regional Meteorological Observatory"],
  ["4013300", "Fukuoka Regional Meteorological Observatory"],
  ["4120100", "Saga Regional Meteorological Observatory"],
  ["4220100", "Nagasaki Regional Meteorological Observatory"],
  ["4520101", "Miyazaki Regional Meteorological Observatory"],
  ["4620100", "Kagoshima Regional Meteorological Observatory"],
  ["4622201", "Naze Weather Observatory"],
  ["4720100", "Okinawa Meteorological Observatory"],
  ["4720700", "Ishigakijima Regional Meteorological Observatory"],
  ["4721400", "Miyakojima Regional Meteorological Observatory"],
  ["4735700", "Minamidaito Island Regional Meteorological Observatory"],
]);

const RESEARCHED_NAME_OVERRIDES = new Map([
  ["0242400", researchedName("Sunagomata Gamayachi", JMA_XML_READINGS_URL, "JMA publishes the observation-point reading as Sunagomata Gamayachi.", "Official JMA observation-point reading")],
  ["0242431", researchedName("Sunagomata Sawauchi", "https://xml.kishou.go.jp/20120831_jmaxml_info.pdf", "JMA publishes the local-government observation point as Sunagomata Sawauchi, distinguishing it from Kamayachi.", "Official observation-point reading")],
  ["0348400", researchedName("Tanohata", JMA_STATION_URL, "JMA places this station at Tanohata 414, separate from the village-office installation.", "Official JMA installation address")],
  ["0348430", researchedName("Tanohata Village Office", "https://www.vill.tanohata.iwate.jp/", "The source label identifies Tanohata Village Office; the village publishes its office at Tanohata 143-1.", "Official municipal facility identity")],
  ["0330200", researchedName("Kuzumaki Motoki", JMA_STATION_URL, "JMA places this station at Kuzumaki, 39th district, Aza Motoki 218.", "Official JMA installation address")],
  ["0330231", researchedName("Kuzumaki Town Office", "https://www.town.kuzumaki.lg.jp/soshiki/kuzumaki/more.p5.html", "The source label explicitly identifies Kuzumaki Town Office.", "Official municipal facility identity")],
  ["0320500", researchedName("Ohasama", JMA_STATION_URL, "JMA places this station at Ohasamacho Ohasama 9-63; Ohasama is the specific locality needed to distinguish it from Hanamaki's other stations.", "Official JMA installation address")],
  ["0320531", researchedName("Ohasama General Branch Office", "https://www.city.hanamaki.iwate.jp/shisetsu/shiyakusyo/1004112/1004115.html", "Hanamaki identifies the source-labelled facility as Ohasama General Branch Office at Ohasamacho Ohasama 2-51-4.", "Official municipal facility identity")],
  ["0646100", researchedName("Yuza", JMA_STATION_URL, "JMA places this station at Yuza, Aza Tsuruta 52-2, separate from the Maizuru station.", "Official JMA installation address")],
  ["0646131", researchedName("Yuza Maizuru", JMA_XML_READINGS_URL, "The official observation-point label specifically identifies Maizuru in Yuza.", "Official observation-point identity")],
  ["0750500", researchedName("Matsukawa Yokokawa", JMA_XML_READINGS_URL, "JMA publishes the observation-point reading as Matsukawa Yokokawa.", "Official JMA observation-point reading")],
  ["0750530", researchedName("Matsukawa Shinkuwabara", "https://xml.kishou.go.jp/20120831_jmaxml_info.pdf", "JMA publishes the local-government observation-point reading as Matsukawa Shinkuwabara.", "Official observation-point reading")],
  ["0820500", researchedName("Kakioka", JMA_STATION_URL, "JMA identifies this observation point specifically as Kakioka.", "Official JMA observation-point identity")],
  ["0820532", researchedName("Ishioka Yasato", "https://www.city.ishioka.lg.jp/page/page002803.html", "The source label identifies the broader Yasato installation; Ishioka publishes its Yasato General Branch Office in Kakioka, explaining why coordinate-only lookup collapses onto Kakioka.", "Official municipal district identity")],
  ["0822800", researchedName("Iwai", JMA_STATION_URL, "JMA identifies this observation point specifically as Iwai.", "Official JMA observation-point identity")],
  ["0822834", researchedName("Bando City Hall", "https://www.city.bando.lg.jp/data/newcityhall/", "The source label explicitly identifies Bando City Hall at Iwai 4365.", "Official municipal facility identity")],
  ["1136500", researchedName("Ryokami Susuki", JMA_STATION_URL, "JMA identifies the observation point as Ryokami Susuki, preserving both parts of the specific locality.", "Official JMA observation-point identity")],
  ["1136531", researchedName("Ryokami Promotion Hall", "https://www.town.ogano.lg.jp/kurashi-tetsuzuki/bus-taxi/", "Ogano identifies the source-labelled facility as Ryokami Promotion Hall, the former Ryokami government building.", "Official municipal facility identity")],
  ["1921300", researchedName("Enzan Shimozo", JMA_STATION_URL, "JMA identifies this observation point specifically as Enzan Shimozo.", "Official JMA observation-point identity")],
  ["1921333", researchedName("Koshu City Hall", "https://www.city.koshu.yamanashi.jp/soshiki/", "The source label explicitly identifies Koshu City Hall, distinguishing the facility station from Enzan Shimozo.", "Official municipal facility identity")],
  ["2222200", researchedName("Nakaizu Ground", "https://www.city.izu.shizuoka.jp/bunka_sports/3/1/3706.html", "Izu identifies the source-labelled sports facility as Nakaizu Ground at Hatsuma 860.", "Official municipal facility identity")],
  ["2222232", researchedName("Izu Hatsuma", JMA_XML_READINGS_URL, "The source observation-point label specifically identifies Hatsuma; the city name keeps the short locality unambiguous in English.", "Official observation-point identity")],
  ["2221000", researchedName("Fuji General Sports Park", "https://www.city.fuji.shizuoka.jp/1015400000/shisetsu/p000155.html", "Fuji identifies the source-labelled facility as Fuji General Sports Park.", "Official municipal facility identity")],
  ["2221036", researchedName("Fuji Obuchi", JMA_XML_READINGS_URL, "The source observation-point label specifically identifies Obuchi; the city name keeps the locality unambiguous in English.", "Official observation-point identity")],
  ["2712800", researchedName("Osaka Regional Meteorological Observatory", "https://www.data.jma.go.jp/osaka/an-nai/saiyou/R0508_kokkakoumuinopen1.pdf", "JMA places the station at Osaka Regional Meteorological Observatory, 4-1-76 Otemae, inside Osaka National Government Building No. 4.", "Official JMA facility identity")],
  ["2712831", researchedName("Osaka Prefectural Government", "https://www.pref.osaka.lg.jp/", "The source label explicitly identifies the Osaka Prefectural Government installation in Otemae.", "Official prefectural facility identity")],
  ["3520402", researchedName("Mishima Utsu", JMA_XML_READINGS_URL, "JMA publishes the observation-point reading as Mishima Utsu.", "Official JMA observation-point reading")],
  ["3520443", researchedName("Mishima Honmura", "https://www.city.hagi.lg.jp/site/machihaku/h30847.html", "Hagi distinguishes Mishima's Utsu and Honmura districts; the source station is specifically Honmura.", "Official municipal locality identity")],
  ["4221301", researchedName("Obamacho Unzen", JMA_STATION_URL, "JMA identifies this observation point specifically as Obamacho Unzen.", "Official JMA observation-point identity")],
  ["4221335", researchedName("Unzen Branch Office", "https://www.city.unzen.nagasaki.jp/bousai/kiji0034840/3_4840_12374_up_okgjakiw.pdf", "Unzen City identifies the source-labelled Unzen Branch Office at Obamacho Unzen 292-1.", "Official municipal facility identity")],
  ["4320202", researchedName("Izumi Kakizako", JMA_STATION_URL, "JMA places the Izumimachi observation point at Izumimachi Kakizako; the specific locality distinguishes it from the branch-office station.", "Official JMA installation address")],
  ["4320237", researchedName("Izumi Branch Office", "https://www.city.yatsushiro.lg.jp/kiji00389/index.html", "Yatsushiro identifies the source-labelled facility as Izumi Branch Office in Izumimachi Kakizako.", "Official municipal facility identity")],
  ["4520304", researchedName("Kitakata General Sports Park", "https://www.city.nobeoka.miyazaki.jp/site/miryoku/3244.html", "Nobeoka identifies the source-labelled facility as Kitakata General Sports Park.", "Official municipal facility identity")],
  ["4520337", researchedName("Kitakata General Branch Office", "https://www.city.nobeoka.miyazaki.jp/soshiki/13.html", "Nobeoka identifies the source-labelled facility as Kitakata General Branch Office.", "Official municipal facility identity")],
  ["4630401", researchedName("Nakanoshima Tokunoo", "https://www.jma.go.jp/jma/press/2305/13a/202305131810.html", "JMA publishes the locality reading as Nakanoshima Tokunoo.", "Official JMA observation-point reading")],
  ["4630445", researchedName("Nakanoshima Branch Office", "https://www.tokara.jp/profile/gaiyou/nakano/", "Toshima identifies the source-labelled facility as Nakanoshima Branch Office.", "Official municipal facility identity")],
  ["4650501", researchedName("Kuchinoerabujima Ikeda", "https://xml.kishou.go.jp/20151023_jmaxml_info.pdf", "JMA corrected the official reading to Kuchinoerabujima Ikeda; Yakushima's official English material uses Kuchinoerabujima.", "Official observation-point reading")],
  ["4650531", researchedName("Kuchinoerabujima Community Center", "https://www.town.yakushima.kagoshima.jp/material/files/group/3/09_r3bousaimap_kanagadake_30870633.pdf", "Yakushima's official English disaster map names the source-labelled facility Kuchinoerabujima Community Center.", "Official municipal English facility identity")],
  ["4735800", researchedName("Koganeyama", JMA_XML_READINGS_URL, "JMA publishes the observation-point reading as Koganeyama; coordinate-only lookup incorrectly collapses it onto Nakano.", "Official JMA observation-point reading")],
  ["4735831", researchedName("Kitadaito Nakano", JMA_XML_READINGS_URL, "The source observation-point label specifically identifies Nakano; the village name keeps the short locality unambiguous in English.", "Official observation-point identity")],
  ["4721405", researchedName("Ueno Shinzato", "https://xml.kishou.go.jp/20130227_jmaxml_info2.pdf", "JMA publishes the observation-point reading as Ueno Shinzato.", "Official JMA observation-point reading")],
  ["4721436", researchedName("Ueno Branch Office", "https://www.city.miyakojima.lg.jp/soshiki/shityo/seikatukankyou/ueno/", "Miyakojima identifies the source-labelled facility as Ueno Branch Office.", "Official municipal facility identity")],
  ["0120401", researchedName("Asahikawa Local Meteorological Office", "https://www.data.jma.go.jp/asahikawa/shosai/pdf/20250319oshirase_asa.pdf", "JMA's English document names the facility Asahikawa Local Meteorological Office; its published address is Miyamae 1-jo 3-3-15.", "Official JMA facility identity")],
  ["0139201", researchedName("Suttsu Special Automated Weather Station", JMA_AMEDAS_MASTER_URL, "JMA places Suttsu at the Suttsu Special Automated Weather Station in Shin'eicho.", "Official JMA facility identity")],
  ["0151700", researchedName("Rebun Uedomarisaki", JMA_XML_READINGS_URL, "JMA publishes the observation-point reading as Rebun-cho Uedomarisaki and the site at Uentomari.", "Official JMA observation-point reading")],
  ["0230733", researchedName("Sotogahama Tairadate", "https://www.town.sotogahama.lg.jp/kanko/spot/spot_tairadate.html", "Sotogahama's official site uses Tairadate for 平舘; the source coordinate is in that district.")],
  ["0230735", researchedName("Sotogahama Kanita", "https://www.town.sotogahama.lg.jp/gyosei/koho/koho_sotogahama/files/201906_soto_prm.pdf", "Sotogahama's official English publication uses KANITA for 蟹田; the source coordinate is in that district.")],
  ["0721336", researchedName("Date Maekawara", JMA_XML_READINGS_URL, "The source station label and GSI result both identify 前川原; JMA's observation-point data supplies the reading.", "Official observation-point label + GSI")],
  ["0754400", researchedName("Shimokawauchi", JMA_STATION_URL, "The JMA station label and GSI result both identify Shimokawauchi.", "Official JMA observation-point identity")],
  ["0754430", researchedName("Kawauchi Village Office", "https://www.kawauchimura.jp/gyosei/tokeidata/page000087.html", "Kawauchi publishes its village-office address as Oaza Kamikawauchi, Aza Hayawata 11-24, matching the station label.", "Official municipal facility identity")],
  ["1020602", researchedName("Gunma Prefectural Oze High School", "https://www.pref.gunma.jp/uploaded/attachment/679269.pdf", "Gunma Prefecture identifies the facility as Gunma Prefectural Oze High School; the source station is explicitly installed at the school.", "Official prefectural facility identity")],
  ["1120200", researchedName("Kumagaya Local Meteorological Office", "https://www.data.jma.go.jp/kumagaya/shosai/annai/access.html", "JMA publishes the Kumagaya Local Meteorological Office at 1-6-10 Sakuracho, matching the station label.", "Official JMA facility identity")],
  ["1330740", researchedName("Hinohara Village Office", "https://www.vill.hinohara.tokyo.jp/soshiki_list.html", "Hinohara identifies the source-labelled facility as Hinohara Village Office at 467-1.", "Official municipal facility identity")],
  ["1336401", researchedName("Kozushima Airport", "https://www.vill.kouzushima.tokyo.jp/images/2023/03/20230314-keikaku.pdf", "Kozushima's official disaster plan identifies the Kincho observation point as located at Kozushima Airport.", "Official facility identity")],
  ["1336431", researchedName("Kozushima Village Office", "https://www.vill.kouzushima.tokyo.jp/", "The source label explicitly identifies the municipal station at Kozushima Village Office.", "Official municipal facility identity")],
  ["1336300", researchedName("Niijima Ohara", JMA_STATION_URL, "JMA publishes the station at Niijima-mura Ohara 291-4.", "Official JMA installation address")],
  ["1342100", researchedName("Chichijima Nishimachi", "https://en.vill.ogasawara.tokyo.jp/chichijima/", "Ogasawara's official English site uses Chichijima and Nishimachi, preserving the source station's specific locality.")],
  ["1342101", researchedName("Chichijima Mikazukiyama", "https://en.vill.ogasawara.tokyo.jp/chichijima/chichijima_info/", "Ogasawara's official English material uses Mikazukiyama for 三日月山, distinguishing it from Nishimachi.")],
  ["1558600", researchedName("Awashimaura Sasabatake", JMA_STATION_URL, "The official JMA observation-point label specifically identifies Sasabatake in Awashimaura.", "Official JMA observation-point identity")],
  ["1558631", researchedName("Awashimaura Village Office", "https://www.vill.awashimaura.lg.jp/wp-content/uploads/2025/10/e760618eb52170f0e17470e12102d730.pdf", "Awashimaura publishes its village office at Aza Hinomiyama 1513-11, matching the source station locality.", "Official municipal facility identity")],
  ["1522445", researchedName("Ryotsu Civic Center", "https://www.city.sado.niigata.jp/soshiki/3001/index.html", "Sado identifies the source-labelled facility as Ryotsu Civic Center at Ryotsuminato 198.", "Official municipal facility identity")],
  ["2041400", researchedName("Nashikubo", "https://www.vill.yasuoka.nagano.jp/docs/29992.html", "Yasuoka's official page gives the reading Nashikubo for 梨久保.", "Official locality reading")],
  ["2041431", researchedName("Yasuoka Village Office", "https://www.vill.yasuoka.nagano.jp/", "The source label explicitly identifies the municipal station at Yasuoka Village Office.", "Official municipal facility identity")],
  ["2042320", researchedName("Nagiso Elementary School", "https://www.bosai.go.jp/sp/information/tender/supply/pdf/shiyousho.pdf", "NIED identifies NGN022 at 3668-1 Yomikaki on the grounds of Nagiso Elementary School, the former Yomikaki Elementary School site.", "Official installation facility identity")],
  ["2042330", researchedName("Nagiso Town Office", "https://www.town.nagiso.nagano.jp/s/foreign/en/information.html", "The source label explicitly identifies the municipal station at Nagiso Town Office.", "Official municipal facility identity")],
  ["2042930", researchedName("Otaki Village Office", "https://www.vill.otaki.nagano.jp/", "The source label explicitly identifies the municipal station at Otaki Village Office.", "Official municipal facility identity")],
  ["2210130", researchedName("Shizuoka Prefectural Government", "https://www.pref.shizuoka.jp/governor/ir2002/documents/ir_20221108_en.pdf", "The source label distinguishes the prefectural-government installation at 9-6 Otemachi.", "Official prefectural facility identity")],
  ["2210131", researchedName("Shizuoka City Hall", "https://www.city.shizuoka.lg.jp/s9328/s002700.html", "Shizuoka identifies its city-hall Shizuoka office at 5-1 Otemachi, distinguishing it from the prefectural station.", "Official municipal facility identity")],
  ["2356333", researchedName("Toyone Tomiyama", "https://www.vill.toyone.aichi.jp/", "The source and GSI locality both identify Toyone's Tomiyama district; the broader district name avoids inventing a more precise aza.")],
  ["2310630", researchedName("Nagoya City Hall", "https://www.city.nagoya.jp/kankobunkakoryu/cmsfiles/contents/0000029/29846/eigo.pdf", "Nagoya's official English guide identifies Nagoya City Hall in Naka Ward; the source label says city hall, not the ward office.", "Official municipal facility identity")],
  ["2447230", researchedName("Minamiise Gokashoura", "https://www.town.minamiise.lg.jp/admin/gyousei/info/1460.html", "The source label and GSI result both identify Gokashoura in Minamiise.")],
  ["2720300", researchedName("Osaka Itami Airport", "https://www.osaka-airport.co.jp/en", "The airport's official English site uses Osaka Itami Airport for Osaka International Airport.", "Official airport identity")],
  ["2714131", researchedName("Sakai City Hall", "https://www.city.sakai.lg.jp/", "The source label explicitly identifies Sakai City Hall in Sakai Ward.", "Official municipal facility identity")],
  ["3040102", researchedName("Shirahama Town Fire Department Headquarters", "https://www.town.shirahama.wakayama.jp/soshiki/shobo/gyomu/toukei/3908.html", "Shirahama identifies the source-labelled facility as its fire-department headquarters.", "Official municipal facility identity")],
  ["3220341", researchedName("Izumo Sada Administrative Center", "https://www.city.izumo.shimane.jp/www/window/index.html", "Izumo publishes the Sada Administrative Center at Sadacho Tanbe 1747-6, matching the source locality.", "Official municipal facility identity")],
  ["3321600", researchedName("Amakusa Park", "https://www.city.asakuchi.lg.jp/page/1911.html", "Asakuchi's official page identifies Amakusa Park, the facility named by the JMA station label.", "Official facility identity")],
  ["3420240", researchedName("Kure Toyohama", "https://www.city.kure.lg.jp/", "The source station covers the broader Toyohama district rather than one of its three constituent localities.")],
  ["3720835", researchedName("Mitoyo Nio", "https://www.city.mitoyo.lg.jp/material/files/group/16/eg_rubi.pdf", "Mitoyo's official English guide uses Nio-cho; the source station covers the broader Nio district.", "Official English locality identity")],
  ["3938635", researchedName("Ino Town Office", "https://www.town.ino.kochi.jp/", "The source label explicitly identifies the municipal station at Ino Town Office.", "Official municipal facility identity")],
  ["4120638", researchedName("Takeo Kitagata", "https://www.city.takeo.lg.jp/kyouiku/cat30/post-81.html", "Takeo's official site uses Kitagata for 北方; the source station is district-level and does not justify a narrower oaza.")],
  ["4134635", researchedName("Miyaki Mine Office", "https://www.town.miyaki.lg.jp/kabetsu/mine.html?media=pc", "Miyaki's official site identifies the Mine government building; the source coordinate is in the former Mine area.", "Official municipal facility identity")],
  ["4420541", researchedName("Saiki Yonouzu", "https://www.city.saiki.oita.jp/", "The source station covers the broader Yonouzu district rather than one of its five constituent localities.")],
  ["4520437", researchedName("Nichinan Kitago Gonohara", "https://www.city.nichinan.lg.jp/", "The station label and GSI result both identify Kitagocho Gonohara; the source does not select Ko or Otsu.")],
  ["4620132", researchedName("Kagoshima Shinjima", "https://www.city.kagoshima.lg.jp/shimin/sakurajima/soumu/machizukuri/igai/kokyo/koku/fune.html", "Kagoshima identifies Shinjima as the inhabited island created by the An'ei eruption; this is the specific island station in the Sakurajima-Akamizu reporting area.")],
  ["4620830", researchedName("Izumi Katsurajima", "https://www.city.kagoshima-izumi.lg.jp/", "The source station specifically identifies Katsurajima in Izumi.")],
  ["4735930", researchedName("Iheya Village Office", "https://www.vill.iheya.okinawa.jp/soshiki/", "Iheya publishes its village office at Aza Gakiya 251, matching the source-labelled facility.", "Official municipal facility identity")],
  ["4721533", researchedName("Nanjo Sashiki", "https://www.city.nanjo.okinawa.jp/movie_library/movie_en/1579322539/1579323323/", "Nanjo's official English page uses Sashiki; this station is in Aza Sashiki.", "Official English locality identity")],
  ["4721535", researchedName("Nanjo Sashiki Shinzato", "https://www.city.nanjo.okinawa.jp/userfiles/files/topics/1230/004.pdf", "Nanjo's official English publication uses Shinzato, Sashiki, distinguishing this station from Aza Sashiki.", "Official English locality identity")],
  ["4721400", researchedName("Hirara Shimozato", "https://www.city.miyakojima.lg.jp/soshiki/kyouiku/syougaigakusyu/syougaikakusyu/bunkazai-Tuzupisukiabu.html", "Miyakojima's official English page uses Shimozato in Hirara, preserving the specific JMA station locality.", "Official English locality identity")],
  ["4721403", researchedName("Hirara Ikema", "https://www.city.miyakojima.lg.jp/gyosei/mayor/oshirase/files/P00-P03.pdf", "Miyakojima's official English map uses Ikema Island; the JMA station label identifies Hirara Ikema.", "Official English locality identity")],
  ["4721435", researchedName("Miyakojima Shimoji", "https://www.city.miyakojima.lg.jp/gyosei/mayor/oshirase/files/P00-P03.pdf", "The source station covers Miyakojima's Shimoji district and is distinct from Shimojishima Airport.", "Official district identity")],
  ["4721439", researchedName("Shimojishima Airport", "https://www.city.miyakojima.lg.jp/gyosei/ecoisland/modeltoshi/tousyo/files/190423_SmartCommunity_Eng.pdf", "Miyakojima's official English document uses Shimojishima Airport for the source-labelled airport.", "Official airport identity")],
  ["4738200", researchedName("Yonaguni Sonai", "https://www.data.jma.go.jp/ishigaki/bosai/tmanual/pdf/tsunami_manual_all.pdf", "JMA's local official material distinguishes the Sonai settlement from Kubura.", "Official JMA locality identity")],
  ["4738202", researchedName("Yonaguni Kubura", "https://www.data.jma.go.jp/kaiyou/db/tide/suisan/suisan.php?stn=YJ", "JMA's official tide page locates Yonaguni at Kubura, confirming the source locality's reading.", "Official JMA locality identity")],
  ["4738230", researchedName("Yonaguni Town Office", "https://www.town.yonaguni.okinawa.jp/docs/2018042400588/access.html", "The source label explicitly identifies Yonaguni Town Office.", "Official municipal facility identity")],
]);

const EXPLICIT_STATION_NAME_OVERRIDES = new Map([
  ["0123100", "Izarigawa Dam Management Branch Office"],
  ["0121401", "Wakkanai Keihoku"],
  ["0120803", "Kitami Tokorocho Higashihama"],
  ["0151121", "Sarufutsu Asajino"],
  ["0151220", "Hamatonbetsu Kutcharo"],
  ["0155500", "Engaru Maruseppu Kanayuyama"],
  ["0169100", "Betsukai Tokiwa"],
  ["0320940", "Ichinoseki Kawasakicho"],
  ["0336631", "Nishiwaga Sawauchi Ota"],
  ["0321120", "Kamaishi Nakatsumacho"],
  ["0444530", "Kami Nakaniida"],
  ["0444531", "Kami Onoda"],
  ["0620321", "Tsuruoka Michitamachi"],
  ["0410142", "Sendai Aoba Ward Amamiya"],
  ["1042130", "Nakanojo Nakanojo"],
  ["1120230", "Kumagaya Osato"],
  ["1120232", "Kumagaya Konan"],
  ["1121032", "Kazo Kitakawabe"],
  ["1121033", "Kazo Otone"],
  ["1121733", "Konosu Kawasato"],
  ["1120931", "Hanno Naguri"],
  ["1223602", "Katori Sawara Hirata"],
  ["1242700", "Chonan General Sports Ground"],
  ["1242732", "Chonan"],
  ["1310531", "Bunkyo Sports Center"],
  ["1311730", "Tokyo Kita Nishigahara"],
  ["1320850", "Chofu Nishi-tsutsujigaoka"],
  ["1413641", "Kawasaki Miyamae Nogawa"],
  ["1936833", "Fujikawa Tenjin Nakajo"],
  ["2060240", "Sakae Koakazawa"],
  ["2221034", "Fuji Iwabuchi"],
  ["2221035", "Fuji Yoshinaga"],
  ["2310632", "Aichi Prefectural Government"],
  ["2323534", "Yatomi Maegasucho"],
  ["2420334", "Ise Iwabuchi"],
  ["1721020", "Hakusan Bekkumachi"],
  ["2456100", "Teradani General Park"],
  ["2456131", "Mihama Atawa"],
  ["2610440", "Kyoto Nakagyo Kawaramachi Oike"],
  ["2611040", "Kyoto Yamashina Nishino"],
  ["2721333", "Izumisano Ichiba"],
  ["3244833", "Misato Kasubuchi"],
  ["3410832", "Hiroshima Saeki Yukicho Wada"],
  ["3636836", "Naka Kisawa"],
  ["3636838", "Naka Kaminaka"],
  ["3732201", "Tonosho Fuchizaki"],
  ["3848800", "Kihoku Narukawa"],
  ["3521137", "Nagato Shinbetsumyo"],
  ["3520138", "Shimonoseki Hohokucho Tsunoshima"],
  ["3520140", "Shimonoseki Hohokucho Takibe"],
  ["3520143", "Shimonoseki Toyotacho Tonoshiki"],
  ["3520145", "Shimonoseki Kikugawacho Shimookaeda"],
  ["4120637", "Takeo Takeocho Showa"],
  ["4120933", "Ureshino Shimojuku Otsu"],
  ["4132732", "Yoshinogari Mitagawa"],
  ["4132733", "Yoshinogari Higashisefuri"],
  ["4134633", "Miyaki Nakabaru"],
  ["4134636", "Miyaki Kitashigeyasu"],
  ["4142535", "Shiroishi Ariake"],
  ["4220934", "Tsushima Mitsushimacho Kechi"],
  ["4221238", "Saikai Oshimamachi"],
  ["4351433", "Asagiri Sue"],
  ["4420930", "Bungotakada Matama"],
  ["4543130", "Misato Nango Mikado"],
  ["4543133", "Misato Saigo Tashiro"],
  ["4544332", "Gokase Sangasho"],
  ["4520533", "Kobayashi Suki Nakahara"],
  ["4621837", "Kirishima Fukuyamacho Makinohara"],
  ["4640434", "Nagashima Ikarajima"],
  ["4645232", "Yusui Kurino"],
  ["4645233", "Yusui Yoshimatsu"],
  ["4622235", "Amami Sumiyocho Nishinakama"],
  ["4652531", "Setouchi Ukejima"],
  ["4652535", "Setouchi Kakeromajima"],
  ["4738104", "Taketomi Ohara"],

  ["0122401", "New Chitose Airport"],
  ["0420700", "Sendai Airport"],
  ["1221101", "Narita International Airport"],
  ["1311102", "Haneda Airport"],
  ["1510200", "Niigata Airport"],
  ["2321601", "Chubu Centrair International Airport"],
  ["2736200", "Kansai International Airport"],
  ["3420401", "Hiroshima Airport"],
  ["3720101", "Takamatsu Airport"],
  ["4013201", "Fukuoka Airport"],
  ["4621802", "Kagoshima Airport"],
  ["4720101", "Naha Airport"],

  ["0143020", "Tsukigata Maruyama Park"],
  ["0534820", "Mitane Kotooka Central Park"],
  ["0520120", "Akita Yabase Sports Park"],
  ["0720731", "Naganuma Branch Office"],
  ["0720735", "Iwase Branch Office"],
  ["0734432", "Yumoto Branch Office"],
  ["0744733", "Hongo Government Office"],
  ["0744734", "Niitsuru Government Office"],
  ["0744735", "Takada Government Office"],
  ["0820220", "Sukegawa Elementary School"],
  ["0920636", "Nikko Fujiwara Government Office"],
  ["0921333", "Nasushiobara Shiobara Government Office"],
  ["1520248", "Nagaoka Central Park"],
  ["1522620", "Shiozawa Elementary School"],
  ["1522632", "Shiozawa Government Office"],
  ["1510801", "Maki Temporary Government Office"],
  ["1510331", "Niigata City Hall"],
  ["1934632", "Rokugo Branch Office"],
  ["1936620", "Sakae Elementary School"],
  ["1944233", "Kosuge Elementary School"],
  ["2021220", "Omachi City Library"],
  ["2056120", "Yamanouchi Fire Station"],
  ["2020820", "Komoro Fire Station"],
  ["2021432", "Kuzui Park"],
  ["2022001", "Hotaka Branch Office"],
  ["2120320", "Takayama Fire Station"],
  ["2122021", "Gero Elementary School"],
  ["2121020", "Osashima Elementary School"],
  ["2121520", "Miyama Branch Office"],
  ["2521120", "Konan Chuo Morikita Park"],
  ["2521130", "Ishibe Chuo West Government Office"],
  ["2521131", "Chuo East Government Office"],
  ["2720720", "Takatsuki Municipal Daini Junior High School"],
  ["2720732", "Takatsuki Fire Department Headquarters"],
  ["3034401", "Koyasan Junior High School"],
  ["3042220", "Taiji Atami Park"],
  ["3120121", "Shikano Elementary School"],
  ["3137120", "Akasaki Junior High School"],
  ["3220122", "Mihonoseki General Sports Park"],
  ["3244936", "Mizuho Branch Office"],
  ["3252620", "Urago Elementary School"],
  ["3420921", "Konu Library"],
  ["3420202", "Ondo Junior High School"],
  ["3620701", "Anabuki Fureai Sports Park"],
  ["3620820", "Ikeda Junior High School"],
  ["3720620", "Nagao General Park"],
  ["3921137", "Akaoka Branch Office"],
  ["3521121", "Fukawa Junior High School"],
  ["3520851", "Kuga Branch Office"],
  ["3530540", "Towa General Branch Office"],
  ["4350520", "Kamikuma Fire Station"],
  ["4420820", "Taketa Elementary School"],
  ["4420821", "Naoiri Elementary School"],
  ["4520333", "Kitagawa General Branch Office"],
  ["4520602", "Daiodani Sports Park"],
  ["4543020", "Shiiba General Sports Park"],
  ["4520121", "Tano Gymnasium"],
  ["4520136", "Tano Branch Office"],
  ["4639220", "Miyanojo Health Center"],
  ["4649020", "Tashiro Branch Office"],
  ["4630444", "Kuchinoshima Branch Office"],
  ["4738112", "Uehara Elementary School"],

  ["0139220", "Suttsu Oshima"],
  ["0148620", "Embetsu Honcho"],
  ["0121423", "Wakkanai Numakawa"],
  ["0151620", "Toyotomi Nishi 6-Jo"],
  ["0120821", "Kitami Tokorocho Tokoro"],
  ["0155222", "Saroma Nishitomi"],
  ["0163320", "Kamishihoro Shimizudani"],
  ["0163521", "Shintoku Tomuraushi"],
  ["0232120", "Ajigasawa Maitomachi Sayo"],
  ["0232320", "Fukaura Nakazawa"],
  ["0220824", "Mutsu Ohatacho Nakajima"],
  ["0330220", "Kuzumaki Fire Substation"],
  ["0336621", "Nishiwaga Sawauchi Kawafune"],
  ["0521521", "Senboku Tazawako Obonai Kamishimizu"],
  ["0636520", "Okura Hijiori"],
  ["0748120", "Tanagura Tategaoka"],
  ["0752220", "Ono Nakamichi"],
  ["0754120", "Hirono Shimokitaba Oyachihara"],
  ["0754420", "Kawauchi Kamikawauchi Koyamadaira"],
  ["1120220", "Kumagaya Miyacho"],
  ["1223620", "Katori Sawara Suwadai"],
  ["1330721", "Hinohara Motoshuku"],
  ["1340121", "Hachijo Fuji Ground"],
  ["1522424", "Sado Matsugasaki"],
  ["1720220", "Nanao Sodegaemachi"],
  ["1920920", "Hokuto Kenkoland Sutama"],
  ["1944321", "Tabayama Taba"],
  ["2021520", "Shiojiri Narakawa Nursery School"],
  ["2041321", "Tenryu Shimizu"],
  ["2042920", "Otaki Suzugasawa"],
  ["2120321", "Takayama Okuhida Onsengo Tochio"],
  ["2120621", "Nakatsugawa Oguriyama"],
  ["2140121", "Ibigawa Nakakagobashi"],
  ["2322121", "Shinshiro Tsukude Takasato Matsuburo"],
  ["3020821", "Kinokawa Naga General Center"],
  ["3140320", "Kofu Uenodan Square"],
  ["3320320", "Tsuyama Konakabara"],
  ["3320520", "Kasaoka Tonogawa"],
  ["3436920", "Kitahiroshima Toyohira Post Office"],
  ["3732221", "Tonosho Obe"],
  ["3940521", "Yusuhara Hirono"],
  ["3520121", "Shimonoseki Toyouracho Water Purification Plant"],
  ["3520825", "Iwakuni Mikawamachi Takagahara"],
  ["4013120", "Fukuoka Higashi Kashii Ekihigashi 3-Chome"],
  ["4022820", "Asakura Tsutsumi"],
  ["4120621", "Takeo Takeocho Takeo"],
  ["4220820", "Matsuura Shisacho"],
  ["4320820", "Yamaga Senior Welfare Center"],
  ["4421420", "Kunisaki Tabuka"],
  ["4520820", "Saito Uenomiya"],
  ["4543120", "Misato Unama"],
  ["4544120", "Takachiho Terasako"],
  ["4622221", "Amami Kasaricho Sato"],
  ["4721420", "Miyakojima Hirara Karimata"],
  ["4721421", "Miyakojima Gusukube Fukunishi"],
]);

const APPROVED_MUNICIPALITY_FALLBACKS = new Map([
  ["0130431", "Shinshinotsu"],
  ["0110740", "Sapporo Nishi Ward"],
  ["0111040", "Sapporo Kiyota Ward"],
  ["0122632", "Sunagawa"],
  ["0142831", "Naganuma"],
  ["0145332", "Higashikagura"],
  ["0145431", "Toma"],
  ["0154301", "Bihoro"],
  ["0163820", "Nakasatsunai"],
  ["2821633", "Takasago"],
  ["4038231", "Mizumaki"],
  ["4120431", "Taku"],
  ["4351331", "Kuma"],
]);

const VERIFIED_OPERATOR_QUALIFIED_DISPLAY_NAMES = new Map();

const VERIFIED_NETWORK_QUALIFIED_DISPLAY_NAMES = new Map([
  ["0140020", "K-NET Kutchan"],
]);

const STATION_METADATA_OVERRIDES = new Map([
  [
    "2712800",
    {
      addressJa: "大阪市中央区大手前4-1-76（大阪管区気象台）",
      facilityNameJa: "大阪管区気象台",
      facilityNameEn: "Osaka Regional Meteorological Observatory",
      status: "Official facility and address verified",
      sourceUrls: [
        JMA_STATION_URL,
        "https://www.data.jma.go.jp/osaka/an-nai/contact.html",
        "https://www.data.jma.go.jp/osaka/an-nai/saiyou/R0508_kokkakoumuinopen1.pdf",
      ],
      note: "JMA-operated intensity station at the Osaka Regional Meteorological Observatory in Osaka National Government Building No. 4.",
    },
  ],
  [
    "0110100",
    {
      addressJa: "札幌市中央区北2条西18-2（札幌管区気象台）",
      facilityNameJa: "札幌管区気象台",
      facilityNameEn: "Sapporo Regional Meteorological Observatory",
      status: "Official facility and address verified",
      sourceUrls: [JMA_STATION_URL, JMA_SAPPORO_ENGLISH_URL],
      note: "JMA-operated intensity station at the Sapporo Regional Meteorological Observatory.",
    },
  ],
  [
    "0110140",
    {
      addressJa: "札幌市中央区南4条西10丁目（札幌市中央消防署）",
      facilityNameJa: "札幌市中央消防署",
      facilityNameEn: "Sapporo Chuo Fire Station",
      status: "Official facility and address verified",
      sourceUrls: [
        SAPPORO_FIRE_METER_URL,
        SAPPORO_FIRE_STATION_ADDRESS_URL,
        SAPPORO_CHUO_FIRE_NAME_URL,
      ],
      note: "Sapporo states that each ward's measured intensity comes from its fire-station meter.",
    },
  ],
  [
    "0140000",
    {
      addressJa: "虻田郡倶知安町南1条東3-1（倶知安特別地域気象観測所）",
      facilityNameJa: "倶知安特別地域気象観測所",
      facilityNameEn: "Kutchan Special Automated Weather Station",
      status: "Official JMA facility and address verified",
      sourceUrls: [JMA_STATION_URL],
      note: "JMA publishes the observation-site address; the English facility label is user-approved.",
    },
  ],
  [
    "0140020",
    {
      addressJa: "北海道虻田郡倶知安町北6条東7丁目",
      facilityNameJa: "",
      facilityNameEn: "",
      status: "Google Maps parcel and Street View verified",
      sourceUrls: [NIED_STATION_URL, KUTCHAN_KNET_MAPS_URL],
      note: "NIED identifies K-NET station HKD144 KUCCHAN at precise provider coordinates. Google Street View shows the enclosure on this parcel. English address: 7 Chome Kita 6 Johigashi, Kutchan, Abuta District, Hokkaido 044-0006. Future station-card note: Located in the southwestern corner of the grounds of the Shu Ogawara Museum of Art.",
    },
  ],
  [
    "4320231",
    {
      addressJa: "八代市鏡町内田453-1（八代市鏡支所）",
      facilityNameJa: "八代市鏡支所",
      facilityNameEn: "",
      status: "Official address verified",
      sourceUrls: [
        "https://www.town.mashiki.lg.jp/bousai/kiji0036632/3_6632_19153_up_cu4d7aru.pdf",
      ],
      note: "Official seismic-observation listing and the city branch-office address agree.",
    },
  ],
]);

function parseCsvLine(line) {
  const values = [];
  let value = "";
  let quoted = false;
  for (let i = 0; i < line.length; i += 1) {
    const char = line[i];
    if (char === '"') {
      if (quoted && line[i + 1] === '"') {
        value += '"';
        i += 1;
      } else {
        quoted = !quoted;
      }
    } else if (char === "," && !quoted) {
      values.push(value);
      value = "";
    } else {
      value += char;
    }
  }
  values.push(value);
  return values;
}

function parseKanjiNumber(value) {
  const digits = { 零: 0, 〇: 0, 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 };
  const units = { 十: 10, 百: 100, 千: 1000 };
  if (![...value].some((char) => units[char])) {
    return [...value].map((char) => digits[char]).join("");
  }
  let total = 0;
  let pending = 0;
  for (const char of value) {
    if (char in digits) {
      pending = digits[char];
    } else if (char in units) {
      total += (pending || 1) * units[char];
      pending = 0;
    }
  }
  return String(total + pending);
}

function normalize(value) {
  return String(value ?? "")
    .normalize("NFKC")
    .replace(/[零〇一二三四五六七八九十百千]+(?=(?:条|丁目|番|号|線))/g, parseKanjiNumber)
    .replace(/[\s　]/g, "")
    .replace(/^大字/, "")
    .replace(/^字/, "")
    .trim();
}

function normalizePlaceKey(value) {
  return normalize(value)
    .replace(/大字|字/g, "")
    .replace(/[ヶケ]/g, "ケ")
    .replace(/[渕淵]/g, "淵")
    .replace(/[惠恵]/g, "恵")
    .replace(/[鷄雞鶏]/g, "鶏");
}

const KANA_ROMAJI = new Map(Object.entries({
  きゃ: "kya", きゅ: "kyu", きょ: "kyo", ぎゃ: "gya", ぎゅ: "gyu", ぎょ: "gyo",
  しゃ: "sha", しゅ: "shu", しょ: "sho", じゃ: "ja", じゅ: "ju", じょ: "jo",
  ちゃ: "cha", ちゅ: "chu", ちょ: "cho", にゃ: "nya", にゅ: "nyu", にょ: "nyo",
  ひゃ: "hya", ひゅ: "hyu", ひょ: "hyo", びゃ: "bya", びゅ: "byu", びょ: "byo",
  ぴゃ: "pya", ぴゅ: "pyu", ぴょ: "pyo", みゃ: "mya", みゅ: "myu", みょ: "myo",
  りゃ: "rya", りゅ: "ryu", りょ: "ryo", ふぁ: "fa", ふぃ: "fi", ふぇ: "fe", ふぉ: "fo",
  てぃ: "ti", でぃ: "di", うぃ: "wi", うぇ: "we", うぉ: "wo", しぇ: "she", じぇ: "je",
  ちぇ: "che", つぁ: "tsa", つぃ: "tsi", つぇ: "tse", つぉ: "tso",
  あ: "a", い: "i", う: "u", え: "e", お: "o", か: "ka", き: "ki", く: "ku", け: "ke", こ: "ko",
  が: "ga", ぎ: "gi", ぐ: "gu", げ: "ge", ご: "go", さ: "sa", し: "shi", す: "su", せ: "se", そ: "so",
  ざ: "za", じ: "ji", ず: "zu", ぜ: "ze", ぞ: "zo", た: "ta", ち: "chi", つ: "tsu", て: "te", と: "to",
  だ: "da", ぢ: "ji", づ: "zu", で: "de", ど: "do", な: "na", に: "ni", ぬ: "nu", ね: "ne", の: "no",
  は: "ha", ひ: "hi", ふ: "fu", へ: "he", ほ: "ho", ば: "ba", び: "bi", ぶ: "bu", べ: "be", ぼ: "bo",
  ぱ: "pa", ぴ: "pi", ぷ: "pu", ぺ: "pe", ぽ: "po", ま: "ma", み: "mi", む: "mu", め: "me", も: "mo",
  や: "ya", ゆ: "yu", よ: "yo", ら: "ra", り: "ri", る: "ru", れ: "re", ろ: "ro", わ: "wa", を: "o",
  ん: "n", ゔ: "vu", ゎ: "wa", ゕ: "ka", ゖ: "ke",
}));

function kanaToRomaji(value) {
  const hiragana = String(value ?? "")
    .normalize("NFKC")
    .replace(/[ァ-ヶ]/g, (char) => String.fromCodePoint(char.codePointAt(0) - 0x60))
    // DAABR contains legacy spellings such as チヨウ and リユウ. Restrict
    // normalization to long-vowel sequences so genuine names such as
    // Uchiyama and Miyauchi are not collapsed into Uchama/Myauchi.
    .replace(/([きぎしちぢにひびぴみり])ゆ(?=う)/g, "$1ゅ")
    .replace(/([きぎしちぢにひびぴみり])よ(?=う)/g, "$1ょ");
  let output = "";
  let geminate = false;
  for (let index = 0; index < hiragana.length; index += 1) {
    const char = hiragana[index];
    if (char === "っ") {
      geminate = true;
      continue;
    }
    if (char === "ー") {
      const vowels = output.match(/[aeiou]/gi);
      const vowel = vowels?.at(-1) ?? "";
      output += vowel;
      continue;
    }
    const pair = hiragana.slice(index, index + 2);
    let syllable = KANA_ROMAJI.get(pair);
    if (syllable) index += 1;
    else syllable = KANA_ROMAJI.get(char) ?? char;
    if (geminate && /^[bcdfghjklmpqrstvwxyz]/i.test(syllable)) syllable = syllable[0] + syllable;
    geminate = false;
    output += syllable;
  }
  return output.replace(/n([bmp])/g, "m$1");
}

function derivedLocalityEnglish(candidate) {
  if (candidate?.localityEn) {
    let official = String(candidate.localityEn)
      .replace(/(\p{L})(\d)/gu, "$1 $2")
      .replace(/\s+/g, " ")
      .trim();
    if (/区.+/u.test(String(candidate.localityJa ?? ""))) {
      official = official.replace(/\b([A-Za-z]+)ku(?=\s)/i, "$1");
    }
    return official.split(" ").filter((token, index, tokens) => (
      index === 0 || token.toLocaleLowerCase("en") !== tokens[index - 1].toLocaleLowerCase("en")
    )).join(" ");
  }
  let romaji = kanaToRomaji(candidate?.localityKana);
  if (!romaji || /[\u3040-\u30ff\u3400-\u9fff]/u.test(romaji)) return "";
  // GSI geographical-name romanization omits long-vowel markers and the
  // residential division labels oaza/aza/chome in foreign-facing names.
  romaji = romaji.toLocaleLowerCase("en");
  const localityJa = String(candidate?.localityJa ?? "");
  // Preserve a boundary after a non-terminal administrative division before
  // removing long vowels; otherwise 町内山 becomes "chiyama" by consuming
  // the first u of Uchiyama as part of the preceding chou.
  if (/町.+/u.test(localityJa)) romaji = romaji.replace(/(?:chou|machi)(?=[a-z])/, "$& ");
  if (/村.+/u.test(localityJa)) romaji = romaji.replace(/mura(?=[a-z])/, "$& ");
  if (/区.+/u.test(localityJa)) romaji = romaji.replace(/ku(?=[a-z])/, "$& ");
  romaji = romaji
    .replace(/ou+/g, "o")
    .replace(/oo+/g, "o")
    .replace(/uu+/g, "u");
  if (/区.+/u.test(localityJa)) romaji = romaji.replace(/\b([a-z]+)ku(?=\s)/, "$1");
  if (localityJa.includes("大字")) romaji = romaji.replace(/oaza/, " ");
  const standaloneAzaCount = (localityJa.replace(/大字/g, "").match(/字/g) ?? []).length;
  for (let count = 0; count < standaloneAzaCount; count += 1) {
    romaji = romaji.replace(/aza/, " ");
  }
  romaji = romaji
    .replace(/(\d+)chome/g, " $1-Chome ")
    .replace(/(\d+)bancho/g, " $1-Bancho ")
    .replace(/(\d+)jo(?=$|[^a-z])/g, " $1-Jo ");
  let formatted = romaji.replace(/(\d+)/g, " $1 ");
  formatted = formatted.replace(/\s+/g, " ").trim();
  formatted = formatted.replace(/(\d+)\s*-\s*(Chome|Bancho|Jo)\b/gi, "$1-$2");
  formatted = formatted.split(" ").filter((token, index, tokens) => (
    index === 0 || token.toLocaleLowerCase("en") !== tokens[index - 1].toLocaleLowerCase("en")
  )).join(" ");
  return formatted.replace(/(^|[\s-])(\p{L})/gu, (_, prefix, letter) => (
    `${prefix}${letter.toLocaleUpperCase("en")}`
  ));
}

function derivedCandidateEnglish(candidate, sourceText = "") {
  const base = derivedLocalityEnglish(candidate);
  if (!base || !sourceText) return base;
  const sourceKey = normalizePlaceKey(sourceText);
  const variants = (candidate.variants ?? [])
    .filter((variant) => {
      const variantKey = normalizePlaceKey(variant.localityJa);
      return variantKey && sourceKey.includes(variantKey);
    })
    .sort((a, b) => normalizePlaceKey(b.localityJa).length - normalizePlaceKey(a.localityJa).length);
  if (!variants.length) return base;
  const variant = variants[0];
  const suffixes = [];
  if (variant.chomeNumber) suffixes.push(`${Number(variant.chomeNumber)}-Chome`);
  if (variant.koazaJa) {
    const koazaEnglish = variant.koazaEn || derivedLocalityEnglish({
      localityJa: variant.koazaJa,
      localityKana: variant.koazaKana,
      localityEn: "",
    });
    if (koazaEnglish) suffixes.push(koazaEnglish);
  }
  const uniqueSuffixes = suffixes.filter((suffix, index) => (
    suffix && suffixes.findIndex((value) => value.toLocaleLowerCase("en") === suffix.toLocaleLowerCase("en")) === index
  ));
  return uniqueSuffixes.length ? `${base} ${uniqueSuffixes.join(" ")}` : base;
}

function deriveLocalPart(stationName, admin) {
  const name = normalize(stationName);
  const tokens = [admin?.ward, admin?.city]
    .map(normalize)
    .filter(Boolean);
  let bestEnd = -1;
  for (const token of tokens) {
    const index = name.lastIndexOf(token);
    if (index >= 0) bestEnd = Math.max(bestEnd, index + token.length);
  }
  return bestEnd >= 0 ? name.slice(bestEnd) : name;
}

function columnIndexes(header) {
  return Object.fromEntries(header.map((name, index) => [name, index]));
}

async function loadInputs() {
  const stationRoot = JSON.parse(await fs.readFile(STATIONS_PATH, "utf8"));
  const placeNameRoot = JSON.parse(await fs.readFile(PLACE_NAMES_PATH, "utf8"));
  const stations = stationRoot.stations.map((row) => ({
    code: String(row[0]),
    nameJa: String(row[1]),
    prefectureJa: String(row[2]),
    latitudeText: String(row[3]),
    longitudeText: String(row[4]),
    latitude: Number(row[3]),
    longitude: Number(row[4]),
    providerJa: String(row[5]),
    areaCode: String(row[6]),
    areaNameJa: String(row[7]),
    municipalityCode: String(row[8]),
    municipalityKey: String(row[8]).slice(0, 5),
  }));

  const municipalityKeys = new Set(stations.map((station) => station.municipalityKey));
  const admins = new Map();
  const places = new Map();
  const stream = createReadStream(DAABR_PATH, { encoding: "utf8" });
  const lines = readline.createInterface({ input: stream, crlfDelay: Infinity });
  let indexes;

  for await (const line of lines) {
    const row = parseCsvLine(line.replace(/^\uFEFF/, ""));
    if (!indexes) {
      indexes = columnIndexes(row);
      continue;
    }
    if (row[indexes.status_flg] !== "1") continue;
    const lgCode = row[indexes.lg_code];
    const municipalityKey = lgCode.slice(0, 5);
    if (!municipalityKeys.has(municipalityKey)) continue;

    if (!admins.has(municipalityKey)) {
      admins.set(municipalityKey, {
        lgCode,
        prefecture: row[indexes.pref],
        city: row[indexes.city],
        ward: row[indexes.ward],
      });
    }

    const localityJa = row[indexes.oaza_cho];
    if (!localityJa) continue;
    if (!places.has(municipalityKey)) places.set(municipalityKey, new Map());
    const municipalityPlaces = places.get(municipalityKey);
    const localityKey = normalize(localityJa);
    const existing = municipalityPlaces.get(localityKey);
    const candidate = {
      localityJa,
      localityKana: row[indexes.oaza_cho_kana],
      localityEn: row[indexes.oaza_cho_roma],
      lgCode,
      machiazaId: row[indexes.machiaza_id],
      variants: [],
    };
    const chomeJa = row[indexes.chome] ?? "";
    const koazaJa = row[indexes.koaza] ?? "";
    const variant = {
      localityJa: `${localityJa}${chomeJa}${koazaJa}`,
      chomeJa,
      chomeKana: row[indexes.chome_kana] ?? "",
      chomeNumber: row[indexes.chome_number] ?? "",
      koazaJa,
      koazaKana: row[indexes.koaza_kana] ?? "",
      koazaEn: row[indexes.koaza_roma] ?? "",
      machiazaId: row[indexes.machiaza_id],
    };
    if (!existing) {
      candidate.variants.push(variant);
      municipalityPlaces.set(localityKey, candidate);
    } else {
      existing.variants ??= [];
      existing.variants.push(variant);
      if (!existing.localityEn && candidate.localityEn) {
        existing.localityEn = candidate.localityEn;
        existing.localityKana = candidate.localityKana;
      }
    }
  }

  return {
    stations,
    admins,
    places,
    catalog: Object.fromEntries(Object.entries(stationRoot).filter(([key]) => key !== "stations")),
    officialMunicipalityNames: placeNameRoot.municipality ?? {},
  };
}

function buildAmbiguousRows({ stations, admins, places }) {
  const rows = [];
  for (const station of stations) {
    const admin = admins.get(station.municipalityKey);
    const localPart = deriveLocalPart(station.nameJa, admin);
    const localKey = normalize(localPart);
    if (!localKey) continue;
    const municipalityPlaces = places.get(station.municipalityKey);
    if (!municipalityPlaces) continue;
    const allPlaces = [...municipalityPlaces.values()];
    const exactCandidates = allPlaces.filter(
      (candidate) => normalize(candidate.localityJa) === localKey,
    );
    const candidates = (exactCandidates.length
      ? exactCandidates
      : allPlaces.filter((candidate) => normalize(candidate.localityJa).startsWith(localKey)))
      .sort((a, b) => a.localityJa.localeCompare(b.localityJa, "ja"));
    if (candidates.length <= 1) continue;
    rows.push({ ...station, admin, localPart, candidates });
  }
  return rows;
}

const OFFICIAL_STATION_ALIASES = new Map([
  ["福岡空港", "Fukuoka Airport"],
  ["鹿児島空港", "Kagoshima Airport"],
]);

function canonicalObservationNames(station, admin) {
  const names = [station.nameJa];
  if (admin?.ward && admin?.city?.endsWith("市")) {
    names.push(station.nameJa.replace(
      `${admin.city}${admin.ward}`,
      `${admin.city.slice(0, -1)}${admin.ward}`,
    ));
  }
  return uniqueStrings(names);
}

function automaticEnglishName(station, admin, officialMunicipalityNames) {
  for (const name of canonicalObservationNames(station, admin)) {
    if (OFFICIAL_STATION_ALIASES.has(name)) return OFFICIAL_STATION_ALIASES.get(name);
    if (officialMunicipalityNames[name]) return officialMunicipalityNames[name];
    const municipalityKey = Object.keys(officialMunicipalityNames)
      .filter((key) => name.startsWith(key))
      .sort((a, b) => b.length - a.length)[0];
    if (municipalityKey) return officialMunicipalityNames[municipalityKey];
  }
  return "";
}

function candidateMatchesLocalKey(candidate, localKey) {
  const candidateKey = normalize(candidate.localityJa);
  const variantKeys = (candidate.variants ?? []).map((variant) => normalize(variant.localityJa));
  return candidateKey === localKey
    || candidateKey.startsWith(localKey)
    || localKey.startsWith(candidateKey)
    || variantKeys.some((variantKey) => variantKey === localKey || variantKey.startsWith(localKey));
}

function stationCandidates(station, admin, places) {
  const localPart = deriveLocalPart(station.nameJa, admin);
  const localKey = normalize(localPart);
  const allPlaces = [...(places.get(station.municipalityKey)?.values() ?? [])];
  if (!localKey) return { localPart, candidates: [] };
  const exactCandidates = allPlaces.filter((candidate) => normalize(candidate.localityJa) === localKey);
  const candidates = (exactCandidates.length
    ? exactCandidates
    : allPlaces.filter((candidate) => candidateMatchesLocalKey(candidate, localKey)))
    .sort((a, b) => a.localityJa.localeCompare(b.localityJa, "ja"));
  return { localPart, candidates };
}

function buildAllStationRows(inputs) {
  const municipalityCounts = new Map();
  for (const station of inputs.stations) {
    municipalityCounts.set(
      station.municipalityKey,
      (municipalityCounts.get(station.municipalityKey) ?? 0) + 1,
    );
  }
  const rows = inputs.stations.map((station) => {
    const admin = inputs.admins.get(station.municipalityKey);
    const { localPart, candidates } = stationCandidates(station, admin, inputs.places);
    return {
      ...station,
      admin,
      localPart,
      candidates,
      automaticEnglishName: automaticEnglishName(
        station,
        admin,
        inputs.officialMunicipalityNames,
      ),
      municipalityStationCount: municipalityCounts.get(station.municipalityKey) ?? 1,
      municipalityPlaces: [...(inputs.places.get(station.municipalityKey)?.values() ?? [])],
    };
  });
  const automaticCounts = new Map();
  for (const row of rows) {
    if (!row.automaticEnglishName) continue;
    const key = `${row.municipalityKey}|${row.automaticEnglishName.toLocaleLowerCase("en")}`;
    automaticCounts.set(key, (automaticCounts.get(key) ?? 0) + 1);
  }
  return rows.map((row) => ({
    ...row,
    automaticCollisionCount: row.automaticEnglishName
      ? automaticCounts.get(`${row.municipalityKey}|${row.automaticEnglishName.toLocaleLowerCase("en")}`) ?? 0
      : 0,
  }));
}

async function loadCache() {
  try {
    return JSON.parse(await fs.readFile(CACHE_PATH, "utf8"));
  } catch {
    return {};
  }
}

async function saveCache(cache) {
  const ordered = Object.fromEntries(Object.entries(cache).sort(([a], [b]) => a.localeCompare(b)));
  await fs.writeFile(CACHE_PATH, `${JSON.stringify(ordered, null, 2)}\n`, "utf8");
}

function decodeHtml(value) {
  return String(value ?? "")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;|&#160;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)))
    .replace(/\s+/g, " ")
    .trim();
}

async function loadProviderCache() {
  try {
    return JSON.parse(await fs.readFile(PROVIDER_CACHE_PATH, "utf8"));
  } catch {
    return {};
  }
}

async function saveProviderCache(cache) {
  await fs.writeFile(PROVIDER_CACHE_PATH, `${JSON.stringify(cache, null, 2)}\n`, "utf8");
}

async function fetchJmaAddresses(providerCache) {
  try {
    const response = await fetch(JMA_STATION_URL, { headers: { "User-Agent": "QuakeDeck station-name audit" } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const html = await response.text();
    const current = {};
    for (const rowMatch of html.matchAll(/<tr[^>]*>([\s\S]*?)<\/tr>/gi)) {
      const cells = [...rowMatch[1].matchAll(/<td[^>]*>([\s\S]*?)<\/td>/gi)]
        .map((match) => decodeHtml(match[1]));
      if (cells.length < 9 || !cells[1] || !cells[2]) continue;
      const endDate = cells[8];
      if (!endDate) current[cells[1]] = cells[2];
    }
    providerCache.jma = {
      fetchedAt: new Date().toISOString(),
      sourceUrl: JMA_STATION_URL,
      addresses: current,
    };
    await saveProviderCache(providerCache);
  } catch (error) {
    if (!providerCache.jma?.addresses) {
      providerCache.jma = { sourceUrl: JMA_STATION_URL, addresses: {}, error: String(error?.message ?? error) };
    }
  }
  return providerCache.jma?.addresses ?? {};
}

function csrfCookieFromHeaders(headers) {
  const setCookie = headers.get("set-cookie") ?? "";
  const match = setCookie.match(/csrftoken=([^;]+)/i);
  return match ? `csrftoken=${match[1]}` : "";
}

async function fetchNiedStations(providerCache) {
  try {
    const pageResponse = await fetch(NIED_STATION_URL, { headers: { "User-Agent": "QuakeDeck station-name audit" } });
    if (!pageResponse.ok) throw new Error(`Station page HTTP ${pageResponse.status}`);
    const html = await pageResponse.text();
    const token = html.match(/name="csrfmiddlewaretoken"\s+value="([^"]+)"/i)?.[1] ?? "";
    const cookie = csrfCookieFromHeaders(pageResponse.headers);
    if (!token || !cookie) throw new Error("NIED CSRF token or cookie missing");
    const form = new URLSearchParams({ csrfmiddlewaretoken: token, datakind: "3" });
    const apiResponse = await fetch(NIED_STATION_API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "Cookie": cookie,
        "Referer": NIED_STATION_URL,
        "X-CSRFToken": token,
        "User-Agent": "QuakeDeck station-name audit",
      },
      body: form,
    });
    if (!apiResponse.ok) throw new Error(`Station API HTTP ${apiResponse.status}`);
    const payload = await apiResponse.json();
    const items = (payload.items ?? []).map((item) => ({
      network: String(item.data_type_name ?? ""),
      siteCode: String(item.sitecode ?? ""),
      nameJa: String(item.sitename_j ?? ""),
      nameEn: String(item.sitename_e ?? ""),
      latitude: Number(item.lat_jgd),
      longitude: Number(item.lon_jgd),
      prefectureJa: String(item.prefname ?? ""),
    })).filter((item) => item.siteCode && Number.isFinite(item.latitude) && Number.isFinite(item.longitude));
    providerCache.nied = {
      fetchedAt: new Date().toISOString(),
      sourceUrl: NIED_STATION_URL,
      items,
    };
    await saveProviderCache(providerCache);
  } catch (error) {
    if (!providerCache.nied?.items) {
      providerCache.nied = { sourceUrl: NIED_STATION_URL, items: [], error: String(error?.message ?? error) };
    }
  }
  return providerCache.nied?.items ?? [];
}

function haversineKm(latitude1, longitude1, latitude2, longitude2) {
  const radians = (degrees) => degrees * Math.PI / 180;
  const earthRadiusKm = 6371.0088;
  const deltaLatitude = radians(latitude2 - latitude1);
  const deltaLongitude = radians(longitude2 - longitude1);
  const a = Math.sin(deltaLatitude / 2) ** 2
    + Math.cos(radians(latitude1)) * Math.cos(radians(latitude2)) * Math.sin(deltaLongitude / 2) ** 2;
  return 2 * earthRadiusKm * Math.asin(Math.sqrt(a));
}

function matchNiedStation(row, niedStations) {
  const ranked = niedStations
    .filter((item) => item.prefectureJa === row.prefectureJa)
    .map((item) => ({
      ...item,
      distanceKm: haversineKm(row.latitude, row.longitude, item.latitude, item.longitude),
    }))
    .sort((a, b) => a.distanceKm - b.distanceKm);
  const nearest = ranked[0];
  const second = ranked[1];
  if (!nearest || nearest.distanceKm > 1.5) return null;
  const nameCompatible = normalize(row.localPart).includes(normalize(nearest.nameJa))
    || normalize(nearest.nameJa).includes(normalize(row.localPart));
  const clearlyNearest = !second || second.distanceKm - nearest.distanceKm >= 0.15;
  return nameCompatible || clearlyNearest ? nearest : null;
}

function trailingFacilityName(address) {
  return String(address ?? "").match(/[（(]([^()（）]+)[）)]\s*$/)?.[1] ?? "";
}

function uniqueStrings(values) {
  return [...new Set(values.filter(Boolean))];
}

function applyApprovedMunicipalityFallback(row, resolution) {
  const approvedName = APPROVED_MUNICIPALITY_FALLBACKS.get(row.code);
  if (!approvedName) return resolution;
  return {
    ...resolution,
    localityEn: approvedName,
    confidence: "Municipality unique",
    ready: "Yes",
    method: "User-approved municipality fallback",
    note: [
      resolution.note,
      "User-approved municipality fallback on 2026-08-19.",
      `The bundled catalogue contains exactly one current reporting station in municipality code ${row.municipalityCode}.`,
    ].filter(Boolean).join(" "),
  };
}

function buildStationSourceData(
  inputs,
  providerCache,
  jmaAddresses,
  niedStations,
  resolvedRows = [],
  geocodes = {},
) {
  const resolvedByCode = new Map(resolvedRows.map((item) => [item.row.code, item]));
  const stations = inputs.stations.map((station) => {
    const admin = inputs.admins.get(station.municipalityKey);
    const localPart = deriveLocalPart(station.nameJa, admin);
    const enriched = { ...station, admin, localPart };
    const jmaAddress = station.providerJa === "気象庁" ? jmaAddresses[station.nameJa] ?? "" : "";
    const niedMatch = station.providerJa === "防災科学技術研究所"
      ? matchNiedStation(enriched, niedStations)
      : null;
    const override = STATION_METADATA_OVERRIDES.get(station.code);
    const manual = MANUAL_CONFIRMATIONS.get(station.code);
    const resolved = resolvedByCode.get(station.code);
    const resolution = resolved?.resolution;
    const approvedMunicipalityName = APPROVED_MUNICIPALITY_FALLBACKS.get(station.code) ?? "";
    const addressJa = override?.addressJa ?? jmaAddress;
    const sourceUrls = uniqueStrings([
      ...(override?.sourceUrls ?? []),
      jmaAddress ? JMA_STATION_URL : "",
      niedMatch ? NIED_STATION_URL : "",
      resolution?.evidenceUrl ?? "",
    ]);
    const status = override?.status
      ?? (jmaAddress
        ? "Official JMA published address"
        : niedMatch
          ? "Official NIED provider coordinates"
          : "Catalogue only");
    const note = override?.note
      ?? (jmaAddress
        ? "Current JMA observation-point table publishes this address."
        : niedMatch
          ? `Matched ${niedMatch.network} station ${niedMatch.siteCode} within ${niedMatch.distanceKm.toFixed(3)} km of the rounded catalogue coordinate; no street address is recorded yet.`
          : "No exact address or precise provider-station metadata is recorded yet.");
    return {
      code: station.code,
      nameJa: station.nameJa,
      automaticEnglishName: resolved?.row.automaticEnglishName ?? "",
      automaticCollisionCount: resolved?.row.automaticCollisionCount ?? null,
      municipalityStationCount: resolved?.row.municipalityStationCount ?? null,
      resolvedEnglishName: resolution?.localityEn ?? manual?.localityEn ?? approvedMunicipalityName,
      resolutionMethod: resolution?.method ?? manual?.method
        ?? (approvedMunicipalityName ? "User-approved municipality fallback" : ""),
      resolutionConfidence: resolution?.confidence ?? "",
      readyForApp: resolution?.ready ?? "",
      resolutionEvidenceUrl: resolution?.evidenceUrl ?? null,
      resolutionNote: resolution?.note ?? null,
      approvedEnglishName: resolution?.localityEn ?? manual?.localityEn ?? approvedMunicipalityName,
      approvedEnglishNameMethod: resolution?.method ?? manual?.method
        ?? (approvedMunicipalityName ? "User-approved municipality fallback" : ""),
      prefectureJa: station.prefectureJa,
      providerJa: station.providerJa,
      providerEn: providerEnglish(station.providerJa),
      catalogueLatitude: station.latitude,
      catalogueLongitude: station.longitude,
      catalogueCoordinateText: `${station.latitudeText}, ${station.longitudeText}`,
      areaCode: station.areaCode,
      areaNameJa: station.areaNameJa,
      municipalityCode: station.municipalityCode,
      gsiLocalityJa: geocodes[station.code]?.localityJa ?? null,
      publishedAddressJa: addressJa || null,
      facilityNameJa: (override?.facilityNameJa ?? trailingFacilityName(addressJa)) || null,
      facilityNameEn: override?.facilityNameEn ?? JMA_METEOROLOGICAL_FACILITY_NAMES.get(station.code) ?? null,
      metadataStatus: status,
      providerStationCode: niedMatch?.siteCode ?? null,
      providerStationNetwork: niedMatch?.network ?? null,
      providerStationNameJa: niedMatch?.nameJa ?? null,
      providerStationNameEn: niedMatch?.nameEn ?? null,
      providerLatitude: niedMatch?.latitude ?? null,
      providerLongitude: niedMatch?.longitude ?? null,
      sourceUrls,
      note,
    };
  });
  const addressCount = stations.filter((station) => station.publishedAddressJa).length;
  const providerCoordinateCount = stations.filter((station) => station.providerStationCode).length;
  const niedCatalogueCount = stations.filter((station) => station.providerJa === "防災科学技術研究所").length;
  const niedUnmatchedCount = stations.filter(
    (station) => station.providerJa === "防災科学技術研究所" && !station.providerStationCode,
  ).length;
  const catalogueOnlyCount = stations.filter((station) => station.metadataStatus === "Catalogue only").length;
  const duplicateStationCodes = stations.length - new Set(stations.map((station) => station.code)).size;
  if (duplicateStationCodes) {
    throw new Error(`Station-source export contains ${duplicateStationCodes} duplicate station code(s)`);
  }
  return {
    schemaVersion: 2,
    catalog: inputs.catalog,
    sources: {
      jma: {
        url: providerCache.jma?.sourceUrl ?? JMA_STATION_URL,
        fetchedAt: providerCache.jma?.fetchedAt ?? null,
      },
      nied: {
        url: providerCache.nied?.sourceUrl ?? NIED_STATION_URL,
        fetchedAt: providerCache.nied?.fetchedAt ?? null,
      },
    },
    coverage: {
      stations: stations.length,
      publishedAddresses: addressCount,
      niedCatalogueStations: niedCatalogueCount,
      providerCoordinateMatches: providerCoordinateCount,
      niedUnmatched: niedUnmatchedCount,
      catalogueOnly: catalogueOnlyCount,
      approvedEnglishNames: stations.filter((station) => station.approvedEnglishName).length,
      resolvedEnglishNames: stations.filter((station) => station.resolvedEnglishName).length,
      needsResearch: stations.filter((station) => station.readyForApp !== "Yes").length,
    },
    stations,
  };
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function fetchReverseGeocode(cache, cacheKey, longitudeText, latitudeText) {
  if (cache[cacheKey] && !cache[cacheKey].error) return false;
    const url = new URL(GSI_ENDPOINT);
  url.searchParams.set("lon", longitudeText);
  url.searchParams.set("lat", latitudeText);
    let result;
    try {
      const response = await fetch(url, { headers: { "User-Agent": "QuakeDeck station-name audit" } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const payload = await response.json();
      result = {
        municipalityCode: String(payload?.results?.muniCd ?? ""),
        localityJa: String(payload?.results?.lv01Nm ?? ""),
        url: url.toString(),
        error: "",
      };
    } catch (error) {
      result = {
        municipalityCode: "",
        localityJa: "",
        url: url.toString(),
        error: String(error?.message ?? error),
      };
    }
  cache[cacheKey] = result;
  return true;
}

async function reverseGeocode(rows, niedStations) {
  const cache = await loadCache();
  let fetched = 0;
  for (const row of rows) {
    if (await fetchReverseGeocode(cache, row.code, row.longitudeText, row.latitudeText)) {
      fetched += 1;
      if (fetched % 25 === 0) await saveCache(cache);
      await wait(175);
    }
    if (row.providerJa === "防災科学技術研究所") {
      const niedMatch = matchNiedStation(row, niedStations);
      if (niedMatch && await fetchReverseGeocode(
        cache,
        `nied:${row.code}`,
        String(niedMatch.longitude),
        String(niedMatch.latitude),
      )) {
        fetched += 1;
        if (fetched % 25 === 0) await saveCache(cache);
        await wait(175);
      }
    }
  }
  if (fetched) await saveCache(cache);
  return cache;
}

const ENVELOPE_OFFSETS = [
  [-0.0075, -0.0075], [-0.0075, 0], [-0.0075, 0.0075],
  [0, -0.0075],                         [0, 0.0075],
  [0.0075, -0.0075],  [0.0075, 0],  [0.0075, 0.0075],
];

async function reverseGeocodeEnvelopes(rows, cache) {
  let fetched = 0;
  for (const row of rows) {
    for (let index = 0; index < ENVELOPE_OFFSETS.length; index += 1) {
      const [latitudeOffset, longitudeOffset] = ENVELOPE_OFFSETS[index];
      const latitude = (row.latitude + latitudeOffset).toFixed(6);
      const longitude = (row.longitude + longitudeOffset).toFixed(6);
      if (await fetchReverseGeocode(cache, `env:${row.code}:${index}`, longitude, latitude)) {
        fetched += 1;
        if (fetched % 25 === 0) await saveCache(cache);
        await wait(175);
      }
    }
  }
  if (fetched) await saveCache(cache);
  return cache;
}

function envelopeGeocodes(row, geocodes) {
  return ENVELOPE_OFFSETS.map((_, index) => geocodes[`env:${row.code}:${index}`]).filter(Boolean);
}

function candidateFromOfficialText(row, officialText) {
  const textKey = normalize(officialText);
  const matches = (row.municipalityPlaces ?? row.candidates).filter(
    (candidate) => textKey.includes(normalize(candidate.localityJa)),
  );
  return matches.length === 1 ? matches[0] : null;
}

function sourceLabelIsGenericFacility(row) {
  if (row.candidates.length > 0) return false;
  return /(?:役所|役場|庁舎|支所|出張所|消防署|消防本部|学校|大学|公園|体育館|図書館|公民館|センター|空港|港|観測所|気象台|測候所)$/u.test(row.localPart);
}

function resolveGeocodeCandidate(row, geocode, context = {}) {
  const {
    matchedConfidence = "Coordinate matched",
    matchedMethod = "GSI coordinate + DAABR romanization",
    matchedEvidenceUrl = geocode?.url ?? GSI_INFO_URL,
    matchedNote = "Official GSI reverse-geocoder selects one DAABR candidate; station coordinates may be rounded.",
    allowMunicipalityFallback = false,
  } = context;
  if (!geocode || geocode.error || (!geocode.municipalityCode && !geocode.localityJa)) {
    return {
      selected: null,
      localityEn: "",
      confidence: "Unresolved",
      ready: "No",
      evidenceUrl: geocode?.url ?? matchedEvidenceUrl,
      note: geocode?.error ? `GSI lookup failed: ${geocode.error}` : "No GSI locality result.",
      method: "No coordinate result",
    };
  }

  if (geocode.municipalityCode !== row.municipalityKey) {
    return {
      selected: null,
      localityEn: "",
      confidence: "Unresolved",
      ready: "No",
      evidenceUrl: matchedEvidenceUrl,
      note: `GSI municipality ${geocode.municipalityCode} differs from station municipality ${row.municipalityKey}.`,
      method: "Municipality mismatch",
    };
  }

  const gsiKey = normalize(geocode.localityJa);
  const keysForCandidate = (candidate) => uniqueStrings([
    normalize(candidate.localityJa),
    ...(candidate.variants ?? []).map((variant) => normalize(variant.localityJa)),
  ]);
  let exact = row.candidates.filter((candidate) => keysForCandidate(candidate).includes(gsiKey));
  let compatible = exact.length
    ? exact
    : row.candidates.filter((candidate) => {
        return keysForCandidate(candidate).some(
          (candidateKey) => gsiKey.startsWith(candidateKey) || candidateKey.startsWith(gsiKey),
        );
      });
  if (compatible.length === 0 && allowMunicipalityFallback) {
    exact = (row.municipalityPlaces ?? []).filter(
      (candidate) => keysForCandidate(candidate).includes(gsiKey),
    );
    compatible = exact;
  }

  if (compatible.length !== 1) {
    return {
      selected: null,
      localityEn: "",
      confidence: "Unresolved",
      ready: "No",
      evidenceUrl: matchedEvidenceUrl,
      note: compatible.length === 0
        ? "GSI locality is not one of the DAABR candidates."
        : "GSI locality still matches multiple DAABR candidates.",
      method: "Coordinate mismatch",
    };
  }

  const selected = compatible[0];
  const selectedEnglish = derivedCandidateEnglish(selected, geocode.localityJa);
  if (!selectedEnglish) {
    return {
      selected,
      localityEn: "",
      confidence: "Romanization missing",
      ready: "No",
      evidenceUrl: matchedEvidenceUrl,
      note: "Coordinate resolves the locality, but DAABR has no official romanization for it.",
      method: matchedMethod.replace("romanization", "locality"),
    };
  }

  return {
    selected,
    localityEn: selectedEnglish,
    confidence: selected.localityEn ? matchedConfidence : "Kana-derived romanization",
    ready: "Review",
    evidenceUrl: matchedEvidenceUrl,
    note: matchedNote,
    method: matchedMethod,
  };
}

function resolveEnvelopeCandidate(row, samples) {
  const hits = [];
  for (const sample of samples) {
    if (sample.error || sample.municipalityCode !== row.municipalityKey || !sample.localityJa) continue;
    const sampleKey = normalize(sample.localityJa);
    const compatible = row.candidates.filter((candidate) => uniqueStrings([
      normalize(candidate.localityJa),
      ...(candidate.variants ?? []).map((variant) => normalize(variant.localityJa)),
    ]).some((candidateKey) => sampleKey.startsWith(candidateKey) || candidateKey.startsWith(sampleKey)));
    if (compatible.length === 1) hits.push({ candidate: compatible[0], sample });
  }
  const unique = new Map(hits.map((hit) => [normalize(hit.candidate.localityJa), hit]));
  if (unique.size !== 1) return null;
  const { candidate, sample } = [...unique.values()][0];
  const candidateEnglish = derivedCandidateEnglish(candidate, sample.localityJa);
  return {
    selected: candidate,
    localityEn: candidateEnglish,
    confidence: candidate.localityEn
      ? "Coordinate envelope matched"
      : candidateEnglish ? "Kana-derived romanization" : "Romanization missing",
    ready: candidateEnglish ? "Review" : "No",
    evidenceUrl: sample.url,
    note: `${hits.length} of ${samples.length} uncertainty-envelope samples hit one DAABR candidate and none hit another; source local-government coordinates are minute-scale approximations.`,
    method: candidateEnglish
      ? "GSI coordinate envelope + DAABR romanization"
      : "GSI coordinate envelope + DAABR locality",
  };
}

function resolveCandidate(row, geocode, jmaAddress, niedMatch, niedGeocode, envelopeSamples = []) {
  const manual = MANUAL_CONFIRMATIONS.get(row.code);
  if (manual) {
    const candidate = row.candidates.find(
      (item) => normalize(item.localityJa) === normalize(manual.localityJa),
    );
    return {
      selected: candidate ?? { localityJa: manual.localityJa, localityEn: manual.localityEn },
      localityEn: manual.localityEn,
      confidence: manual.confidence,
      ready: manual.ready,
      evidenceUrl: manual.evidenceUrl,
      note: manual.note,
      method: manual.method ?? "Official installation address",
    };
  }

  if (jmaAddress) {
    const candidate = candidateFromOfficialText(row, jmaAddress);
    if (candidate) {
      const candidateEnglish = derivedCandidateEnglish(candidate, jmaAddress);
      return {
        selected: candidate,
        localityEn: candidateEnglish,
        confidence: candidate.localityEn
          ? "Address confirmed"
          : candidateEnglish ? "Kana-derived romanization" : "Romanization missing",
        ready: candidateEnglish ? "Yes" : "No",
        evidenceUrl: JMA_STATION_URL,
        note: `JMA installation address: ${jmaAddress}`,
        method: "Official JMA installation address",
      };
    }
  }

  if (niedMatch && niedGeocode) {
    const precise = resolveGeocodeCandidate(row, niedGeocode, {
      matchedConfidence: "Provider coordinate matched",
      matchedMethod: "NIED coordinate + GSI + DAABR romanization",
      matchedEvidenceUrl: NIED_STATION_URL,
      matchedNote: `Matched NIED ${niedMatch.network} station ${niedMatch.siteCode} ${niedMatch.nameJa} (${niedMatch.nameEn}) at ${niedMatch.latitude}, ${niedMatch.longitude}; ${niedMatch.distanceKm.toFixed(3)} km from the rounded catalogue coordinate.`,
      allowMunicipalityFallback: true,
    });
    if (precise.selected) return precise;
  }

  const sourceResolution = resolveGeocodeCandidate(row, geocode, {
    allowMunicipalityFallback: sourceLabelIsGenericFacility(row),
  });
  if (sourceResolution.selected) return sourceResolution;
  return resolveEnvelopeCandidate(row, envelopeSamples) ?? sourceResolution;
}

function providerEnglish(providerJa) {
  return {
    気象庁: "JMA",
    防災科学技術研究所: "NIED",
    地方公共団体: "Local government",
  }[providerJa] ?? providerJa;
}

function displayNiedName(value) {
  return String(value ?? "")
    .trim()
    .toLocaleLowerCase("en")
    .replace(/(^|[-\s])(\p{L})/gu, (_, prefix, letter) => `${prefix}${letter.toLocaleUpperCase("en")}`)
    .replace(/(\D)(\d+)$/u, "$1 $2");
}

function sharedOfficialEnglishPrefix(candidates) {
  const official = candidates
    .filter((candidate) => candidate.localityEn)
    .map((candidate) => derivedLocalityEnglish(candidate))
    .filter(Boolean);
  if (official.length < 2) return "";
  const tokenRows = official.map((value) => value.split(/\s+/).filter(Boolean));
  const shared = [];
  for (let index = 0; index < Math.min(...tokenRows.map((tokens) => tokens.length)); index += 1) {
    const token = tokenRows[0][index];
    if (!tokenRows.every((tokens) => tokens[index]?.toLocaleLowerCase("en") === token.toLocaleLowerCase("en"))) {
      break;
    }
    shared.push(token);
  }
  return shared.join(" ");
}

function explicitMunicipalOfficeName(row) {
  if (!/(?:役所|役場)$/u.test(row.localPart)) return "";
  const municipality = String(row.automaticEnglishName ?? "").trim();
  if (/ City(?:,|$)/u.test(municipality)) return municipality.replace(/ City(?=,|$)/u, " City Hall");
  if (/ Town(?:,|$)/u.test(municipality)) return municipality.replace(/ Town(?=,|$)/u, " Town Office");
  if (/ Village(?:,|$)/u.test(municipality)) return municipality.replace(/ Village(?=,|$)/u, " Village Office");
  return "";
}

function resolveAllStation(row, geocodes, jmaAddresses, niedStations) {
  const manual = MANUAL_CONFIRMATIONS.get(row.code);
  if (manual) {
    return {
      selected: row.candidates.find(
        (candidate) => normalize(candidate.localityJa) === normalize(manual.localityJa),
      ) ?? null,
      localityEn: manual.localityEn,
      confidence: manual.confidence,
      ready: "Yes",
      evidenceUrl: manual.evidenceUrl,
      note: manual.note,
      method: manual.method ?? "Verified station identity",
    };
  }

  const officialJmaFacilityName = JMA_METEOROLOGICAL_FACILITY_NAMES.get(row.code);
  if (officialJmaFacilityName) {
    const jmaAddress = jmaAddresses[row.nameJa] ?? "";
    return {
      selected: null,
      localityEn: officialJmaFacilityName,
      confidence: "Official facility identity",
      ready: "Yes",
      evidenceUrl: JMA_STATION_URL,
      note: `JMA installation address: ${jmaAddress}`,
      method: "Official JMA facility identity",
    };
  }

  const researched = RESEARCHED_NAME_OVERRIDES.get(row.code);
  if (researched) {
    return {
      selected: null,
      ...researched,
    };
  }

  const explicitStationName = EXPLICIT_STATION_NAME_OVERRIDES.get(row.code)
    ?? explicitMunicipalOfficeName(row);
  if (explicitStationName) {
    return {
      selected: null,
      localityEn: explicitStationName,
      confidence: "Explicit station identity",
      ready: "Yes",
      evidenceUrl: JMA_INTENSITY_MAP_URL,
      note: "The official Japanese observation-point label identifies this locality or facility directly; the English name preserves that identity without an operator or network prefix.",
      method: "Official station label transliteration",
    };
  }

  const approvedMunicipalityName = APPROVED_MUNICIPALITY_FALLBACKS.get(row.code);
  if (approvedMunicipalityName) {
    return {
      selected: null,
      localityEn: approvedMunicipalityName,
      confidence: "Municipality unique",
      ready: "Yes",
      evidenceUrl: JMA_STATION_URL,
      note: `User-approved municipality fallback. The bundled catalogue contains exactly one current reporting station in municipality code ${row.municipalityCode}.`,
      method: "User-approved municipality fallback",
    };
  }

  const niedMatch = row.providerJa === "防災科学技術研究所"
    ? matchNiedStation(row, niedStations)
    : null;

  const jmaAddress = row.providerJa === "気象庁" ? jmaAddresses[row.nameJa] ?? "" : "";
  if (jmaAddress) {
    const addressCandidate = candidateFromOfficialText(row, jmaAddress);
    const addressCandidateEnglish = derivedCandidateEnglish(addressCandidate, jmaAddress);
    if (addressCandidateEnglish) {
      return {
        selected: addressCandidate,
        localityEn: addressCandidateEnglish,
        confidence: addressCandidate.localityEn
          ? "Official address matched"
          : "Official address + kana romanization",
        ready: "Yes",
        evidenceUrl: JMA_STATION_URL,
        note: `JMA installation address: ${jmaAddress}`,
        method: addressCandidate.localityEn
          ? "Official JMA address + DAABR romanization"
          : "Official JMA address + DAABR kana-derived romanization",
      };
    }
  }

  const geocode = geocodes[row.code];
  const niedGeocode = niedMatch ? geocodes[`nied:${row.code}`] : null;
  const coordinateResolution = resolveCandidate(
    row,
    geocode,
    jmaAddress,
    niedMatch,
    niedGeocode,
    envelopeGeocodes(row, geocodes),
  );
  if (niedMatch && coordinateResolution.localityEn) {
    return {
      ...coordinateResolution,
      ready: "Yes",
    };
  }

  if (row.candidates.length === 1 && derivedCandidateEnglish(row.candidates[0], row.localPart)) {
    const candidate = row.candidates[0];
    const candidateEnglish = derivedCandidateEnglish(candidate, row.localPart);
    return {
      selected: candidate,
      localityEn: candidateEnglish,
      confidence: candidate.localityEn ? "Unique source locality" : "Unique kana-derived locality",
      ready: "Yes",
      evidenceUrl: "https://catalog.registries.digital.go.jp/rc/dataset/ba-o1-000000_g2-000003",
      note: "The station's Japanese locality label matches one active DAABR oaza/cho in its municipality.",
      method: candidate.localityEn
        ? "Station label + DAABR romanization"
        : "Station label + DAABR kana-derived romanization",
    };
  }

  const sharedCandidatePrefix = sharedOfficialEnglishPrefix(row.candidates);
  if (sharedCandidatePrefix) {
    return {
      selected: null,
      localityEn: sharedCandidatePrefix,
      confidence: "Shared official locality prefix",
      ready: "Yes",
      evidenceUrl: "https://catalog.registries.digital.go.jp/rc/dataset/ba-o1-000000_g2-000003",
      note: "Every DAABR candidate covered by the station's broader Japanese source label shares this official English locality prefix.",
      method: "Station label + shared DAABR romanization",
    };
  }

  if (coordinateResolution.localityEn) {
    return {
      ...coordinateResolution,
      localityEn: coordinateResolution.localityEn,
      ready: "Yes",
    };
  }

  if (niedMatch?.nameEn) {
    return {
      selected: null,
      localityEn: `${niedMatch.network} ${displayNiedName(niedMatch.nameEn)}`,
      confidence: "Official provider station identity fallback",
      ready: "Yes",
      evidenceUrl: NIED_STATION_URL,
      note: `No stronger facility or locality name resolved. Matched official ${niedMatch.network} station ${niedMatch.siteCode} ${niedMatch.nameJa} (${niedMatch.nameEn}) at ${niedMatch.latitude}, ${niedMatch.longitude}; ${niedMatch.distanceKm.toFixed(3)} km from the rounded catalogue coordinate.`,
      method: "Official NIED station identity fallback",
      qualifierNecessity: "No stronger facility or locality resolution",
    };
  }

  if (row.automaticEnglishName && row.automaticCollisionCount === 1) {
    return {
      selected: coordinateResolution.selected ?? null,
      localityEn: row.automaticEnglishName,
      confidence: "Unique official automatic name",
      ready: "Yes",
      evidenceUrl: "https://www.jma.go.jp/jma/kishou/books/saigaiji/saigaiji_202101/denbun/202101/17/VP20210117013000_3.html",
      note: "The official JMA English dictionary produces a station-specific name that does not collide with another station in the same municipality.",
      method: "Official JMA English place-name dictionary",
    };
  }

  const municipalityFallback = APPROVED_MUNICIPALITY_FALLBACKS.get(row.code)
    ?? (row.municipalityStationCount === 1 ? row.automaticEnglishName : "");
  if (municipalityFallback) {
    return {
      selected: coordinateResolution.selected ?? null,
      localityEn: municipalityFallback,
      confidence: "Single station in municipality",
      ready: "Yes",
      evidenceUrl: JMA_STATION_URL,
      note: `The bundled catalogue has one current reporting station in municipality code ${row.municipalityCode}; the official JMA municipality name is unambiguous.`,
      method: APPROVED_MUNICIPALITY_FALLBACKS.has(row.code)
        ? "User-approved municipality fallback"
        : "Official JMA municipality fallback",
    };
  }

  return {
    ...coordinateResolution,
    localityEn: "",
    ready: "No",
  };
}

function resolveAllStations(rows, geocodes, jmaAddresses, niedStations) {
  const resolvedRows = rows.map((row) => ({
    row,
    geocode: geocodes[row.code],
    resolution: resolveAllStation(row, geocodes, jmaAddresses, niedStations),
  }));
  const groupedNames = () => {
    const groups = new Map();
    for (const item of resolvedRows) {
      if (!item.resolution.localityEn) continue;
      const key = `${item.row.municipalityKey}|${item.resolution.localityEn.toLocaleLowerCase("en")}`;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(item);
    }
    return groups;
  };

  for (const group of groupedNames().values()) {
    if (group.length <= 1) continue;
    for (const item of group.filter(({ row }) => row.providerJa === "防災科学技術研究所")) {
      const niedMatch = matchNiedStation(item.row, niedStations);
      if (!niedMatch?.nameEn) continue;
      const previousName = item.resolution.localityEn;
      item.resolution = {
        selected: null,
        localityEn: `${niedMatch.network} ${displayNiedName(niedMatch.nameEn)}`,
        confidence: "Official provider identity required for collision",
        ready: "Yes",
        evidenceUrl: NIED_STATION_URL,
        note: `The stronger locality name "${previousName}" collides with another station in the same municipality, so the official ${niedMatch.network} identity ${niedMatch.siteCode} ${niedMatch.nameEn} is required to distinguish this station.`,
        method: "Official NIED station identity required for collision",
        qualifierNecessity: "Same-municipality collision",
        unqualifiedName: previousName,
        unqualifiedCollisionCount: group.length,
      };
    }
  }

  for (const group of groupedNames().values()) {
    if (group.length <= 1) continue;
    for (const item of group) {
      item.resolution = {
        ...item.resolution,
        confidence: "Resolved-name collision",
        ready: "No",
        note: `${item.resolution.note} The proposed English name still collides with ${group.length - 1} other station(s) in the same municipality.`,
        method: `${item.resolution.method}; collision requires a more specific identity`,
      };
    }
  }
  return resolvedRows;
}

function validateStationNameConventions(resolvedRows) {
  const violations = [];
  let verifiedJmaFacilityNames = 0;
  let verifiedNetworkFacilityNames = 0;
  let networkFallbackNames = 0;
  let networkCollisionNames = 0;
  for (const { row, resolution } of resolvedRows) {
    const name = resolution.localityEn ?? "";
    if (/^JMA\s/u.test(name)) {
      if (VERIFIED_OPERATOR_QUALIFIED_DISPLAY_NAMES.get(row.code) === name) {
        verifiedJmaFacilityNames += 1;
      } else {
        violations.push(`${row.code}: unverified JMA-qualified name "${name}"`);
      }
    }
    if (/^(?:NIED|Local government)\s/u.test(name)) {
      violations.push(`${row.code}: provider label was added to "${name}"`);
    }
    if (/^(?:K-NET|KiK-net)\s/u.test(name)) {
      if (row.providerJa !== "防災科学技術研究所") {
        violations.push(`${row.code}: network label is not backed by an official NIED station identity: "${name}"`);
      } else if (resolution.method === "Official NIED station identity fallback") {
        if (resolution.qualifierNecessity === "No stronger facility or locality resolution") {
          networkFallbackNames += 1;
        } else {
          violations.push(`${row.code}: fallback network label has no necessity proof: "${name}"`);
        }
      } else if (resolution.method === "Official NIED station identity required for collision") {
        if (resolution.qualifierNecessity === "Same-municipality collision"
          && resolution.unqualifiedName
          && resolution.unqualifiedCollisionCount > 1) {
          networkCollisionNames += 1;
        } else {
          violations.push(`${row.code}: collision network label has no collision proof: "${name}"`);
        }
      } else if (VERIFIED_NETWORK_QUALIFIED_DISPLAY_NAMES.get(row.code) === name) {
        verifiedNetworkFacilityNames += 1;
      } else {
        violations.push(`${row.code}: network label is not proven necessary: "${name}"`);
      }
    }
  }
  if (violations.length) {
    throw new Error(`Station-name convention audit failed:\n${violations.join("\n")}`);
  }
  return {
    verifiedJmaFacilityNames,
    verifiedNetworkFacilityNames,
    networkFallbackNames,
    networkCollisionNames,
    violations: 0,
  };
}

function validateStationNameQuality(resolvedRows) {
  const violations = [];
  const nameGroups = new Map();
  const facilitySemantics = [
    [/役所$/u, /City Hall/iu],
    [/役場$/u, /(?:Town|Village) Office/iu],
    [/(?:支所|出張所)$/u, /Branch Office/iu],
    [/消防分署$/u, /Fire Substation/iu],
    [/消防署$/u, /Fire Station/iu],
    [/消防本部$/u, /Fire Department Headquarters/iu],
    [/小学校$/u, /Elementary School/iu],
    [/中学校$/u, /Junior High School/iu],
    [/高等学校$/u, /High School/iu],
    [/空港$/u, /Airport/iu],
    [/体育館$/u, /Gymnasium/iu],
    [/図書館$/u, /Library/iu],
    [/公民館$/u, /Community Center/iu],
    [/郵便局$/u, /Post Office/iu],
    [/保育園$/u, /Nursery School/iu],
    [/浄水場$/u, /Water Purification Plant/iu],
  ];
  let kanaDerivedNames = 0;
  for (const { row, resolution } of resolvedRows) {
    const name = String(resolution.localityEn ?? "").trim();
    if (!name) violations.push(`${row.code}: blank English name`);
    if (resolution.ready !== "Yes") violations.push(`${row.code}: name is not ready`);
    if (!resolution.evidenceUrl) violations.push(`${row.code}: name has no evidence URL`);
    if (/[\u3040-\u30ff\u3400-\u9fff]/u.test(name)) {
      violations.push(`${row.code}: English name still contains Japanese text: "${name}"`);
    }
    if (/\b(?:Ooaza|Oaza|Aza)\b|chou|myou|choume|banchou/iu.test(name)) {
      violations.push(`${row.code}: unnormalized administrative or long-vowel spelling: "${name}"`);
    }
    if (/\s{2,}|^[-,\s]|[-,\s]$/u.test(name)) {
      violations.push(`${row.code}: malformed spacing or punctuation: "${name}"`);
    }
    if (/(?:\p{L}\d|\d\p{L})/u.test(name)) {
      violations.push(`${row.code}: missing separator between a word and number: "${name}"`);
    }
    for (const [japanesePattern, englishPattern] of facilitySemantics) {
      if (japanesePattern.test(row.localPart) && !englishPattern.test(name)) {
        violations.push(`${row.code}: explicit facility semantics were lost: "${row.localPart}" -> "${name}"`);
      }
    }
    if (/kana-derived/iu.test(resolution.confidence) || /kana-derived/iu.test(resolution.method)) {
      kanaDerivedNames += 1;
    }
    if (name) {
      const key = `${row.municipalityKey}|${name.toLocaleLowerCase("en")}`;
      if (!nameGroups.has(key)) nameGroups.set(key, []);
      nameGroups.get(key).push(row.code);
    }
  }
  for (const codes of nameGroups.values()) {
    if (codes.length > 1) violations.push(`same-municipality collision: ${codes.join(", ")}`);
  }
  if (violations.length) {
    throw new Error(`Station-name quality audit failed:\n${violations.join("\n")}`);
  }
  return {
    stationsChecked: resolvedRows.length,
    kanaDerivedNames,
    sameMunicipalityCollisions: 0,
    missingNames: 0,
    missingEvidence: 0,
    unnormalizedNames: 0,
    violations: 0,
  };
}

function countBy(rows, selector) {
  const counts = new Map();
  for (const row of rows) {
    const key = selector(row);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0])));
}

async function buildWorkbook(rows, geocodes, jmaAddresses, niedStations, stationSourceData) {
  const resolvedRows = rows.map((row) => {
    const geocode = geocodes[row.code];
    const jmaAddress = row.providerJa === "気象庁" ? jmaAddresses[row.nameJa] : "";
    const niedMatch = row.providerJa === "防災科学技術研究所" ? matchNiedStation(row, niedStations) : null;
    const niedGeocode = niedMatch ? geocodes[`nied:${row.code}`] : null;
    const resolution = applyApprovedMunicipalityFallback(
      row,
      resolveCandidate(
        row,
        geocode,
        jmaAddress,
        niedMatch,
        niedGeocode,
        envelopeGeocodes(row, geocodes),
      ),
    );
    return { row, geocode, resolution, jmaAddress, niedMatch, niedGeocode };
  });

  const workbook = Workbook.create();
  const summary = workbook.worksheets.add("Summary");
  const mapping = workbook.worksheets.add("Proposed Mapping");
  const candidates = workbook.worksheets.add("DAABR Candidates");
  const unresolved = workbook.worksheets.add("Needs Research");
  const stationSources = workbook.worksheets.add("Station Sources");

  const mappingHeaders = [
    "Station code", "Japanese station name", "Prefecture", "Provider", "Latitude", "Longitude",
    "Municipality code", "Short source label", "Candidate count", "GSI locality", "Selected DAABR locality",
    "Official English locality", "Method", "Confidence", "Ready for app", "Evidence URL", "Notes",
  ];
  const mappingValues = resolvedRows.map(({ row, geocode, resolution }) => [
    Number(row.code),
    row.nameJa,
    row.prefectureJa,
    providerEnglish(row.providerJa),
    row.latitude,
    row.longitude,
    Number(row.municipalityCode),
    row.localPart,
    row.candidates.length,
    geocode?.localityJa ?? "",
    resolution.selected?.localityJa ?? "",
    resolution.localityEn,
    resolution.method,
    resolution.confidence,
    resolution.ready,
    resolution.evidenceUrl,
    resolution.note,
  ]);

  mapping.getRangeByIndexes(0, 0, 1, mappingHeaders.length).values = [mappingHeaders];
  if (mappingValues.length) {
    mapping.getRangeByIndexes(1, 0, mappingValues.length, mappingHeaders.length).values = mappingValues;
  }
  mapping.tables.add(`A1:Q${mappingValues.length + 1}`, true, "ProposedMappingTable");
  mapping.freezePanes.freezeRows(1);
  mapping.freezePanes.freezeColumns(2);
  mapping.showGridLines = false;

  const candidateHeaders = [
    "Station code", "Japanese station name", "Short source label", "Candidate rank", "DAABR locality",
    "DAABR kana", "DAABR official English", "DAABR municipality code", "Machiaza ID", "Selected",
  ];
  const candidateValues = [];
  for (const { row, resolution } of resolvedRows) {
    row.candidates.forEach((candidate, index) => {
      candidateValues.push([
        Number(row.code),
        row.nameJa,
        row.localPart,
        index + 1,
        candidate.localityJa,
        candidate.localityKana,
        candidate.localityEn,
        Number(candidate.lgCode),
        Number(candidate.machiazaId),
        normalize(candidate.localityJa) === normalize(resolution.selected?.localityJa) ? "Yes" : "",
      ]);
    });
  }
  candidates.getRangeByIndexes(0, 0, 1, candidateHeaders.length).values = [candidateHeaders];
  if (candidateValues.length) {
    candidates.getRangeByIndexes(1, 0, candidateValues.length, candidateHeaders.length).values = candidateValues;
  }
  candidates.tables.add(`A1:J${candidateValues.length + 1}`, true, "DaabrCandidatesTable");
  candidates.freezePanes.freezeRows(1);
  candidates.freezePanes.freezeColumns(2);
  candidates.showGridLines = false;

  const unresolvedRows = mappingValues.filter((values) => values[14] !== "Yes" && values[14] !== "Review");
  unresolved.getRangeByIndexes(0, 0, 1, mappingHeaders.length).values = [mappingHeaders];
  if (unresolvedRows.length) {
    unresolved.getRangeByIndexes(1, 0, unresolvedRows.length, mappingHeaders.length).values = unresolvedRows;
  }
  unresolved.tables.add(`A1:Q${unresolvedRows.length + 1}`, true, "NeedsResearchTable");
  unresolved.freezePanes.freezeRows(1);
  unresolved.freezePanes.freezeColumns(2);
  unresolved.showGridLines = false;

  const sourceHeaders = [
    "Station code", "Japanese station name", "Approved English name", "English-name method",
    "Prefecture", "Provider", "Catalogue latitude", "Catalogue longitude", "Catalogue coordinate text",
    "Area code", "Area name", "Municipality code", "Published address (Japanese)",
    "Facility name (Japanese)", "Facility name (official English)", "Metadata status",
    "Provider station code", "Provider network", "Provider station name (Japanese)",
    "Provider station name (English)", "Provider latitude", "Provider longitude", "Evidence URLs", "Notes",
  ];
  const sourceValues = stationSourceData.stations.map((station) => [
    Number(station.code),
    station.nameJa,
    station.approvedEnglishName,
    station.approvedEnglishNameMethod,
    station.prefectureJa,
    station.providerEn,
    station.catalogueLatitude,
    station.catalogueLongitude,
    station.catalogueCoordinateText,
    Number(station.areaCode),
    station.areaNameJa,
    Number(station.municipalityCode),
    station.publishedAddressJa ?? "",
    station.facilityNameJa ?? "",
    station.facilityNameEn ?? "",
    station.metadataStatus,
    station.providerStationCode ?? "",
    station.providerStationNetwork ?? "",
    station.providerStationNameJa ?? "",
    station.providerStationNameEn ?? "",
    station.providerLatitude,
    station.providerLongitude,
    station.sourceUrls.join(" | "),
    station.note,
  ]);
  stationSources.getRangeByIndexes(0, 0, 1, sourceHeaders.length).values = [sourceHeaders];
  stationSources.getRangeByIndexes(1, 0, sourceValues.length, sourceHeaders.length).values = sourceValues;
  stationSources.tables.add(`A1:X${sourceValues.length + 1}`, true, "StationSourcesTable");
  stationSources.freezePanes.freezeRows(1);
  stationSources.freezePanes.freezeColumns(2);
  stationSources.showGridLines = false;

  const confidenceCounts = countBy(resolvedRows, ({ resolution }) => resolution.confidence);
  const providerCounts = countBy(resolvedRows, ({ row }) => providerEnglish(row.providerJa));
  summary.getRange("A1:H1").merge();
  summary.getRange("A1").values = [["QuakeDeck ambiguous station-name audit"]];
  summary.getRange("A3:B8").values = [
    ["Metric", "Count"],
    ["Ambiguous source labels", resolvedRows.length],
    ["Address confirmed", resolvedRows.filter(({ resolution }) => resolution.confidence === "Address confirmed").length],
    ["Coordinate-backed", resolvedRows.filter(({ resolution }) => resolution.ready === "Review").length],
    ["Needs further research", unresolvedRows.length],
    ["DAABR candidate rows", candidateValues.length],
  ];
  summary.getRange("D3:E3").values = [["Confidence", "Count"]];
  if (confidenceCounts.length) summary.getRangeByIndexes(3, 3, confidenceCounts.length, 2).values = confidenceCounts;
  summary.getRange("G3:H3").values = [["Provider", "Count"]];
  if (providerCounts.length) summary.getRangeByIndexes(3, 6, providerCounts.length, 2).values = providerCounts;
  summary.getRange("A11:H14").merge();
  summary.getRange("A11").values = [[
    "Method: station labels are matched to all active DAABR oaza/cho candidates in the same municipality. " +
    "The official GSI reverse-geocoder then selects the coordinate locality. DAABR supplies the official English " +
    "romanization. Coordinate-only rows remain marked Review because catalogue coordinates may be rounded; " +
    "documented installation addresses take precedence.",
  ]];
  summary.getRange("A16:H18").merge();
  summary.getRange("A16").values = [[
    "Sources: DAABR 全国 町字マスター (local mt_town_all.csv); bundled QuakeDeck station catalogue; " +
    "GSI reverse-geocoder. Each researched row retains its direct evidence URL in Proposed Mapping.",
  ]];
  summary.getRange("A20:B27").values = [
    ["All-station source coverage", "Count"],
    ["Bundled stations", stationSourceData.coverage.stations],
    ["Published addresses", stationSourceData.coverage.publishedAddresses],
    ["NIED catalogue stations", stationSourceData.coverage.niedCatalogueStations],
    ["NIED provider-coordinate matches", stationSourceData.coverage.providerCoordinateMatches],
    ["NIED without provider-coordinate match", stationSourceData.coverage.niedUnmatched],
    ["Catalogue-only records", stationSourceData.coverage.catalogueOnly],
    ["Approved English names", stationSourceData.coverage.approvedEnglishNames],
  ];
  summary.getRange("D20:H27").merge();
  summary.getRange("D20").values = [[
    "Station Sources contains one provenance record for every bundled station. Blank addresses are intentional: " +
    "they mean that no exact sourced address has been recorded yet, not that a rounded coordinate was treated as an address.",
  ]];
  summary.showGridLines = false;

  const navy = "#16324F";
  const blue = "#2F75B5";
  const paleBlue = "#DCEAF7";
  const paleYellow = "#FFF2CC";
  const paleRed = "#FCE4D6";
  const paleGreen = "#E2F0D9";
  for (const sheet of [mapping, candidates, unresolved, stationSources]) {
    const used = sheet.getUsedRange();
    used.format.font = { name: "Aptos", size: 10, color: "#222222" };
    used.format.verticalAlignment = "center";
    const header = sheet.getRangeByIndexes(0, 0, 1, used.columnCount);
    header.format = {
      fill: navy,
      font: { name: "Aptos", size: 10, bold: true, color: "#FFFFFF" },
      verticalAlignment: "center",
      wrapText: true,
      rowHeight: 32,
    };
    used.format.borders = { preset: "inside", style: "thin", color: "#D9E2F3" };
  }

  mapping.getRange(`E2:F${mappingValues.length + 1}`).format.numberFormat = "0.00000";
  mapping.getRange(`I2:I${mappingValues.length + 1}`).format.numberFormat = "0";
  mapping.getRange(`N2:O${mappingValues.length + 1}`).format.wrapText = true;
  mapping.getRange(`Q2:Q${mappingValues.length + 1}`).format.wrapText = true;
  mapping.getRange(`A2:A${mappingValues.length + 1}`).format.numberFormat = "0000000";
  mapping.getRange(`G2:G${mappingValues.length + 1}`).format.numberFormat = "0000000";
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Address confirmed", format: { fill: paleGreen, font: { bold: true, color: "#375623" } },
  });
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Facility confirmed", format: { fill: paleGreen, font: { bold: true, color: "#375623" } },
  });
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "matched", format: { fill: paleBlue, font: { color: blue } },
  });
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Unresolved", format: { fill: paleRed, font: { bold: true, color: "#9C0006" } },
  });
  mapping.getRange(`N2:N${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "missing", format: { fill: paleYellow, font: { color: "#9C6500" } },
  });

  candidates.getRange(`A2:A${candidateValues.length + 1}`).format.numberFormat = "0000000";
  candidates.getRange(`H2:H${candidateValues.length + 1}`).format.numberFormat = "000000";
  candidates.getRange(`I2:I${candidateValues.length + 1}`).format.numberFormat = "0000000";
  candidates.getRange(`J2:J${candidateValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Yes", format: { fill: paleGreen, font: { bold: true, color: "#375623" } },
  });
  unresolved.getRange(`A2:A${unresolvedRows.length + 1}`).format.numberFormat = "0000000";
  unresolved.getRange(`G2:G${unresolvedRows.length + 1}`).format.numberFormat = "0000000";
  unresolved.getRange(`Q2:Q${unresolvedRows.length + 1}`).format.wrapText = true;

  stationSources.getRange(`A2:A${sourceValues.length + 1}`).format.numberFormat = "0000000";
  stationSources.getRange(`G2:H${sourceValues.length + 1}`).format.numberFormat = "0.00000";
  stationSources.getRange(`J2:J${sourceValues.length + 1}`).format.numberFormat = "000";
  stationSources.getRange(`L2:L${sourceValues.length + 1}`).format.numberFormat = "0000000";
  stationSources.getRange(`U2:V${sourceValues.length + 1}`).format.numberFormat = "0.00000";
  stationSources.getRange(`M2:P${sourceValues.length + 1}`).format.wrapText = true;
  stationSources.getRange(`W2:X${sourceValues.length + 1}`).format.wrapText = true;

  const widths = [12, 25, 12, 18, 11, 11, 15, 20, 11, 22, 25, 28, 27, 26, 14, 48, 58];
  widths.forEach((width, index) => {
    mapping.getRangeByIndexes(0, index, mappingValues.length + 1, 1).format.columnWidth = width;
    unresolved.getRangeByIndexes(0, index, Math.max(1, unresolvedRows.length + 1), 1).format.columnWidth = width;
  });
  const candidateWidths = [12, 25, 20, 12, 24, 24, 30, 18, 14, 12];
  candidateWidths.forEach((width, index) => {
    candidates.getRangeByIndexes(0, index, candidateValues.length + 1, 1).format.columnWidth = width;
  });
  const sourceWidths = [
    12, 25, 30, 28, 12, 18, 12, 12, 22, 10, 22, 15,
    48, 28, 32, 30, 18, 16, 26, 26, 12, 12, 55, 60,
  ];
  sourceWidths.forEach((width, index) => {
    stationSources.getRangeByIndexes(0, index, sourceValues.length + 1, 1).format.columnWidth = width;
  });

  summary.getRange("A1:H1").format = {
    fill: navy,
    font: { name: "Aptos Display", size: 18, bold: true, color: "#FFFFFF" },
    horizontalAlignment: "left",
    verticalAlignment: "center",
    rowHeight: 34,
  };
  for (const headerRange of ["A3:B3", "D3:E3", "G3:H3"]) {
    summary.getRange(headerRange).format = {
      fill: blue,
      font: { bold: true, color: "#FFFFFF" },
      horizontalAlignment: "left",
    };
  }
  summary.getRange("A3:B8").format.borders = { preset: "inside", style: "thin", color: "#D9E2F3" };
  summary.getRange("A11:H14").format = { fill: paleBlue, wrapText: true, verticalAlignment: "top" };
  summary.getRange("A16:H18").format = { fill: "#F2F2F2", wrapText: true, verticalAlignment: "top" };
  summary.getRange("A20:B20").format = {
    fill: blue,
    font: { bold: true, color: "#FFFFFF" },
  };
  summary.getRange("A20:B27").format.borders = { preset: "inside", style: "thin", color: "#D9E2F3" };
  summary.getRange("D20:H27").format = { fill: paleGreen, wrapText: true, verticalAlignment: "top" };
  summary.getRange("A1:H27").format.font = { name: "Aptos", size: 10, color: "#222222" };
  summary.getRange("A1:H1").format.font = { name: "Aptos Display", size: 18, bold: true, color: "#FFFFFF" };
  for (const headerRange of ["A3:B3", "D3:E3", "G3:H3", "A20:B20"]) {
    summary.getRange(headerRange).format.font = { name: "Aptos", size: 10, bold: true, color: "#FFFFFF" };
  }
  [42, 14, 12, 32, 12, 4, 22, 12].forEach((width, index) => {
    summary.getRangeByIndexes(0, index, 27, 1).format.columnWidth = width;
  });

  await fs.mkdir(OUTPUT_DIR, { recursive: true });
  await fs.writeFile(STATION_SOURCES_PATH, `${JSON.stringify(stationSourceData, null, 2)}\n`, "utf8");
  const inspection = await workbook.inspect({
    kind: "table",
    range: "'Proposed Mapping'!A1:Q8",
    include: "values,formulas",
    tableMaxRows: 8,
    tableMaxCols: 17,
    maxChars: 12000,
  });
  const errors = await workbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 100 },
    summary: "final formula error scan",
    maxChars: 3000,
  });
  const preview = await workbook.render({ sheetName: "Summary", range: "A1:H27", scale: 1.5, format: "png" });
  await fs.writeFile(PREVIEW_PATH, new Uint8Array(await preview.arrayBuffer()));
  const mappingPreview = await workbook.render({
    sheetName: "Proposed Mapping",
    range: `A1:Q${Math.min(mappingValues.length + 1, 14)}`,
    scale: 1,
    format: "png",
  });
  await fs.writeFile(MAPPING_PREVIEW_PATH, new Uint8Array(await mappingPreview.arrayBuffer()));
  const candidatesPreview = await workbook.render({
    sheetName: "DAABR Candidates",
    range: `A1:J${Math.min(candidateValues.length + 1, 18)}`,
    scale: 1,
    format: "png",
  });
  await fs.writeFile(CANDIDATES_PREVIEW_PATH, new Uint8Array(await candidatesPreview.arrayBuffer()));
  const researchPreview = await workbook.render({
    sheetName: "Needs Research",
    range: `A1:Q${Math.min(unresolvedRows.length + 1, 14)}`,
    scale: 1,
    format: "png",
  });
  await fs.writeFile(RESEARCH_PREVIEW_PATH, new Uint8Array(await researchPreview.arrayBuffer()));
  const sourcesPreview = await workbook.render({
    sheetName: "Station Sources",
    range: `A1:X${Math.min(sourceValues.length + 1, 14)}`,
    scale: 1,
    format: "png",
  });
  await fs.writeFile(SOURCES_PREVIEW_PATH, new Uint8Array(await sourcesPreview.arrayBuffer()));
  const output = await SpreadsheetFile.exportXlsx(workbook);
  await output.save(OUTPUT_PATH);

  return {
    outputPath: OUTPUT_PATH,
    previewPath: PREVIEW_PATH,
    mappingPreviewPath: MAPPING_PREVIEW_PATH,
    candidatesPreviewPath: CANDIDATES_PREVIEW_PATH,
    researchPreviewPath: RESEARCH_PREVIEW_PATH,
    sourcesPreviewPath: SOURCES_PREVIEW_PATH,
    stationSourcesPath: STATION_SOURCES_PATH,
    ambiguousRows: resolvedRows.length,
    candidateRows: candidateValues.length,
    unresolvedRows: unresolvedRows.length,
    stationSourceCoverage: stationSourceData.coverage,
    confidenceCounts: Object.fromEntries(confidenceCounts),
    providerCounts: Object.fromEntries(providerCounts),
    unresolvedMethodCounts: Object.fromEntries(countBy(
      resolvedRows.filter(({ resolution }) => resolution.ready === "No"),
      ({ resolution }) => resolution.method,
    )),
    duplicateStationCodes: resolvedRows.length - new Set(resolvedRows.map(({ row }) => row.code)).size,
    yatsushiro: mappingValues.find((values) => values[0] === 4320231),
    statusByProvider: Object.fromEntries(
      [...new Set(resolvedRows.map(({ row }) => providerEnglish(row.providerJa)))].map((provider) => [
        provider,
        Object.fromEntries(countBy(
          resolvedRows.filter(({ row }) => providerEnglish(row.providerJa) === provider),
          ({ resolution }) => resolution.confidence,
        )),
      ]),
    ),
    unresolvedStations: resolvedRows
      .filter(({ resolution }) => resolution.confidence === "Unresolved")
      .map(({ row, geocode, resolution }) => ({
        code: row.code,
        nameJa: row.nameJa,
        provider: providerEnglish(row.providerJa),
        localPart: row.localPart,
        candidateCount: row.candidates.length,
        gsiLocality: geocode?.localityJa ?? "",
        method: resolution.method,
      })),
    romanizationMissingStations: resolvedRows
      .filter(({ resolution }) => resolution.confidence === "Romanization missing")
      .map(({ row, resolution }) => ({
        code: row.code,
        nameJa: row.nameJa,
        provider: providerEnglish(row.providerJa),
        localityJa: resolution.selected?.localityJa ?? "",
        localityKana: resolution.selected?.localityKana ?? "",
      })),
    inspection: inspection.ndjson,
    errors: errors.ndjson,
  };
}

async function buildAllStationWorkbook(resolvedRows, geocodes, stationSourceData) {
  const workbook = Workbook.create();
  const summary = workbook.worksheets.add("Summary");
  const mapping = workbook.worksheets.add("All Station Names");
  const unresolved = workbook.worksheets.add("Needs Research");
  const candidates = workbook.worksheets.add("DAABR Candidates");
  const stationSources = workbook.worksheets.add("Station Sources");

  const mappingHeaders = [
    "Station code", "Japanese station name", "Prefecture", "Provider", "Latitude", "Longitude",
    "Municipality code", "Current automatic English", "Automatic-name collision count",
    "Stations in municipality", "Short source label", "DAABR candidate count", "GSI locality",
    "Selected DAABR locality", "Resolved English name", "Resolution method", "Confidence",
    "Ready for app", "Evidence URL", "Notes",
  ];
  const mappingValues = resolvedRows.map(({ row, geocode, resolution }) => [
    Number(row.code),
    row.nameJa,
    row.prefectureJa,
    providerEnglish(row.providerJa),
    row.latitude,
    row.longitude,
    Number(row.municipalityCode),
    row.automaticEnglishName,
    row.automaticCollisionCount,
    row.municipalityStationCount,
    row.localPart,
    row.candidates.length,
    geocode?.localityJa ?? "",
    resolution.selected?.localityJa ?? "",
    resolution.localityEn,
    resolution.method,
    resolution.confidence,
    resolution.ready,
    resolution.evidenceUrl,
    resolution.note,
  ]);
  mapping.getRangeByIndexes(0, 0, 1, mappingHeaders.length).values = [mappingHeaders];
  mapping.getRangeByIndexes(1, 0, mappingValues.length, mappingHeaders.length).values = mappingValues;
  mapping.tables.add(`A1:T${mappingValues.length + 1}`, true, "AllStationNamesTable");
  mapping.freezePanes.freezeRows(1);
  mapping.freezePanes.freezeColumns(2);
  mapping.showGridLines = false;

  const unresolvedRows = mappingValues.filter((values) => values[17] !== "Yes");
  unresolved.getRangeByIndexes(0, 0, 1, mappingHeaders.length).values = [mappingHeaders];
  if (unresolvedRows.length) {
    unresolved.getRangeByIndexes(1, 0, unresolvedRows.length, mappingHeaders.length).values = unresolvedRows;
  }
  unresolved.tables.add(`A1:T${unresolvedRows.length + 1}`, true, "NeedsResearchTableV2");
  unresolved.freezePanes.freezeRows(1);
  unresolved.freezePanes.freezeColumns(2);
  unresolved.showGridLines = false;

  const candidateHeaders = [
    "Station code", "Japanese station name", "Short source label", "Candidate rank", "DAABR locality",
    "DAABR kana", "DAABR official English", "DAABR municipality code", "Machiaza ID", "Selected",
  ];
  const candidateValues = [];
  for (const { row, resolution } of resolvedRows) {
    row.candidates.forEach((candidate, index) => candidateValues.push([
      Number(row.code), row.nameJa, row.localPart, index + 1, candidate.localityJa,
      candidate.localityKana, candidate.localityEn, Number(candidate.lgCode),
      Number(candidate.machiazaId),
      normalize(candidate.localityJa) === normalize(resolution.selected?.localityJa) ? "Yes" : "",
    ]));
  }
  candidates.getRangeByIndexes(0, 0, 1, candidateHeaders.length).values = [candidateHeaders];
  if (candidateValues.length) {
    candidates.getRangeByIndexes(1, 0, candidateValues.length, candidateHeaders.length).values = candidateValues;
  }
  candidates.tables.add(`A1:J${candidateValues.length + 1}`, true, "DaabrCandidatesTableV2");
  candidates.freezePanes.freezeRows(1);
  candidates.freezePanes.freezeColumns(2);
  candidates.showGridLines = false;

  const sourceHeaders = [
    "Station code", "Japanese station name", "Current automatic English", "Resolved English name",
    "Resolution method", "Confidence", "Ready for app", "Prefecture", "Provider",
    "Catalogue latitude", "Catalogue longitude", "Catalogue coordinate text", "Area code", "Area name",
    "Municipality code", "Stations in municipality", "Automatic-name collision count", "GSI locality",
    "Published address (Japanese)", "Facility name (Japanese)", "Facility name (official English)",
    "Metadata status", "Provider station code", "Provider network", "Provider station name (Japanese)",
    "Provider station name (English)", "Provider latitude", "Provider longitude", "Evidence URLs",
    "Resolution evidence URL", "Metadata notes", "Resolution notes",
  ];
  const sourceValues = stationSourceData.stations.map((station) => [
    Number(station.code), station.nameJa, station.automaticEnglishName, station.resolvedEnglishName,
    station.resolutionMethod, station.resolutionConfidence, station.readyForApp, station.prefectureJa,
    station.providerEn, station.catalogueLatitude, station.catalogueLongitude, station.catalogueCoordinateText,
    Number(station.areaCode), station.areaNameJa, Number(station.municipalityCode),
    station.municipalityStationCount, station.automaticCollisionCount, station.gsiLocalityJa ?? "",
    station.publishedAddressJa ?? "", station.facilityNameJa ?? "", station.facilityNameEn ?? "",
    station.metadataStatus, station.providerStationCode ?? "", station.providerStationNetwork ?? "",
    station.providerStationNameJa ?? "", station.providerStationNameEn ?? "", station.providerLatitude,
    station.providerLongitude, station.sourceUrls.join(" | "), station.resolutionEvidenceUrl ?? "",
    station.note, station.resolutionNote ?? "",
  ]);
  stationSources.getRangeByIndexes(0, 0, 1, sourceHeaders.length).values = [sourceHeaders];
  stationSources.getRangeByIndexes(1, 0, sourceValues.length, sourceHeaders.length).values = sourceValues;
  stationSources.tables.add(`A1:AF${sourceValues.length + 1}`, true, "StationSourcesTableV2");
  stationSources.freezePanes.freezeRows(1);
  stationSources.freezePanes.freezeColumns(2);
  stationSources.showGridLines = false;

  const confidenceCounts = countBy(resolvedRows, ({ resolution }) => resolution.confidence);
  const providerCounts = countBy(resolvedRows, ({ row }) => providerEnglish(row.providerJa));
  const duplicateStationCodes = resolvedRows.length - new Set(resolvedRows.map(({ row }) => row.code)).size;
  const finalNameGroups = new Map();
  for (const { row, resolution } of resolvedRows) {
    const key = `${row.municipalityKey}|${resolution.localityEn.toLocaleLowerCase("en")}`;
    if (!finalNameGroups.has(key)) finalNameGroups.set(key, []);
    finalNameGroups.get(key).push(row.code);
  }
  const finalCollisionStations = [...finalNameGroups.values()]
    .filter((codes) => codes.length > 1)
    .reduce((sum, codes) => sum + codes.length, 0);

  summary.getRange("A1:H1").merge();
  summary.getRange("A1").values = [["QuakeDeck complete station-name audit"]];
  summary.getRange("A3:B10").values = [
    ["Metric", "Count"],
    ["Stations audited", resolvedRows.length],
    ["Resolved English names", mappingValues.filter((values) => values[14]).length],
    ["Names still requiring research", unresolvedRows.length],
    ["Final same-municipality name collisions", finalCollisionStations],
    ["Duplicate station codes", duplicateStationCodes],
    ["Stations whose old automatic name collided", resolvedRows.filter(({ row }) => row.automaticCollisionCount > 1).length],
    ["DAABR candidate rows", candidateValues.length],
  ];
  summary.getRange("D3:E3").values = [["Resolution confidence", "Count"]];
  summary.getRangeByIndexes(3, 3, confidenceCounts.length, 2).values = confidenceCounts;
  summary.getRange("G3:H3").values = [["Provider", "Count"]];
  summary.getRangeByIndexes(3, 6, providerCounts.length, 2).values = providerCounts;
  summary.getRange("A21:H25").merge();
  summary.getRange("A21").values = [[
    "Method: every bundled station was evaluated against the app's current automatic English output. " +
    "Same-municipality collisions were then resolved using official JMA/NIED identities, published installation " +
    "addresses, GSI coordinate localities, DAABR official romanization or kana-derived romanization, and targeted " +
    "facility/locality research. A row is marked Ready only when its final name is nonblank and unique in its municipality.",
  ]];
  summary.getRange("A27:H30").merge();
  summary.getRange("A27").values = [[
    "Station Sources contains one provenance record for all 4,360 bundled stations. Published addresses are retained " +
    "where an official source provides them; blank address cells mean no exact sourced address is recorded yet. " +
    "Rounded catalogue coordinates are never presented as street addresses.",
  ]];
  summary.getRange("A32:B40").values = [
    ["Metadata coverage", "Count"],
    ["Bundled stations", stationSourceData.coverage.stations],
    ["Published exact addresses", stationSourceData.coverage.publishedAddresses],
    ["NIED catalogue stations", stationSourceData.coverage.niedCatalogueStations],
    ["NIED provider-coordinate matches", stationSourceData.coverage.providerCoordinateMatches],
    ["NIED without provider-coordinate match", stationSourceData.coverage.niedUnmatched],
    ["Catalogue-only metadata records", stationSourceData.coverage.catalogueOnly],
    ["Resolved English names", stationSourceData.coverage.resolvedEnglishNames],
    ["Needs research", stationSourceData.coverage.needsResearch],
  ];
  summary.getRange("D32:H40").merge();
  summary.getRange("D32").values = [[
    "The final English name is a separate field from address provenance. This keeps a station usable now while allowing " +
    "future station cards to add exact addresses and placement notes as those details are verified.",
  ]];
  summary.showGridLines = false;

  const navy = "#16324F";
  const blue = "#2F75B5";
  const paleBlue = "#DCEAF7";
  const paleGreen = "#E2F0D9";
  const paleRed = "#FCE4D6";
  for (const sheet of [mapping, unresolved, candidates, stationSources]) {
    const used = sheet.getUsedRange();
    used.format.font = { name: "Aptos", size: 10, color: "#222222" };
    used.format.verticalAlignment = "center";
    used.format.borders = { preset: "inside", style: "thin", color: "#D9E2F3" };
    sheet.getRangeByIndexes(0, 0, 1, used.columnCount).format = {
      fill: navy,
      font: { name: "Aptos", size: 10, bold: true, color: "#FFFFFF" },
      verticalAlignment: "center",
      wrapText: true,
      rowHeight: 34,
    };
  }

  mapping.getRange(`A2:A${mappingValues.length + 1}`).format.numberFormat = "0000000";
  mapping.getRange(`E2:F${mappingValues.length + 1}`).format.numberFormat = "0.00000";
  mapping.getRange(`G2:G${mappingValues.length + 1}`).format.numberFormat = "0000000";
  mapping.getRange(`P2:T${mappingValues.length + 1}`).format.wrapText = true;
  mapping.getRange(`R2:R${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Yes", format: { fill: paleGreen, font: { bold: true, color: "#375623" } },
  });
  mapping.getRange(`Q2:Q${mappingValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Researched", format: { fill: paleBlue, font: { color: blue, bold: true } },
  });
  if (unresolvedRows.length) {
    unresolved.getRange(`A2:A${unresolvedRows.length + 1}`).format.numberFormat = "0000000";
    unresolved.getRange(`R2:R${unresolvedRows.length + 1}`).conditionalFormats.add("containsText", {
      text: "No", format: { fill: paleRed, font: { bold: true, color: "#9C0006" } },
    });
  }
  candidates.getRange(`A2:A${candidateValues.length + 1}`).format.numberFormat = "0000000";
  candidates.getRange(`H2:H${candidateValues.length + 1}`).format.numberFormat = "000000";
  candidates.getRange(`I2:I${candidateValues.length + 1}`).format.numberFormat = "0000000";
  candidates.getRange(`J2:J${candidateValues.length + 1}`).conditionalFormats.add("containsText", {
    text: "Yes", format: { fill: paleGreen, font: { bold: true, color: "#375623" } },
  });
  stationSources.getRange(`A2:A${sourceValues.length + 1}`).format.numberFormat = "0000000";
  stationSources.getRange(`J2:K${sourceValues.length + 1}`).format.numberFormat = "0.00000";
  stationSources.getRange(`M2:M${sourceValues.length + 1}`).format.numberFormat = "000";
  stationSources.getRange(`O2:O${sourceValues.length + 1}`).format.numberFormat = "0000000";
  stationSources.getRange(`AA2:AB${sourceValues.length + 1}`).format.numberFormat = "0.00000";
  stationSources.getRange(`S2:V${sourceValues.length + 1}`).format.wrapText = true;
  stationSources.getRange(`AC2:AF${sourceValues.length + 1}`).format.wrapText = true;

  const mappingWidths = [12, 27, 12, 18, 11, 11, 15, 30, 14, 13, 22, 12, 22, 25, 34, 35, 25, 12, 48, 62];
  mappingWidths.forEach((width, index) => {
    mapping.getRangeByIndexes(0, index, mappingValues.length + 1, 1).format.columnWidth = width;
    unresolved.getRangeByIndexes(0, index, Math.max(1, unresolvedRows.length + 1), 1).format.columnWidth = width;
  });
  [12, 27, 22, 12, 26, 24, 30, 18, 14, 12].forEach((width, index) => {
    candidates.getRangeByIndexes(0, index, candidateValues.length + 1, 1).format.columnWidth = width;
  });
  const sourceWidths = [
    12, 27, 30, 34, 35, 25, 12, 12, 18, 11, 11, 22, 10, 22, 15, 13,
    14, 22, 48, 28, 32, 30, 18, 16, 26, 26, 11, 11, 55, 48, 60, 64,
  ];
  sourceWidths.forEach((width, index) => {
    stationSources.getRangeByIndexes(0, index, sourceValues.length + 1, 1).format.columnWidth = width;
  });

  summary.getRange("A1:H1").format = {
    fill: navy,
    font: { name: "Aptos Display", size: 18, bold: true, color: "#FFFFFF" },
    horizontalAlignment: "left",
    verticalAlignment: "center",
    rowHeight: 36,
  };
  for (const headerRange of ["A3:B3", "D3:E3", "G3:H3", "A32:B32"]) {
    summary.getRange(headerRange).format = {
      fill: blue,
      font: { name: "Aptos", size: 10, bold: true, color: "#FFFFFF" },
    };
  }
  summary.getRange("A3:B10").format.borders = { preset: "inside", style: "thin", color: "#D9E2F3" };
  summary.getRange("A21:H25").format = { fill: paleBlue, wrapText: true, verticalAlignment: "top" };
  summary.getRange("A27:H30").format = { fill: "#F2F2F2", wrapText: true, verticalAlignment: "top" };
  summary.getRange("A32:B40").format.borders = { preset: "inside", style: "thin", color: "#D9E2F3" };
  summary.getRange("D32:H40").format = { fill: paleGreen, wrapText: true, verticalAlignment: "top" };
  summary.getRange("A1:H40").format.font = { name: "Aptos", size: 10, color: "#222222" };
  summary.getRange("A1:H1").format.font = { name: "Aptos Display", size: 18, bold: true, color: "#FFFFFF" };
  for (const headerRange of ["A3:B3", "D3:E3", "G3:H3", "A32:B32"]) {
    summary.getRange(headerRange).format.font = { name: "Aptos", size: 10, bold: true, color: "#FFFFFF" };
  }
  [43, 14, 10, 34, 12, 4, 24, 12].forEach((width, index) => {
    summary.getRangeByIndexes(0, index, 40, 1).format.columnWidth = width;
  });

  await fs.mkdir(OUTPUT_DIR, { recursive: true });
  await fs.writeFile(STATION_SOURCES_PATH, `${JSON.stringify(stationSourceData, null, 2)}\n`, "utf8");
  const inspection = await workbook.inspect({
    kind: "table",
    range: "'All Station Names'!A1:T12",
    include: "values,formulas",
    tableMaxRows: 12,
    tableMaxCols: 20,
    maxChars: 16000,
  });
  const errors = await workbook.inspect({
    kind: "match",
    searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
    options: { useRegex: true, maxResults: 100 },
    summary: "final formula error scan",
    maxChars: 3000,
  });
  const preview = await workbook.render({ sheetName: "Summary", range: "A1:H40", scale: 1.5, format: "png" });
  await fs.writeFile(PREVIEW_PATH, new Uint8Array(await preview.arrayBuffer()));
  const mappingPreview = await workbook.render({ sheetName: "All Station Names", range: "A1:T14", scale: 1, format: "png" });
  await fs.writeFile(MAPPING_PREVIEW_PATH, new Uint8Array(await mappingPreview.arrayBuffer()));
  const candidatesPreview = await workbook.render({ sheetName: "DAABR Candidates", range: "A1:J18", scale: 1, format: "png" });
  await fs.writeFile(CANDIDATES_PREVIEW_PATH, new Uint8Array(await candidatesPreview.arrayBuffer()));
  const researchPreview = await workbook.render({ sheetName: "Needs Research", range: unresolvedRows.length ? `A1:T${Math.min(unresolvedRows.length + 1, 14)}` : "A1:T1", scale: 1, format: "png" });
  await fs.writeFile(RESEARCH_PREVIEW_PATH, new Uint8Array(await researchPreview.arrayBuffer()));
  const sourcesPreview = await workbook.render({ sheetName: "Station Sources", range: "A1:AF14", scale: 1, format: "png" });
  await fs.writeFile(SOURCES_PREVIEW_PATH, new Uint8Array(await sourcesPreview.arrayBuffer()));
  const output = await SpreadsheetFile.exportXlsx(workbook);
  await output.save(OUTPUT_PATH);
  await repairExcelContentTypes(OUTPUT_PATH);

  return {
    outputPath: OUTPUT_PATH,
    stationSourcesPath: STATION_SOURCES_PATH,
    stations: resolvedRows.length,
    resolvedEnglishNames: mappingValues.filter((values) => values[14]).length,
    needsResearch: unresolvedRows.length,
    finalCollisionStations,
    duplicateStationCodes,
    candidateRows: candidateValues.length,
    stationSourceCoverage: stationSourceData.coverage,
    confidenceCounts: Object.fromEntries(confidenceCounts),
    providerCounts: Object.fromEntries(providerCounts),
    inspection: inspection.ndjson,
    errors: errors.ndjson,
  };
}

const inputs = await loadInputs();
const allStationRows = buildAllStationRows(inputs);
const ambiguousRows = buildAmbiguousRows(inputs);
if (process.argv.includes("--list-unresolved")) {
  const providerCache = await loadProviderCache();
  const resolvedRows = resolveAllStations(
    allStationRows,
    await loadCache(),
    providerCache.jma?.addresses ?? {},
    providerCache.nied?.items ?? [],
  );
  for (const { row, geocode, resolution } of resolvedRows.filter(
    ({ resolution }) => resolution.ready !== "Yes",
  )) {
    console.log([
      row.code,
      row.nameJa,
      providerEnglish(row.providerJa),
      geocode?.localityJa ?? "",
      resolution.localityEn,
      resolution.confidence,
      row.automaticEnglishName,
      row.automaticCollisionCount,
      row.candidates.map((candidate) => `${candidate.localityJa}=${derivedLocalityEnglish(candidate)}`).join(" | "),
    ].join("\t"));
  }
} else if (process.argv.includes("--list-kana-derived")) {
  const providerCache = await loadProviderCache();
  const resolvedRows = resolveAllStations(
    allStationRows,
    await loadCache(),
    providerCache.jma?.addresses ?? {},
    providerCache.nied?.items ?? [],
  );
  for (const { row, resolution } of resolvedRows.filter(({ resolution }) => (
    /kana-derived/iu.test(resolution.confidence) || /kana-derived/iu.test(resolution.method)
  ))) {
    console.log([
      row.code,
      row.nameJa,
      resolution.localityEn,
      resolution.method,
    ].join("\t"));
  }
} else if (process.argv.includes("--list-facility-labels")) {
  const providerCache = await loadProviderCache();
  const resolvedRows = resolveAllStations(
    allStationRows,
    await loadCache(),
    providerCache.jma?.addresses ?? {},
    providerCache.nied?.items ?? [],
  );
  for (const { row, resolution } of resolvedRows.filter(({ row }) => (
    /(?:役所|役場|庁舎|支所|出張所|消防署|消防本部|学校|大学|公園|グラウンド|体育館|図書館|公民館|センタ|空港|気象台|測候所)$/u.test(row.localPart)
  ))) {
    console.log([row.code, row.nameJa, row.localPart, resolution.localityEn, resolution.method].join("\t"));
  }
} else if (process.argv.includes("--list-qualified")) {
  const providerCache = await loadProviderCache();
  const resolvedRows = resolveAllStations(
    allStationRows,
    await loadCache(),
    providerCache.jma?.addresses ?? {},
    providerCache.nied?.items ?? [],
  );
  for (const { row, geocode, resolution } of resolvedRows.filter(({ resolution }) => (
    /^(?:JMA|NIED|K-NET|KiK-net)\b/u.test(resolution.localityEn)
  ))) {
    console.log([
      row.code, row.nameJa, row.localPart, geocode?.localityJa ?? "",
      resolution.localityEn, resolution.method, resolution.note,
    ].join("\t"));
  }
} else if (process.argv.includes("--fetch-unresolved")) {
  const providerCache = await loadProviderCache();
  const jmaAddresses = providerCache.jma?.addresses ?? {};
  const niedStations = providerCache.nied?.items ?? [];
  const existingGeocodes = await loadCache();
  const initial = resolveAllStations(allStationRows, existingGeocodes, jmaAddresses, niedStations);
  const targets = initial
    .filter(({ resolution }) => resolution.ready !== "Yes")
    .map(({ row }) => row);
  const geocodes = await reverseGeocode(targets, niedStations);
  const after = resolveAllStations(allStationRows, geocodes, jmaAddresses, niedStations);
  console.log(JSON.stringify({
    fetchedTargets: targets.length,
    ready: after.filter(({ resolution }) => resolution.ready === "Yes").length,
    needsResearch: after.filter(({ resolution }) => resolution.ready !== "Yes").length,
  }, null, 2));
} else if (process.argv.includes("--analyze-all")) {
  const providerCache = await loadProviderCache();
  const jmaAddresses = providerCache.jma?.addresses ?? {};
  const niedStations = providerCache.nied?.items ?? [];
  const geocodes = await loadCache();
  const resolvedRows = resolveAllStations(allStationRows, geocodes, jmaAddresses, niedStations);
  const needsResearch = resolvedRows.filter(({ resolution }) => resolution.ready !== "Yes");
  const nameConventionAudit = validateStationNameConventions(resolvedRows);
  const nameQualityAudit = validateStationNameQuality(resolvedRows);
  console.log(JSON.stringify({
    stations: allStationRows.length,
    municipalityCounts: {
      singleStation: allStationRows.filter((row) => row.municipalityStationCount === 1).length,
      multipleStations: allStationRows.filter((row) => row.municipalityStationCount > 1).length,
    },
    automaticNames: {
      blank: allStationRows.filter((row) => !row.automaticEnglishName).length,
      collidingStations: allStationRows.filter((row) => row.automaticCollisionCount > 1).length,
    },
    candidateCounts: Object.fromEntries(countBy(allStationRows, (row) => row.candidates.length)),
    resolutionCounts: Object.fromEntries(countBy(resolvedRows, ({ resolution }) => resolution.confidence)),
    nameConventionAudit,
    nameQualityAudit,
    ready: resolvedRows.length - needsResearch.length,
    needsResearch: needsResearch.length,
    needsResearchMethods: Object.fromEntries(countBy(needsResearch, ({ resolution }) => resolution.method)),
    needsResearchStations: needsResearch.slice(0, 250).map(({ row, geocode, resolution }) => ({
      code: row.code,
      nameJa: row.nameJa,
      provider: providerEnglish(row.providerJa),
      municipalityStationCount: row.municipalityStationCount,
      automaticEnglishName: row.automaticEnglishName,
      localPart: row.localPart,
      candidates: row.candidates.map((candidate) => `${candidate.localityJa}|${candidate.localityEn}`),
      gsiLocality: geocode?.localityJa ?? "",
      method: resolution.method,
      confidence: resolution.confidence,
      proposedEnglishName: resolution.localityEn,
    })),
  }, null, 2));
} else if (process.argv.includes("--analyze")) {
  console.log(JSON.stringify({
    stations: inputs.stations.length,
    ambiguousRows: ambiguousRows.length,
    providerCounts: Object.fromEntries(countBy(ambiguousRows, (row) => providerEnglish(row.providerJa))),
    samples: ambiguousRows.slice(0, 10).map((row) => ({
      code: row.code,
      nameJa: row.nameJa,
      localPart: row.localPart,
      candidates: row.candidates.map((candidate) => candidate.localityJa),
    })),
  }, null, 2));
} else {
  const providerCache = await loadProviderCache();
  const offline = process.argv.includes("--offline");
  const jmaAddresses = offline
    ? providerCache.jma?.addresses ?? {}
    : await fetchJmaAddresses(providerCache);
  const niedStations = offline
    ? providerCache.nied?.items ?? []
    : await fetchNiedStations(providerCache);
  let geocodes = await loadCache();
  if (!offline) geocodes = await reverseGeocode(allStationRows, niedStations);
  const resolvedRows = resolveAllStations(allStationRows, geocodes, jmaAddresses, niedStations);
  const nameConventionAudit = validateStationNameConventions(resolvedRows);
  const nameQualityAudit = validateStationNameQuality(resolvedRows);
  const stationSourceData = buildStationSourceData(
    inputs,
    providerCache,
    jmaAddresses,
    niedStations,
    resolvedRows,
    geocodes,
  );
  console.log(JSON.stringify({
    ...await buildAllStationWorkbook(resolvedRows, geocodes, stationSourceData),
    nameConventionAudit,
    nameQualityAudit,
  }, null, 2));
  process.exit(0);
}
