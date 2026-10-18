--- FC Assist: fc27_career_export v2 — SALT OKUR, oyun verisini değiştirmez.
--- LE v27.1.0 Lua API v2. Kariyerin İÇİNDE (kariyer ana ekranında) çalıştır.
--- Çıktı: Live Editor klasörü > Logs > live_editor_<tarih>.log içine "FCX2" satırları olarak düşer
--- (Live Editor Lua'dan dosya yazmaya izin vermez). İşlem bitince "FC Assist dışa aktarım bitti" penceresi çıkar;
--- sonra FC Assist sitesinde İçe aktar sayfasına bu .log dosyasını yükle.
--- Şema: docs/review/v2/07_LIVE_EDITOR.md §4. Kaynak: backend tools/live-editor/ (web public/ kopyası `npm run sync:le-script` ile gelir).
require 'imports/career_mode/helpers'
require 'imports/other/helpers'
local UserMgr = require 'imports/career_mode/FCECareerModeUserManager'
local SYNC_URL, SYNC_TOKEN = nil, nil          -- site kişisel script üretirken doldurur (opsiyonel)
local PROBE = false                            -- true = yalnız tüm tabloların şemasını dök
local MARK, VER, SCHEMA_V, MAX_CHUNK = "FCX2", "2.0.3", 2, 16000   -- 2.0.1: boolean'lar JSON'da; 2.0.2: bütçe, kadro kiralık/ön sözleşme, DBMAP; 2.0.3: previousteam

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
  elseif t == "string" then return jstr(v)
  elseif t == "boolean" then return v and "true" or "false" end return "null" end
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
  -- 2.0.2: transfer bütçesi, yalnız okur (Set* çağrılmaz). GetTransferBudget LE'de kullanımdan kalktı (nil döner); imza belgesiz olduğu için
  -- önce argümansız, sonra kullanıcı takımıyla denenir.
  local budget = safe(GetUserTransferBudget)
  if type(budget) ~= "number" then budget = safe(GetUserTransferBudget, ctx.userTeamIdMem) end
  if type(budget) == "number" and budget >= 0 then ctx.transferBudget = math.floor(budget) end
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
  {"career_calendar",0,1}, {"career_playercontract",0,1}, {"career_presignedcontract",0,1}, {"career_youthplayers",0,1},
  -- 2.0.3: oyun kariyer kiralıklarını tabloya yazmaz; kalıcı transferde previousteam'e ayrıldığı kulübü yazar (sunucu kiralığı buradan ayırır).
  {"previousteam",0,1} }

