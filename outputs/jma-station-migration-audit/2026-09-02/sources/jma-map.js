//----------------------------------------------------------------------------//
$( function() {

	InitMap();

	DispMap();
});
//----------------------------------------------------------------------------//
function zoomMap(flg){

	if(flg == 0){
		map.flyTo([ 35, 137], 5);
	}else if(flg == 1){
		map.flyTo([ 43.5, 142.5], 7);
	}else if(flg == 2){
		map.flyTo([ 40.1, 141], 8);
	}else if(flg == 3){
		map.flyTo([ 38, 139.7], 8);
	}else if(flg == 4){
		map.flyTo([ 35.9, 139.6], 8);
	}else if(flg == 5){
		map.flyTo([ 34, 139.5], 8);
	}else if(flg == 6){
		map.flyTo([ 29.5, 141.5], 7);
	}else if(flg == 7){
		map.flyTo([ 36.7, 137.5], 8);
	}else if(flg == 8){
		map.flyTo([ 35, 137.5], 8);
	}else if(flg == 9){
		map.flyTo([ 34.5, 135.5], 8);
	}else if(flg == 10){
		map.flyTo([ 34.5, 133.3], 8);
	}else if(flg == 11){
		map.flyTo([ 32.7, 130.5], 8);
	}else if(flg == 12){
		map.flyTo([ 28.5, 129], 7);
	}else if(flg == 13){
		map.flyTo([ 25.5, 125.5], 7);
	}else if(flg == 14){
		map.flyTo([ 35.69, 139.76], 9);
	}else if(flg == 15){
		map.flyTo([ 35.17, 136.97], 9);
	}else if(flg == 16){
		map.flyTo([ 34.68, 135.52], 9);
	}
}
//----------------------------------------------------------------------------//
function DispMap(){

	var prefPrm = {"1": "北海道", "2": "青森県", "3": "岩手県", "4": "宮城県", "5": "秋田県", "6": "山形県", "7": "福島県",
		"8": "茨城県", "9": "栃木県", "10": "群馬県", "11": "埼玉県", "12": "千葉県", "13": "東京都", "14": "神奈川県",
		"15": "新潟県", "16": "富山県", "17": "石川県", "18": "福井県", "19": "山梨県", "20": "長野県", "21": "岐阜県",
		"22": "静岡県", "23": "愛知県", "24": "三重県", "25": "滋賀県", "26": "京都府", "27": "大阪府", "28": "兵庫県",
		"29": "奈良県", "30": "和歌山県", "31": "鳥取県", "32": "島根県", "33": "岡山県", "34": "広島県", "35": "山口県",
		"36": "徳島県", "37": "香川県", "38": "愛媛県", "39": "高知県", "40": "福岡県", "41": "佐賀県", "42": "長崎県",
		"43": "熊本県", "44": "大分県", "45": "宮崎県", "46": "鹿児島県", "47": "沖縄県"};

	$.getJSON('stations.json', function(data) {

		var cnt = [];
		for(var i = 0; i < data.length; i++){
			var name  = data[i].name;
			var affi  = data[i].affi;
			var pref = data[i].pref;
			var lat = data[i].lat;
			var lon = data[i].lon;

			//--　観測点マーカ
			var ColorStr = "rgb(255,040,000)";
			var Syozoku = "気象庁";
			 if(affi == 1){
				Syozoku = "地方公共団体";
				ColorStr = "rgb(000,128,000)";
			}else if(affi == 2){
				Syozoku = "防災科学技術研究所";
				ColorStr = "rgb(000,065,255)";
			}
			var TextStr = Syozoku + " " + name;

			var point = L.circleMarker([lat, lon],{
				radius: 4,
				weight: 1,
				color: '#000000',
				fillColor: ColorStr,
				fillOpacity: 0.8
			}).addTo(map).bindTooltip(TextStr);

			if(typeof cnt[pref] === "undefined")	cnt[pref] = [];
			if(typeof cnt[pref][affi] === "undefined")	cnt[pref][affi] = 0
			cnt[pref][affi] ++;
		}

		//--　震度観測点の数
		var jNum = 0;	var nNum = 0;	var pNum = 0;
		for(var p in prefPrm){
			var total = cnt[p][0] + cnt[p][1] + cnt[p][2];
			var line = '<tr><td>' + prefPrm[p] + '</td><td>' + cnt[p][0] + '</td><td>' + cnt[p][1] + '</td><td>' + cnt[p][2] + '</td><td>' + total + '</td></tr>';
			$('#stationNum').append(line);
			jNum += cnt[p][0];	nNum += cnt[p][1];	pNum += cnt[p][2];
		}
		var total = jNum + nNum + pNum;
		var line = '<tr><td>全国合計</td><td>' + jNum + '</td><td>' + nNum + '</td><td>' + pNum + '</td><td>' + total + '</td></tr>';
		$('#stationNum').append(line);

	});
	return;
}
//----------------------------------------------------------------------------//
var map;

function InitMap() {
	if(map != null){	//　既存のmap削除
		map.remove();	map = null;
	}
	var attrG = '<a href="https://maps.gsi.go.jp/development/ichiran.html" target="_blank">Geospatial Information Authority of Japan</a>';
	var attrJ = '© Japan Meteorological Agency 2018';
	var std = L.tileLayer('https://cyberjapandata.gsi.go.jp/xyz/std/{z}/{x}/{y}.png',  {maxZoom: 11, minZoom: 4, id: 'stdmap'  , attribution: attrG});
	var pal = L.tileLayer('https://cyberjapandata.gsi.go.jp/xyz/pale/{z}/{x}/{y}.png', {maxZoom: 11, minZoom: 4, id: 'palemap' , attribution: attrG});
	var eng = L.tileLayer('https://cyberjapandata.gsi.go.jp/xyz/english/{z}/{x}/{y}.png',{maxZoom: 11, minZoom: 4, id: 'english', attribution: attrG});
	var blk = L.tileLayer('https://cyberjapandata.gsi.go.jp/xyz/blank/{z}/{x}/{y}.png',{maxZoom: 11, minZoom: 6, id: 'blankmap', attribution: attrG});
	var org = L.tileLayer('https://www.data.jma.go.jp/svd/eqdb/data/shindo/map/{z}/{x}/{y}.png',{maxZoom: 11, minZoom: 4, id: 'original', attribution: attrJ});
	var restrictedExtent = [ [15.0, 120.0], [50.0, 160.0] ];
	map = L.map('map', { layers: [org], maxBounds: restrictedExtent, zoomControl: false, doubleClickZoom: false });
	var baseMaps = {
		'地理院地図' : std,
		'淡色地図' : pal,
		'English' : eng,
		'白地図'   : blk,
		'オリジナル' : org
	};
	L.control.layers(baseMaps, {}, { position: 'bottomright' }).addTo(map);
	L.control.zoom({ position: 'bottomright' }).addTo(map);
	L.control.scale({ maxWidth: 150, imperial: false }).addTo(map);
	map.setView([35, 137], 5);	//　初期描画位置
	L.latlngGraticule({	showLabel: true	}).addTo(map);
	return;
};
//----------------------------------------------------------------------------//