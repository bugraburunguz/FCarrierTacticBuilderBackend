# 07 · LIVE EDITOR (FC 27 LE v27.1.0) — ne alınabilir, export v2, backend düzeltmeleri
Kaynak: Buğra'nın yüklediği `FC 27 LE v27.1.0.zip` (yalnız metin dosyaları okundu, hiçbir exe/dll çalıştırılmadı), xAranaktu GitHub repo/wiki'leri (FC-24/25/26/27), repodaki Lua scriptleri, Buğra'nın 5 CSV'si.
`LE/` = zip kökü · `BE/` = backend · `WEB/` = web.

## 0. ÖZET
- Oyun veritabanını okumanın desteklenen yolu `LE.db:GetTable(name)` + kayıt gezme (Lua API v2). Diğer her şey ya eski v1 fonksiyonu ("yakında çalışmayabilir") ya da `TODO: FC27` stub'ı.
- **Form, moral, keskinlik, kondisyon, sakatlık okunamıyor.** LE yalnız set ediyor, Lua'da getter yok. **Piyasa değeri DB'de yok** (bellekte hesaplanıyor, Lua'ya açılmamış).
- **Growth profile ve dynamic potential** LE'nin hiçbir metninde geçmiyor → tek seferlik şema taraması (§4 PROBE) gerekli.
- Mevcut export scripti temelde doğru. Eksikleri: kariyer modu kontrolü, save/oyun tarihi/kullanıcı takımı bağlamı, satır sayısı doğrulaması, çok kolon eksik.
- Backend'de iki gerçek hata: Türkçe çıkan lig/kulüp **adlarıyla** eşleme ve CSV yolunda loan/isim tablolarını sessizce atma.
- LE v2'de HTTP istemcisi var → **tek tık senkron** mümkün görünüyor (27.1.0'da çalıştığı `VERIFY`).

---
## 1. LUA API KATALOĞU (v27.1.0)
`changelog.txt`: "ilk çalışan sürüm": temel oyuncu editörü (yalnız menajer kariyeri), temel takım/menajer editörü, genç scout raporu üretme, legacy dosya tarayıcısı, Lua motoru, menüde speedhack. FC26 sürümü 26.1.x boyunca sezon istatistikleri, puan durumu, fikstür, transfer geçmişi ve `LegacyFileExist/Export` ekledi → 27.x'te benzerleri beklenir. Lua sürümü 5.3+ (kütüphaneler `&`, `>>`, `<<` kullanıyor).

