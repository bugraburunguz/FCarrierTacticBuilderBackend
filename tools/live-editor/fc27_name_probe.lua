--- Isim testi (SADECE OKUR). Oyuncu isimleri bos geldi; hangi durumda dolu geldigini bulmak icin.
--- Calistir -> sonucu MessageBox'ta gorursun, log'a da yazilir. Ana menude VE kariyerin icinde bir kez deneyin.

local ids = { 20801, 158023, 231747, 239085 }
local lines = {}

for _, id in ipairs(ids) do
    local ok, name = pcall(GetPlayerName, id)
    lines[#lines + 1] = string.format("GetPlayerName(%d) -> ok=%s type=%s value=[%s]", id, tostring(ok), type(name), tostring(name))
end

-- oyuncu tablosundan gelen id ile de dene (tip farki olabilir)
local players = LE.db:GetTable("players")
local record = players:GetFirstRecord()
local first_id = players:GetRecordFieldValue(record, "playerid")
local ok2, name2 = pcall(GetPlayerName, first_id)
lines[#lines + 1] = string.format("ilk kayit id=%s (type=%s) -> ok=%s [%s]", tostring(first_id), type(first_id), tostring(ok2), tostring(name2))

-- isim tablolari
for _, tname in ipairs({ "playernames", "players_names", "editedplayernames" }) do
    local t = LE.db:GetTable(tname)
    if t ~= nil then
        lines[#lines + 1] = string.format("tablo %s: written_records=%s", tname, tostring(t.written_records))
        local r = t:GetFirstRecord()
        local c = 0
        while r > 0 and c < 3 do
            lines[#lines + 1] = string.format("  kayit %d: nameid=%s name=%s", r, tostring(t:GetRecordFieldValue(r, "nameid")), tostring(t:GetRecordFieldValue(r, "name")))
            r = t:GetNextValidRecord()
            c = c + 1
        end
    else
        lines[#lines + 1] = "tablo yok: " .. tname
    end
end

local ok3, tname = pcall(GetTeamName, 1)
lines[#lines + 1] = string.format("GetTeamName(1) -> ok=%s [%s]", tostring(ok3), tostring(tname))

for _, l in ipairs(lines) do Log("NAMEPROBE\t" .. l) end
MessageBox("Isim testi", table.concat(lines, "\n"))