local meta = {} for k, v in pairs(ctx) do meta[#meta + 1] = jstr(k) .. ":" .. jval(v) end
local metaJson = string.format('{"schema":"fccareer.le-export","schemaVersion":%d,"script":"%s","context":{%s}}', SCHEMA_V, VER, table.concat(meta, ","))
emit("META", metaJson); push("meta", metaJson)

local summary = {}
-- Satır listesini FCX2 tablosu olarak yazar (HEADER/CHUNK/END + FNV-1a): hem DB tabloları hem türetilmiş tablolar (fca_*) için.
local function emitRows(name, cols, iterate)
  local hdr = {} for i, c in ipairs(cols) do hdr[i] = jstr(c) end
  emit("HEADER", name, "[" .. table.concat(hdr, ",") .. "]")
  local buf, blen, seq, rows, h = {}, 0, 0, 0, 2166136261
  local function flush() if #buf == 0 then return end seq = seq + 1
    local body = "[" .. table.concat(buf, ",") .. "]" emit("CHUNK", name, tostring(seq), body)
    push("chunk/" .. name .. "/" .. seq, body) buf, blen = {}, 0 end
  iterate(function(values) local v = {} for i = 1, #cols do v[i] = jval(values[i]) end
    local line = "[" .. table.concat(v, ",") .. "]" h = fnv(h, line) rows = rows + 1
    buf[#buf + 1] = line blen = blen + #line if blen >= MAX_CHUNK then flush() end end)
  flush() emit("END", name, tostring(rows), string.format("fnv1a32:%08x", h), tostring(seq))
  summary[#summary + 1] = name .. ": " .. rows
end
local function emitTable(name, t)
  local cols, rd = {}, {} for fname in pairs(t.fields) do cols[#cols + 1] = fname end table.sort(cols)
  for i, c in ipairs(cols) do rd[i] = reader(t.fields[c]) end
  emitRows(name, cols, function(row) each(t, function(r) local v = {} for i = 1, #cols do v[i] = rd[i](r) end row(v) end) end)
end
for _, spec in ipairs(TABLES) do
  local name, required, cmOnly = spec[1], spec[2] == 1, spec[3] == 1
  if cmOnly and not in_cm then goto continue end
  local ok, err = pcall(function()
    local t = LE.db:GetTable(name)
    if not t or not t.fields then warnings[#warnings + 1] = "MISSINGTABLE " .. name
      if required then error("zorunlu tablo yok") end return end
    emitTable(name, t) end)
  if not ok then emit("ERROR", name, tostring(err)) summary[#summary + 1] = name .. ": HATA" end
  ::continue::
end

-- 2.0.2: LE.db:GetTable adı eşleşen İLK tabloyu döner. FC27'de aynı ad birden çok veritabanında olabilir (temel + kariyer): kariyerde yapılan
-- kiralıklar ilk kopyada yoktu. Teşhis: her veritabanının tablo adı → satır sayısı (DBMAP); kiralık tablosunun diğer kopyaları playerloans_db<N>.
local TABLE = require 'imports/t3db/table'
local okMap, errMap = pcall(function()
  LE.db:LoadMeta()
  local db, dbi, loans = LE.db:GetDBFromService(), 0, 0
  while db and db > 0 do
    dbi = dbi + 1
    local names, tp = {}, LE.db:GetTableFromDB(db)
    while tp and tp > 0 do
      local t = TABLE:new() t:Load(tp, LE.db.meta)
      if not t:HasNoName() then
        names[#names + 1] = jstr(t.name) .. ":" .. tostring(nrec(t))
        if t.name == "playerloans" and t.fields then loans = loans + 1
          if loans > 1 then emitTable("playerloans_db" .. dbi, t) end end
      end
      tp = LE.db:GetNextTable(tp)
    end
    emit("DBMAP", tostring(dbi), "{" .. table.concat(names, ",") .. "}")
    db = LE.db:GetNextDB(db)
  end end)
if not okMap then warnings[#warnings + 1] = "DBMAP " .. tostring(errMap) end

-- 2.0.2: kullanıcı kadrosu için oyunun kendi sorguları (salt okuma): kiralıkta mı (IsPlayerLoanedOut), ön sözleşmeli mi (IsPlayerPresigned).
-- Kiralık tablosu kariyer kiralığını taşımasa da kadrodaki kiralık oyuncu böyle bilinir; kaynak kulüp ana kayıttaki kulüptür.
local userTeam = ctx.userTeamIdMem
if in_cm and userTeam and userTeam > 0 then
  local okSq, errSq = pcall(function()
    local links = LE.db:GetTable("teamplayerlinks")
    local pid, tid = reader(links.fields.playerid), reader(links.fields.teamid)
    emitRows("fca_squadstatus", {"playerid", "loanedout", "presigned"}, function(row)
      each(links, function(r) if tid(r) == userTeam then local p = pid(r)
        row({ p, safe(IsPlayerLoanedOut, p) == true and 1 or 0, safe(IsPlayerPresigned, p) == true and 1 or 0 }) end end) end) end)
  if not okSq then warnings[#warnings + 1] = "SQUADSTATUS " .. tostring(errSq) end
end
if in_cm and type(GetPlayersStats) == "function" then local s = safe(GetPlayersStats)
  if type(s) == "table" then emit("HEADER", "player_season_stats", '["playerid","teamid","compobjid","app","goals","assists","avg","yellow","red","clean_sheets"]')
    local buf = {} for i, x in ipairs(s) do
      buf[#buf + 1] = "[" .. table.concat({ jval(x.playerid), jval(x.teamid), jval(x.compobjid), jval(x.app), jval(x.goals), jval(x.assists),
        jval(x.avg), jval(x.yellow), jval(x.red), jval(x.clean_sheets) }, ",") .. "]"
      if #buf == 200 then emit("CHUNK", "player_season_stats", tostring(i), "[" .. table.concat(buf, ",") .. "]") buf = {} end end
    if #buf > 0 then emit("CHUNK", "player_season_stats", "last", "[" .. table.concat(buf, ",") .. "]") end
    emit("END", "player_season_stats", tostring(#s), "-", "-") end end
emit("DONE", "[" .. table.concat((function() local w = {} for i, x in ipairs(warnings) do w[i] = jstr(x) end return w end)(), ",") .. "]")
MessageBox("FC Assist dışa aktarım bitti", (in_cm and "Kariyer algılandı" or "DİKKAT: kariyer değil (ana menü)") .. "\n" .. table.concat(summary, "\n"))