### 1.1 v2 (kullan) — `main.lua` → `LE`
| Nesne / fonksiyon | Ne yapar | R/W |
|---|---|---|
| `LE.db:GetTable(name)` | Tablo nesnesi (tüm yüklü DB'lerde ilk eşleşme) | R |
| `LE.db:Load()` | `LE.db.tables[name]` tümünü doldurur → **tüm tablo adlarını listelemenin doğrulanmış yolu** | R |
| tablo: `name, shortname, fields, first_record, record_size, written_records, last_record_idx` | | |
| tablo: `IsRecordValid(rec)`, `GetFirstRecord`, `GetNextValidRecord`, `GetRecordFieldValue(rec, name)`, `SetRecordFieldValue` | | R / **W** |
| alan: `type` (0 string, 3 int, 4 float; diğerleri nil, örn. 13 = sıkıştırılmış `playernames.name`), `offset`, `startbit`, `fld_desc{name, depth, min}` | | |
| `IsInCM()` | Kariyer modunda mı (v1 ama v2 `helpers.lua:47` çağırıyor → var) | R |
| `GetUserTeamID()`, `GetUserNationalTeamID()` (`career_mode/helpers.lua:46-68`, bellek okuması) | Kullanıcı takımı | R |
| `GetCurrentDate()` (`other/helpers.lua`) | DATE (kariyer dışında 1 Tem 2025 döner) | R |
| `FCECareerModeUserManager`: `IsManagerCareer`, `IsPlayerCareer` | Kariyer tipi | R |
| `DATE:FromGregorianDays` / `ToInt` | `birthdate`, `playerjointeamdate` çözümü | — |
| `GetPlayerIDSForTeam(teamid)` | `teamplayerlinks` üzerinden | R |
| `HTTP:send(REQUEST)` → `SendHTTPRequest(method,url,body,auth,payload,headers,cookies,timeout)`; yanıt `status_code,text,header,error,elapsed` | HTTP istemcisi (libcpr) + `imports/external/json` (rxi) | — |
| `PlayerDevelopmentManager`, `GameLocalizationManager`, `TransferBanManager`, olay işleyicileri (`AddEventHandler("post__CareerModeEvent", fn)`) | | W/R |
| **Stub (FC27'de boş):** `GetUserSeniorTeamPlayerIDs` ({} döner), `UserTeamSetPlayersForm/Sharpness/Morale/Fitness`, `SetSquadRole`, `players_manager.GetPlayerName` | | — |

### 1.2 v1 (`LE/lua/DOC.MD`, "v2 kullan, yakında çalışmayabilir")
`Log`, `MessageBox`, `IsInCM`, **`GetSaveUID` (ilk çağrıda save'e YAZAR: `sponsors` tablosunda `adsponserid==8181` satırı)**, `GetCompetitionNameByID`, `GetCurrentDate`, `GetUserTeamID`, `GetTeamIdFromPlayerId` (lig 78/2136/76/383'ü atlar), `GetTeamName`, `GetPlayerName` (FC27'de boş), `SetPlayerSharpness/Morale/Form/Fitness` (getter yok), `PlayerHasDevelopementPlan`, **`GetPlayersStats()`** (teamid, playerid, compobjid, avg÷app, goals, assists, yellow, red, app, clean_sheets…; yalnız kullanıcının oynadığı turnuvalar), transfer/kiralık/serbest bırakma yazıcıları, `IsPlayerLoanedOut/Presigned/TransferListed`, `GetDBTablesNames/Fields/Rows`, `PlayerCapture*` (yüz görseli üretir, ~2 sn/yüz, PNG ya da DDS), `Get/SetTransferBudget` (deprecation stub → `GetUserTransferBudget` adları imzasız).

### 1.3 Bilinen LE hataları (script yazarken etrafından dolaş)
- `table.lua:82` `written_records = ReadShort(...)` **işaretli 16 bit** → 32.767 üstü tablo negatif çıkar, `GetNextValidRecord` hemen durur (`playernames` ~43k). Çözüm: `n<0 → n+65536` ve indeksle gez.
- `field.lua:100` `GetString` uzunluksuz `ReadString` → slotu dolduran string sonraki alana taşar. Çözüm: `MEMORY:ReadString(rec+offset, depth/8)` ve `\0`'da kes.

---
## 2. NE ALINABİLİR — veri setleri
FC27 kolon ipuçları `loc/eng_us/localize.json`'dan (editör etiketleri, şema değil). Görsel yolları `legacy_filename_hash_list.csv`'den (119.003 benzersiz yol; diskteki dosya listesi değil, ad↔hash eşlemesi).

| Veri seti | LE kaynağı | Alanlar | Uygulama değeri | Efor |
|---|---|---|---|---|
| Tam oyuncu tablosu | `players` (tüm kolonlar) | 34 attribute, `preferredposition1..7`, `role1..5` (1-52, +100 = ++), `trait1/2` + `icontrait1/2` (PlayStyle bitmask: trait1 bit 0-29 = 30 saha PS, trait2 bit 0-5 = GK PS, bit 8-13 gizli kariyer özellikleri: Injury Prone, Leadership, Super Sub…), `skillmoves`, `weakfootabilitytypecode`, `preferredfoot`, `height`, `weight`, `birthdate`, `contractvaliduntil`, `playerjointeamdate`, work rate'ler, `internationalrep`, `isretiring`, `personality`, `headassetid`, `hashighqualityhead`, `isregen` | Çok yüksek | S (`"*"`'a geç) |
| İsimler | `playernames` (tip 13, mevcut Huffman çözücü), `editedplayernames`, `dcplayernames` | `nameid`, `name` | Yüksek | Yapılmış |
| Kadro bağları | `teamplayerlinks` tüm kolonlar | `jerseynumber`, `position`, `artificialkey` (+ eski oyunlarda form/sakatlık/gol, `VERIFY`) | Yüksek | S |
| Takımlar | `teams` tüm kolonlar | rating'ler, prestij, `clubworth`, `youthdevelopment`, rakip, kaptan/duran top atıcıları, stadyum, taktik | Yüksek | S |
| Ligler, bağlar, ülkeler | `leagues` (+`leaguelevel`), `leagueteamlinks`, `nations` (`VERIFY` ad) | id, ad, uluslararası bayrağı | Orta (BE-BUG-17 tier) | S |
| Kontrat ve maaş | `career_playercontract` (`VERIFY` kolonlar: `wage`, `playerrole`, `loan_wage_split`, `performancebonus*`), `players.contractvaliduntil` | Maaş, bitiş, kadro rolü, bonus | Yüksek (bitiş uyarısı, maaş bütçesi) | S-M |
| Kiralık ve ön-kontrat | `playerloans`, `career_presignedcontract` | Kiralayan takım, bitiş | Yüksek | S |
| Genç akademi | `career_youthplayers` (export ediliyor ama **backend hiç okumuyor**), id ≥ 460000 oyuncular | Gerçek potansiyel (LE "reveal" seçeneği değerin saklandığını gösteriyor) | Yüksek | M |
| Sezon istatistikleri | `GetPlayersStats()` (v1, probe) | Maç, gol, asist, ort. puan, clean sheet | Yüksek | M |
| Kariyer bağlamı | `IsInCM`, `career_users.clubteamid`, `GetCurrentDate`/`career_calendar`, kariyer tipi, `sponsors` satırı 8181'deki mevcut save id (yalnız **oku**) | | Çok yüksek: otomatik kadro, snapshot sırası | S |
| Transfer bütçesi | `GetUserTransferBudget` (probe) | | Orta | S |
| Growth profile / dynamic potential / dynamic OVR | **Bilinmiyor** → PROBE | | Çok yüksek | Probe S, sonra M |
| Form, moral, keskinlik, kondisyon, sakatlık, piyasa değeri | Bellek yöneticileri, Lua getter'ı yok | | Yüksek ama mümkün değil | Sonraki LE sürümünü bekle |
| Görseller | `LegacyFileExport` (FC26'da var, 27'de probe) / dosya tarayıcısı, `PlayerCapture*` | DDS → WebP | Yüksek UX | M + hukuki (§5) |

Görsel yolları: `data/ui/imgAssets/heads/p{playerid}.dds` (40.104), `youthheads/p{id}.dds` (11.460), `crest/{light,dark}/l{teamid}.dds`, `league/{light,dark}/l{leagueid}.dds`, `flags512x512/{light,dark}/f_{nationid}.dds`, `heads_staff/heads_staff_{id}.dds`, `kits/j{type}_{teamid}_{n}.dds`. Mbappé 231747 ve Haaland 239085'in `p` başları listede var. Format DDS BC3/DXT5 180×180.

---
## 3. MEVCUT SCRIPT İNCELEMESİ
`WEB/public/fc27_career_export.lua` = `BE/tools/live-editor/fc27_career_export.lua` = `fc27_base_export.lua` (birebir 3 kopya) → **tek kaynak**: backend `tools/live-editor/`, web build'de kopyalasın.
**Doğru olanlar:** v2 `GetTable`, büyük tablo sayaç düzeltmesi (52-56), indeksle gezip geçerlilik kontrolü (84-97), tablo başına `pcall`, CSV tırnaklama, yalnız var olan API.
**Sorunlar:**
1. Kariyer modu kontrolü yok; ana menüde çalışınca base kadroları export eder, backend bunu kariyer verisi sanar.
2. Bağlam yok: save id, oyun tarihi, kariyer tipi, kullanıcı takımı → backend takımı adla tahmin ediyor (`CareerImportService.java:152-155`), snapshot sıralanamıyor.
3. Uzunluksuz string okuma (§1.3).
4. Eksik kolonlar: `role1-5`, `contractvaliduntil`, `playerjointeamdate`, work rate'ler, `modifier`, `isretiring`, `skillmoveslikelihood`, `personality`, `headassetid`. `teamplayerlinks` yalnız `playerid,teamid`. `career_users`, `career_calendar`, `career_playercontract`, `manager` hiç yok.
5. Bütünlük yok: şema versiyonu, tablo checksum'ı yok; `END` sayısı alınan satırla karşılaştırılmıyor.
6. `fc27_dump.lua` büyük tablolarda kırık (`GetNextValidRecord`), `io` yolu hiç çalışmıyor → **emekliye ayır**.
7. **`GetSaveUID` asla çağrılmamalı** (save'e yazar). Bugün çağrılmıyor, öyle kalsın.
8. Son kullanıcı metinleri geliştiriciye yazılmış ("Bana 'bitti' yaz") ve ImportPage'deki pencere adıyla uyuşmuyor (WEB-BUG-25).

### 3.1 Backend/web hataları (LE kaynaklı)
| ID | Pri | Yer | Sorun | Düzeltme |
|---|---|---|---|---|
| LE-01 | P0 | `BE/.../BaseCatalogImporter.java:45, 705-709` | `NON_CATALOG_LEAGUES` İngilizce ad ("Rest of World Men"), export Türkçe ("Erkekler - Diğer") → o kulüpler dışlanmıyor. Kulüp ad eşlemesi; milli takımlar "İngiltere", serbestler "Serbest" | **ID ile** eşle: lig 76 (Diğer), 78 (Erkek Milli), 2136 (Kadın Milli), 382 (Serbest), 2000/2028 (yer tutucu); takım 111592/131368 (serbest). Kulübe EA team id yaz. `BEHAVIOR CHANGE` |
| LE-02 | P0 | `BE/.../BaseUploads.java:19`, `WEB/pages/ImportPage.tsx` | CSV yolu yalnız 5 tablo adı kabul ediyor → `playerloans`, isim tabloları, gençler kayboluyor. 3 kolonlu `players.csv`'de pozisyon yok → 10 kariyer-üretimi oyuncu atlanıyor, `career_player_rating` boş. UI uyarmıyor | `KEYS`'e ekle; `players.csv`'de attribute/pozisyon kolonu yoksa UI uyarısı; `ctx.inCareer=false` ise "kariyer algılanmadı" |
| LE-03 | P1 | `CareerImportService.java:146-150` | `dynamic_potential = potential` → veteranlar şişik (Ronaldo 84/94, Messi 89/94) | `birthdate` export edilince yaşa göre tavan (örn. 31+ yaşta POT ≤ OVR+1); `VERIFY` FC27 dynamic potential kuralı |
| LE-04 | P1 | `DumpMappings.java:23-26` | 28 pozisyon kodundan yalnız 12'si eşleniyor | Tam tablo (0 GK, 1 SW, 2 RWB, 3 RB, 4 RCB, 5 CB, 6 LCB, 7 LB, 8 LWB, 9 RDM, 10 CDM, 11 LDM, 12 RM, 13 RCM, 14 CM, 15 LCM, 16 LM, 17 RAM, 18 CAM, 19 LAM, 20 RF, 21 CF, 22 LF, 23 RW, 24 RS, 25 ST, 26 LS, 27 LW) → 12 UT pozisyonuna indirgeme (RWB→RB, R/LCB→CB, R/L/CDM→CDM, R/L/CM→CM, R/L/CAM→CAM, RF/CF/LF/RS/LS→ST) |
| LE-05 | P2 | importer | `trait2` bit 8-13 gizli özellikler (Injury Prone vb.) okunmuyor | `players.hidden_traits TEXT[]` |
| LE-06 | P1 | `CareerImportService.java:105-106` | Her import öncekini siliyor → CAR-02 snapshot modeliyle çelişiyor | Import = yeni snapshot (`exportId`, `gameDate`); kadro yalnız `userTeamId`'den türer (BE-BUG-13) |

---
## 4. EXPORT v2 — şema ve script
### 4.1 Doküman şeması (log satırlarından ya da HTTP'den kurulur)
```json
{"schema":"fccareer.le-export","schemaVersion":2,"exportId":"<uuid>",
 "producer":{"script":"fc27_career_export","version":"2.0.0","leVersion":"27.1.0","gameModule":"..."},
 "context":{"inCareer":true,"mode":"MANAGER","gameDate":"2027-01-14","userTeamId":121,"userTeamIdMem":121,"userNationalTeamId":0,"saveUid":null},
 "tables":{"players":{"columns":["playerid","..."],"rowCount":21620,"checksum":"fnv1a32:...","rows":[[20801,84,"..."]]}},
 "derived":{"player_season_stats":{"columns":["..."],"rows":[]}},
 "warnings":["MISSINGTABLE nations"]}
```
Log satır biçimi (sekme ayraçlı, `FCX2` öneki): `META <json>` · `HEADER <table> <json cols>` · `CHUNK <table> <seq> <json rows>` · `END <table> <rowCount> <checksum> <chunks>` · `ERROR <table> <msg>` · `DONE <json warnings>`. Satır başına ≤16 KB.

### 4.2 Lua script (yalnız doğrulanmış API + `type()` kontrollü probe'lar)
```lua
--- fc27_career_export v2 — SALT OKUR. LE v27.1.0 Lua API v2. Çıktı: Logs/live_editor_<tarih>.log "FCX2" satırları.
require 'imports/career_mode/helpers'
require 'imports/other/helpers'
local UserMgr = require 'imports/career_mode/FCECareerModeUserManager'
local SYNC_URL, SYNC_TOKEN = nil, nil          -- site kişisel script üretirken doldurur (opsiyonel, §6)
local PROBE = false                            -- true = yalnız tüm tabloların şemasını dök
local MARK, VER, SCHEMA_V, MAX_CHUNK = "FCX2", "2.0.0", 2, 16000

local function safe(f, ...) if type(f) ~= "function" then return nil end local ok, v = pcall(f, ...) if ok then return v end end
local function nrec(t) local n = t.written_records if n < 0 then n = n + 65536 end return n end
local function each(t, fn) local b, s = t.first_record, t.record_size
  for i = 0, nrec(t) - 1 do local r = b + s * i if t:IsRecordValid(r) then fn(r) end end end
local function fnv(h, s) for i = 1, #s do h = ((h ~ s:byte(i)) * 16777619) & 0xFFFFFFFF end return h end
local function jstr(s) return '"' .. (s:gsub('[%c"\\]', function(c)
  if c == '"' then return '\\"' elseif c == '\\' then return '\\\\' end return string.format('\\u%04x', c:byte()) end)) .. '"' end
local function jval(v) local t = type(v)
  if t == "number" then if math.type(v) == "integer" then return tostring(v) end
    if v ~= v or v == math.huge or v == -math.huge then return "null" end return string.format("%.6g", v)
  elseif t == "string" then return jstr(v) end return "null" end
local function reader(f)
  if f.type == 0 then local len = math.floor(f.fld_desc.depth / 8)
    return function(r) local s = MEMORY:ReadString(r + f.offset, len) return s and s:match("^([^\0]*)") end
  elseif f.type == 3 or f.type == 4 then return function(r) return f:GetValue(r) end end
  return function() return nil end end

local warnings = {}
local function emit(...) Log(table.concat({ MARK, ... }, "\t")) end
local http_on = SYNC_URL and SYNC_TOKEN and type(SendHTTPRequest) == "function" and HTTP and REQUEST
local function push(kind, body)
  if not http_on then return end
  local req = REQUEST:new({ method = HTTP_POST_REQUEST, url = SYNC_URL .. "/" .. kind, timeout = 15000 })
  req:SetHeaders({ ["Content-Type"] = "application/json" }); req:SetBody(body)
  req:SetAuth({ access_token = SYNC_TOKEN, auth_mode = HTTP_AUTH_METHOD_BEARER })
  local r = safe(HTTP.send, HTTP, req)
  if not r or r.status_code ~= 200 then http_on = false; warnings[#warnings + 1] = "HTTP_FALLBACK_LOG" end end

if PROBE then
  LE.db:Load()
  for name, t in pairs(LE.db.tables) do
    local f = {} for fname, fld in pairs(t.fields or {}) do
      f[#f + 1] = string.format('{"n":%s,"t":%d,"d":%d,"m":%d}', jstr(fname), fld.type, fld.fld_desc.depth or 0, fld.fld_desc.min or 0) end
    emit("SCHEMA", name, tostring(nrec(t)), "[" .. table.concat(f, ",") .. "]") end
  MessageBox("FC27 şema taraması", "Bitti. Log dosyasını siteye yükle.") return end

local in_cm = safe(IsInCM) == true
local ctx = { inCareer = in_cm, leVersion = LE_VERSION, gameModule = LE.game_name }
if in_cm then
  local d = safe(GetCurrentDate) if d and d.year then ctx.gameDate = string.format("%04d-%02d-%02d", d.year, d.month, d.day) end
  ctx.userTeamIdMem = safe(GetUserTeamID); ctx.userNationalTeamId = safe(GetUserNationalTeamID)
  local um = UserMgr:new()
  ctx.mode = safe(um.IsManagerCareer, um) and "MANAGER" or (safe(um.IsPlayerCareer, um) and "PLAYER" or "UNKNOWN")
  local cu = LE.db:GetTable("career_users")
  if cu and cu.fields and cu.fields.clubteamid then each(cu, function(r) ctx.userTeamId = ctx.userTeamId or cu:GetRecordFieldValue(r, "clubteamid") end) end
  local sp = LE.db:GetTable("sponsors")       -- yalnız OKU; GetSaveUID çağırma (save'e yazar)
  if sp and sp.fields and sp.fields.adsponserid and sp.fields.name then local rd = reader(sp.fields.name)
    each(sp, function(r) if sp:GetRecordFieldValue(r, "adsponserid") == 8181 then ctx.saveUid = rd(r) end end) end
else ctx.mode = "BASE" end

local TABLES = { {"players",1}, {"teams",1}, {"teamplayerlinks",1}, {"leagues"}, {"leagueteamlinks"}, {"nations"},
  {"editedplayernames"}, {"dcplayernames"}, {"manager"}, {"formations"}, {"playerloans",0,1}, {"career_users",0,1},
  {"career_calendar",0,1}, {"career_playercontract",0,1}, {"career_presignedcontract",0,1}, {"career_youthplayers",0,1} }

local meta = {} for k, v in pairs(ctx) do meta[#meta + 1] = jstr(k) .. ":" .. jval(v) end
local metaJson = string.format('{"schema":"fccareer.le-export","schemaVersion":%d,"script":"%s","context":{%s}}', SCHEMA_V, VER, table.concat(meta, ","))
emit("META", metaJson); push("meta", metaJson)

local summary = {}
for _, spec in ipairs(TABLES) do
  local name, required, cmOnly = spec[1], spec[2] == 1, spec[3] == 1
  if cmOnly and not in_cm then goto continue end
  local ok, err = pcall(function()
    local t = LE.db:GetTable(name)
    if not t or not t.fields then warnings[#warnings + 1] = "MISSINGTABLE " .. name
      if required then error("zorunlu tablo yok") end return end
    local cols, rd = {}, {} for fname in pairs(t.fields) do cols[#cols + 1] = fname end table.sort(cols)
    for i, c in ipairs(cols) do rd[i] = reader(t.fields[c]) end
    local hdr = {} for i, c in ipairs(cols) do hdr[i] = jstr(c) end
    emit("HEADER", name, "[" .. table.concat(hdr, ",") .. "]")
    local buf, blen, seq, rows, h = {}, 0, 0, 0, 2166136261
    local function flush() if #buf == 0 then return end seq = seq + 1
      local body = "[" .. table.concat(buf, ",") .. "]" emit("CHUNK", name, tostring(seq), body)
      push("chunk/" .. name .. "/" .. seq, body) buf, blen = {}, 0 end
    each(t, function(r) local v = {} for i = 1, #cols do v[i] = jval(rd[i](r)) end
      local line = "[" .. table.concat(v, ",") .. "]" h = fnv(h, line) rows = rows + 1
      buf[#buf + 1] = line blen = blen + #line if blen >= MAX_CHUNK then flush() end end)
    flush() emit("END", name, tostring(rows), string.format("fnv1a32:%08x", h), tostring(seq))
    summary[#summary + 1] = name .. ": " .. rows end)
  if not ok then emit("ERROR", name, tostring(err)) summary[#summary + 1] = name .. ": HATA" end
  ::continue::
end
if in_cm and type(GetPlayersStats) == "function" then local s = safe(GetPlayersStats)
  if type(s) == "table" then emit("HEADER", "player_season_stats", '["playerid","teamid","compobjid","app","goals","assists","avg","yellow","red","clean_sheets"]')
    local buf = {} for i, x in ipairs(s) do buf[#buf + 1] = string.format("[%d,%d,%d,%d,%d,%d,%d,%d,%d,%d]",
      x.playerid, x.teamid, x.compobjid, x.app, x.goals, x.assists, x.avg, x.yellow, x.red, x.clean_sheets)
      if #buf == 200 then emit("CHUNK", "player_season_stats", tostring(i), "[" .. table.concat(buf, ",") .. "]") buf = {} end end
    if #buf > 0 then emit("CHUNK", "player_season_stats", "last", "[" .. table.concat(buf, ",") .. "]") end
    emit("END", "player_season_stats", tostring(#s), "-", "-") end end
emit("DONE", "[" .. table.concat((function() local w = {} for i, x in ipairs(warnings) do w[i] = jstr(x) end return w end)(), ",") .. "]")
MessageBox("FCareer dışa aktarım bitti", (in_cm and "Kariyer algılandı" or "DİKKAT: kariyer değil (ana menü)") .. "\n" .. table.concat(summary, "\n"))
```
İlk gerçek çalıştırmada doğrula: `SendHTTPRequest` ve `GetPlayersStats` 27.1.0'da var mı (script `type()` ile kontrol ediyor), log satırı uzunluk sınırı (`MAX_CHUNK`), `nations` ve `career_*` tablo adları (PROBE listeler). `GetPlayersStats` x.avg float olabilir → `%d` yerine `jval` kullan (`VERIFY`).

### 4.3 Backend parser (`LeExportV2Parser`)
- Log dosyasından son `FCX2 META`'dan başlayan bloğu al (aynı logda birden fazla çalıştırma olabilir).
- Tablo başına: `HEADER` → `CHUNK`'ları birleştir → `END` satır sayısı ve FNV-1a checksum'ı doğrula. Uymazsa import reddedilir, kullanıcıya hangi tablo eksik gösterilir.
- Bilinmeyen `schemaVersion` → 422.
- `context.userTeamId` → kadro; `context.gameDate` → `career.current_game_date` (BE-BUG-18); `exportId` + `gameDate` → snapshot (CAR-02).
- Eski CSV yolu geriye uyum için kalsın (LE-02 düzeltmeleriyle).

---
## 5. GÖRSELLER (LE'den yüz/arma) — `LEGAL-RISK`, DN-04
- Bunlar EA'nın telifli dosyaları ve **kullanıcının kendi oyun kurulumundan** gelir. Yalnız kullanıcının kendi kadrosu, kısa listesi, ligi için; yalnız kendi tarayıcısında (IndexedDB) ya da kendi özel deposunda. Public kataloğa ya da başka kullanıcıya asla.
- Yol: `LegacyFileExport` probe (FC26'da var), yoksa LE dosya tarayıcısından elle export. Kafası olmayan regen için `PlayerCapture*`.
- Dönüşüm: ImageMagick `magick p231747.dds p231747.webp` ya da Pillow (DXT1/3/5). 180×180 BC3 ≈ 32 KB → WebP 5-8 KB. Tarayıcıda dönüşüm: BC3/DXT5 çözen küçük bir JS/WASM kütüphanesi + canvas ile istemci tarafı (paket seçimi `VERIFY`; BC3 çözücü ~150 satır, gerekirse kendin yaz); sunucu dosyayı hiç görmez.
- `DATA_POLICY`'ye "kullanıcının kendi oyun dosyaları, yalnız yerel" maddesi ekle (Buğra onayı).

---
## 6. TEK TIK SENKRON (P1/P2)
1. Site `/career/import`'ta "Kişisel script indir" → backend 15 dk geçerli, tek kullanımlık, tek kariyere bağlı, boyut sınırlı bearer token üretir, `SYNC_URL`/`SYNC_TOKEN` dolu scripti verir.
2. Uç: `POST /api/v1/careers/{id}/import/le/{exportId}/{meta | chunk/{table}/{seq}}`. `(exportId, table, seq)` ile idempotent. Tüm `END` checksum'ları tutunca finalize.
3. HTTP başarısızsa script otomatik log'a düşer, kullanıcı log dosyasını sürükler (bugünkü akış).
4. Site SSE/polling ile "players 21620/21620 ✓" ilerlemesi gösterir.

---
## 7. ÖNCELİKLİ SIRA
1. **P0:** LE-01 (ID ile eşleme), LE-02 (CSV yolu), Buğra'nın makinesinde **PROBE** çalıştırması (growth profile, dynamic potential, kontrat/istatistik kolonları, `nations` adı).
2. **P0:** Export v2 + backend parser + tek kaynak dosya; `fc27_dump.lua`'yı sil.
3. **P1:** Kontrat, kiralık, ön-kontrat, gençler (gerçek POT), roller ve +/++, gizli özellikler, 28 pozisyon kodu, sezon istatistikleri. Veteran POT tavanı (LE-03).
4. **P1/P2:** Tek tık senkron (§6).
5. **P2:** Görseller (§5).
6. **Bekle:** form, moral, keskinlik, kondisyon, sakatlık, piyasa değeri, bütçe, fikstür, puan durumu, transfer geçmişi (FC26'da 26.1.7-26.1.8'de geldi). O zamana kadar elle giriş.
