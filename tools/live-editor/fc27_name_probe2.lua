--- Isim testi 2 (SADECE OKUR). Hangi global fonksiyonlar var, isim nereden okunur? Sonuc LOG'a yazilir (NAMEPROBE).
--- Kariyerin ICINDE calistir. Bitince bana 'bitti' yaz; log'u ben okurum.

local function dump_keys(label, tbl)
    local keys = {}
    if type(tbl) == "table" then
        for k, v in pairs(tbl) do keys[#keys + 1] = tostring(k) .. ":" .. type(v) end
        table.sort(keys)
    end
    Log("NAMEPROBE\t" .. label .. "\t" .. table.concat(keys, " "))
end

dump_keys("_G", _G)
dump_keys("LE", LE)
if type(LE) == "table" and type(LE.db) == "table" then dump_keys("LE.db", LE.db) end
local mt = type(LE) == "table" and getmetatable(LE) or nil
if type(mt) == "table" then dump_keys("LE.meta", mt) end
if type(mt) == "table" and type(mt.__index) == "table" then dump_keys("LE.meta.__index", mt.__index) end

-- Ronaldo (20801) isim id'leri
local players = LE.db:GetTable("players")
local record = players:GetFirstRecord()
local found = nil
while record > 0 do
    if players:GetRecordFieldValue(record, "playerid") == 20801 then found = record break end
    record = players:GetNextValidRecord()
end
if found then
    local ids = {}
    for _, f in ipairs({ "firstnameid", "lastnameid", "commonnameid", "playerjerseynameid" }) do
        ids[f] = players:GetRecordFieldValue(found, f)
        Log("NAMEPROBE\tRonaldo " .. f .. "=" .. tostring(ids[f]))
    end
    local variants = {}
    for _, id in pairs(ids) do
        variants[#variants + 1] = id
        variants[#variants + 1] = tostring(id)
        variants[#variants + 1] = "playername_" .. tostring(id)
        variants[#variants + 1] = "PLAYERNAME_" .. tostring(id)
        variants[#variants + 1] = "nameid_" .. tostring(id)
        variants[#variants + 1] = "player_name_" .. tostring(id)
    end
    if type(GetGameLocString) == "function" then
        for _, key in ipairs(variants) do
            local ok, value = pcall(GetGameLocString, key)
            Log(string.format("NAMEPROBE\tGetGameLocString(%s) ok=%s value=[%s]", tostring(key), tostring(ok), tostring(value)))
        end
    else
        Log("NAMEPROBE\tGetGameLocString yok")
    end
end

-- playernames tablosunu farkli sekillerde oku
local t = LE.db:GetTable("playernames")
if t ~= nil then
    Log("NAMEPROBE\tplayernames fields: " .. (function() local n = {} for k in pairs(t.fields or {}) do n[#n + 1] = k end table.sort(n) return table.concat(n, ",") end)())
    local r = t:GetFirstRecord()
    Log("NAMEPROBE\tplayernames first record=" .. tostring(r))
    for _, f in ipairs({ "nameid", "name", "commentaryid" }) do
        local ok, v = pcall(function() return t:GetRecordFieldValue(r, f) end)
        Log(string.format("NAMEPROBE\tplayernames[%s] ok=%s v=[%s] type=%s", f, tostring(ok), tostring(v), type(v)))
    end
end

MessageBox("Isim testi 2", "Bitti. Sonuc log dosyasina yazildi (NAMEPROBE satirlari).\nBana 'bitti' yaz.")
