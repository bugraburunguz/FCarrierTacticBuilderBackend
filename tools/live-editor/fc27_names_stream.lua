--- Isim tablosu SIKISTIRILMIS AKIS dokumu (SADECE OKUR). Onceki ham dokumde kod agaci bulundu; isimlerin bit akisi
--- tablonun bir isaretcisinin gosterdigi yerde. Burada o akisi hex olarak log'a yazariz (NAMESTREAM satirlari).
--- Calistir, bitince bana 'bitti' yaz. Kisa surer (~300 KB veri).

local function say(text) Log("NAMESTREAM\t" .. text) end

local tbl = LE.db:GetTable("playernames")
local count = tbl.written_records
if count < 0 then count = count + 65536 end
local table_end = tbl.first_record + tbl.record_size * count

local length = ReadInteger(table_end + 8)
local pointer = ReadQword(table_end + 16)
say(string.format("INFO\ttable_end=%d\tlength=%d\tpointer=%d", table_end, length, pointer))

local CHUNK = 4096
local total = length + 256
local offset = 0
while offset < total do
    local n = math.min(CHUNK, total - offset)
    local ok, bytes = pcall(ReadBytes, pointer + offset, n)
    if not ok or type(bytes) ~= "table" or #bytes == 0 then
        say(string.format("STOP\toffset=%d", offset))
        break
    end
    local parts = {}
    for i = 1, #bytes do parts[i] = string.format("%02X", bytes[i]) end
    say(string.format("STREAM\t%d\t%s", offset, table.concat(parts)))
    offset = offset + #bytes
end
say(string.format("END\tbytes=%d", offset))

MessageBox("Isim akisi", "Bitti. Veri log dosyasina yazildi (NAMESTREAM satirlari).\nBana 'bitti' yaz.")
