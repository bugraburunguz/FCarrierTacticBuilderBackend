--- Isim tablosu HAM BAYT dokumu (SADECE OKUR). 'playernames' tablosunun sikistirilmis isim blogunu hex olarak log'a yazar;
--- sikistirmayi bilgisayarda cozecegiz. Sonuc: Logs/live_editor_<tarih>.log icinde NAMERAW satirlari.
--- Calistir (ana menude ya da kariyerde), bitince bana 'bitti' yaz. Yaklasik 1-2 dakika surer, log ~15 MB buyur.

local function say(text) Log("NAMERAW\t" .. text) end

local DB = LE.db
local tbl = DB:GetTable("playernames")
local count = tbl.written_records
if count < 0 then count = count + 65536 end
local base = tbl.first_record
local size = tbl.record_size
local table_end = base + size * count

say(string.format("INFO\tfirst_record=%d\trecord_size=%d\tcount=%d\ttable_end=%d", base, size, count, table_end))

local CHUNK = 4096
local AFTER_BYTES = 6 * 1024 * 1024

local function dump_range(label, start, length)
    local offset = 0
    local failed = false
    while offset < length and not failed do
        local n = math.min(CHUNK, length - offset)
        local ok, bytes = pcall(ReadBytes, start + offset, n)
        if not ok or type(bytes) ~= "table" or #bytes == 0 then
            say(string.format("STOP\t%s\toffset=%d", label, offset))
            failed = true
        else
            local parts = {}
            for i = 1, #bytes do parts[i] = string.format("%02X", bytes[i]) end
            say(string.format("%s\t%d\t%s", label, offset, table.concat(parts)))
            offset = offset + #bytes
        end
    end
    say(string.format("END\t%s\tbytes=%d", label, offset))
end

dump_range("BEFORE", base - 512, 512)
dump_range("RECORDS", base, size * count)
dump_range("AFTER", table_end, AFTER_BYTES)

MessageBox("Isim ham dokum", "Bitti. Veri log dosyasina yazildi (NAMERAW satirlari).\nBana 'bitti' yaz.")
