--- Isim testi 4 (SADECE OKUR). playernames.name alani 'type 13' (sikistirilmis/dizinli metin). Ham degerini ve
--- olasi metin konumlarini deniyoruz. Sonuc log'a yazilir (NAMEPROBE4). Calistir, sonra 'bitti' yaz.

local function say(text) Log("NAMEPROBE4\t" .. text) end

local DB = LE.db
local tbl = DB:GetTable("playernames")
local count = tbl.written_records
if count < 0 then count = count + 65536 end
local base = tbl.first_record
local size = tbl.record_size
say(string.format("first_record=0x%X record_size=%d count=%d", base, size, count))

local function printable(s)
    if s == nil then return "nil" end
    return (tostring(s):gsub("[^%g ]", "?")):sub(1, 40)
end

local function hex(addr, n)
    local ok, bytes = pcall(ReadBytes, addr, n)
    if not ok or type(bytes) ~= "table" then return "ReadBytes hata" end
    local out = {}
    for i = 1, #bytes do out[i] = string.format("%02X", bytes[i]) end
    return table.concat(out, " ")
end

for _, id in ipairs({ 0, 1, 2, 6183, 8211, 33301, 42468, 43065 }) do
    local record = base + size * id
    local raw = ReadInteger and ReadInteger(record) or -1
    say(string.format("nameid idx=%d record=0x%X bytes=%s", id, record, hex(record, size)))
    say(string.format("  raw int @0 = %s", tostring(raw)))
    local candidates = {
        { "record+raw", record + (raw or 0) },
        { "first+raw", base + (raw or 0) },
        { "end+raw", base + size * count + (raw or 0) },
    }
    for _, c in ipairs(candidates) do
        local ok, s = pcall(ReadString, c[2])
        say(string.format("  %s (0x%X) -> ok=%s [%s]", c[1], c[2], tostring(ok), printable(s)))
    end
end

local tail = base + size * count
say("tablo sonrasi 96 bayt: " .. hex(tail, 96))
say("tablo oncesi 48 bayt: " .. hex(base - 48, 48))

MessageBox("Isim testi 4", "Bitti. Sonuc log dosyasina yazildi (NAMEPROBE4).\nBana 'bitti' yaz.")
