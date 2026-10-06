--- Isim testi 3 (SADECE OKUR). Oyunda BIRDEN FAZLA veritabani var (ana db + dil/loc db). GetTable ilkini veriyor;
--- isimler muhtemelen digerinde. Burada tum db'leri ve icindeki tablolari gezip 'playernames' tablolarini ariyoruz.
--- Sonuc log'a yazilir (NAMEPROBE3). Calistir, sonra bana 'bitti' yaz.

local function say(text) Log("NAMEPROBE3\t" .. text) end

local DB = LE.db
DB:LoadMeta()
local TABLE = require 'imports/t3db/table'

local wanted = { playernames = true, editedplayernames = true }
local found = {}

local db = DB:GetDBFromService()
local db_index = 0
while db > 0 do
    db_index = db_index + 1
    local names = {}
    local db_tbl = DB:GetTableFromDB(db)
    while db_tbl > 0 do
        local tname = DB:GetT3DBTableName(db_tbl)
        if tname ~= nil and tname ~= "" then
            names[#names + 1] = tname
            if wanted[tname] then found[#found + 1] = { db_index = db_index, ptr = db_tbl, name = tname } end
        end
        db_tbl = DB:GetNextTable(db_tbl)
    end
    say(string.format("db #%d: %d tablo: %s", db_index, #names, table.concat(names, ",")))
    db = DB:GetNextDB(db)
end

for _, item in ipairs(found) do
    local tbl = TABLE:new()
    tbl:Load(item.ptr, DB.meta)
    local count = tbl.written_records
    if count < 0 then count = count + 65536 end
    say(string.format("[db #%d] %s: kayit=%d record_size=%d", item.db_index, item.name, count, tbl.record_size))
    for fname, fld in pairs(tbl.fields) do
        say(string.format("  alan %s type=%s offset=%s sz=%s", fname, tostring(fld.type), tostring(fld.offset), tostring(fld.sz)))
    end
    local shown = 0
    for index = 0, count - 1 do
        local record = tbl.first_record + tbl.record_size * index
        if tbl:IsRecordValid(record) then
            local out = {}
            for fname, fld in pairs(tbl.fields) do
                local ok, value
                if fld.type == 3 then
                    ok, value = pcall(function() return fld:GetInt(record) end)
                else
                    ok, value = pcall(function() return fld:GetString(record) end)
                end
                out[#out + 1] = string.format("%s=[%s]", fname, tostring(value))
            end
            -- ilk 3 kayit + Ronaldo'nun isim id'leri
            local nameid_fld = tbl.fields["nameid"]
            local id = nameid_fld and nameid_fld:GetInt(record) or -1
            if shown < 3 or id == 6183 or id == 42468 or id == 8211 or id == 33301 then
                say(string.format("  kayit idx=%d %s", index, table.concat(out, " ")))
                shown = shown + 1
            end
        end
    end
end

MessageBox("Isim testi 3", "Bitti. Sonuc log dosyasina yazildi (NAMEPROBE3).\nBana 'bitti' yaz.")
