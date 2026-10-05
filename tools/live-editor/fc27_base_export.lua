--- FC Career Platform - BASE KATALOG disa aktarimi (SADECE OKUR, hicbir seyi degistirmez).
--- Tum oyuncular (alt lig, genc, regen, serbest dahil) + isimleri + takim/lig verisi.
--- Kullanim: oyunu ac -> (tercihen kariyere BASLAMADAN, ana menuden) Live Editor -> Lua Engine -> bu dosyayi calistir.
--- Cikti: Live Editor'un Logs\live_editor_<tarih>.log dosyasina "FCDUMP" satirlari olarak yazilir
---        (Lua dosya yazma kapali oldugu icin). Dosyayi sonra projenin icindeki FC 27 LE klasorunden aliriz.

local MARK = "FCDUMP"
local PROGRESS_EVERY = 4000

local PLAYER_COLUMNS = {
    "playerid", "birthdate", "height", "weight", "preferredfoot", "nationality", "gender",
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
    { name = "players", columns = PLAYER_COLUMNS, with_name = true },
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

local function safe_name(playerid)
    local ok, name = pcall(GetPlayerName, playerid)
    if ok and name ~= nil then return tostring(name) end
    return ""
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
    if spec.with_name then header[#header + 1] = "name" end
    Log(string.format("%s\tBEGIN\t%s\tfields=%d\trecords=%d\tvia=log", MARK, spec.name, #header, tbl.written_records))
    Log(string.format("%s\tHEADER\t%s\t%s", MARK, spec.name, table.concat(header, ",")))

    local count = 0
    local record = tbl:GetFirstRecord()
    while record > 0 do
        local row = {}
        for i, name in ipairs(columns) do
            row[i] = csv(tbl:GetRecordFieldValue(record, name))
        end
        if spec.with_name then
            row[#row + 1] = csv(safe_name(tbl:GetRecordFieldValue(record, "playerid")))
        end
        count = count + 1
        Log(string.format("%s\tROW\t%s\t%s", MARK, spec.name, table.concat(row, ",")))
        if count % PROGRESS_EVERY == 0 then
            Log(string.format("%s\tPROGRESS\t%s\t%d", MARK, spec.name, count))
        end
        record = tbl:GetNextValidRecord()
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
    "\n\nVeri Live Editor log dosyasina yazildi:\nFC 27 LE v27.1.0\\Logs\\live_editor_<tarih>.log\nBana 'bitti' yaz, gerisini ben alirim.")
