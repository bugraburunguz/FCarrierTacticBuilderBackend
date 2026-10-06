--- FC Career Platform - BASE KATALOG disa aktarimi (SADECE OKUR, hicbir seyi degistirmez).
--- Tum oyuncular (alt lig, genc, regen, serbest dahil) + isimleri + takim/lig verisi.
--- Kullanim: oyunu ac -> Live Editor -> Lua Engine -> bu dosyayi calistir (ana menude de, kariyerde de calisir).
--- Cikti: Live Editor'un Logs/live_editor_<tarih>.log dosyasina "FCDUMP" satirlari olarak yazilir
---        (Lua dosya yazma kapali oldugu icin). Dosyayi sonra projenin icindeki FC 27 LE klasorunden aliriz.
---
--- NOT: Live Editor tablo kayit sayisini isaretli 16-bit okuyor; 32767'den buyuk tablolarda (playernames ~43 bin)
--- sayi eksiye dondugu icin eski donguler 1 kayitta duruyordu. Burada sayi duzeltilip kayitlar tek tek gezilir.

local MARK = "FCDUMP"
local PROGRESS_EVERY = 5000

local PLAYER_COLUMNS = {
    "playerid", "birthdate", "height", "weight", "preferredfoot", "nationality", "gender",
    "firstnameid", "lastnameid", "commonnameid", "playerjerseynameid",
    "overallrating", "potential",
    "preferredposition1", "preferredposition2", "preferredposition3", "preferredposition4",
    "preferredposition5", "preferredposition6", "preferredposition7",
    "weakfootabilitytypecode", "skillmoves", "internationalrep",
    "trait1", "trait2", "icontrait1", "icontrait2", "runningcode1", "runningcode2", "bodytypecode",
    "acceleration", "sprintspeed", "agility", "balance", "reactions", "ballcontrol", "dribbling", "composure",
    "vision", "shortpassing", "longpassing", "crossing", "curve", "freekickaccuracy", "finishing", "shotpower",
    "longshots", "volleys", "penalties", "positioning", "headingaccuracy", "defensiveawareness",
    "standingtackle", "slidingtackle", "interceptions", "jumping", "stamina", "strength", "aggression",
    "gkdiving", "gkhandling", "gkkicking", "gkpositioning", "gkreflexes",
}

local TABLES = {
    { name = "playernames", columns = { "nameid", "name" } },
    { name = "players", columns = PLAYER_COLUMNS },
    { name = "teams", columns = { "teamid", "teamname", "overallrating", "attackrating", "midfieldrating",
        "defenserating", "buildupplay", "defensivedepth", "youthdevelopment", "domesticprestige",
        "internationalprestige", "popularity", "clubworth", "profitability", "rivalteam", "gender" } },
    { name = "teamplayerlinks", columns = { "playerid", "teamid" } },
    { name = "leagueteamlinks", columns = { "teamid", "leagueid" } },
    { name = "leagues", columns = { "leagueid", "leaguename", "isinternationalleague" } },
}

local function csv(v)
    if v == nil then return "" end
    local s = tostring(v)
    if s:find('[",\r\n\t]') then
        s = '"' .. (s:gsub('"', '""'):gsub("[\r\n\t]", " ")) .. '"'
    end
    return s
end

local function record_count(tbl)
    local count = tbl.written_records
    if count < 0 then count = count + 65536 end
    return count
end

local function dump_table(spec)
    local tbl = LE.db:GetTable(spec.name)
    if tbl == nil or tbl.fields == nil then
        Log(string.format("%s\tSKIP\t%s", MARK, spec.name))
        return 0
    end
    local columns = {}
    for _, name in ipairs(spec.columns) do
        if tbl.fields[name] ~= nil then
            columns[#columns + 1] = name
        else
            Log(string.format("%s\tMISSINGFIELD\t%s\t%s", MARK, spec.name, name))
        end
    end
    local header = {}
    for i, name in ipairs(columns) do header[i] = csv(name) end
    local total = record_count(tbl)
    Log(string.format("%s\tBEGIN\t%s\tfields=%d\trecords=%d\tvia=log", MARK, spec.name, #header, total))
    Log(string.format("%s\tHEADER\t%s\t%s", MARK, spec.name, table.concat(header, ",")))

    local count = 0
    for index = 0, total - 1 do
        local record = tbl.first_record + (tbl.record_size * index)
        if tbl:IsRecordValid(record) then
            local row = {}
            for i, name in ipairs(columns) do
                row[i] = csv(tbl:GetRecordFieldValue(record, name))
            end
            count = count + 1
            Log(string.format("%s\tROW\t%s\t%s", MARK, spec.name, table.concat(row, ",")))
            if count % PROGRESS_EVERY == 0 then
                Log(string.format("%s\tPROGRESS\t%s\t%d", MARK, spec.name, count))
            end
        end
    end
    Log(string.format("%s\tEND\t%s\t%d", MARK, spec.name, count))
    return count
end

local summary = {}
for _, spec in ipairs(TABLES) do
    local ok, count = pcall(dump_table, spec)
    summary[#summary + 1] = ok and string.format("%s: %d satir", spec.name, count) or string.format("%s: HATA", spec.name)
    if not ok then Log(string.format("%s\tERROR\t%s\t%s", MARK, spec.name, tostring(count))) end
end

MessageBox("Base disa aktarim bitti", table.concat(summary, "\n") ..
    "\n\nVeri Live Editor log dosyasina yazildi:\nFC 27 LE v27.1.0/Logs/live_editor_<tarih>.log\nBana 'bitti' yaz, gerisini ben alirim.")
