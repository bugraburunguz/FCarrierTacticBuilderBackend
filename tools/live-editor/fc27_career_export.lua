--- FC Career Platform - kariyer disa aktarimi (SADECE OKUR, hicbir seyi degistirmez).
--- Kullanim: oyunu ac -> KARIYERINI YUKLE (ana menude degil, kariyerin icinde ol) -> Live Editor -> Lua Engine -> bu dosyayi calistir.
--- Cikti: Live Editor Lua dosya yazmayi kapali tuttugu icin veri Logsive_editor_<tarih>.log dosyasina "FCDUMP" satirlari olarak yazilir;
---        bu log dosyasini sitedeki 'Ice aktar' sayfasina yukle. (Dosya yazma aciksa Masaustureerexport klasorune CSV de yazilir.)
--- Dosya olusmazsa Logs\live_editor_<tarih>.log icinde "FCDUMP" satirlari yazilir.

local FALLBACK_DIR = "C:\\FC 27 Live Editor\\export\\"
local FOLDER = "careerexport"

local function can_write(dir)
    if type(io) ~= "table" or type(io.open) ~= "function" then return false end
    local probe = dir .. ".probe"
    local ok, f = pcall(io.open, probe, "wb")
    if ok and f then
        f:close()
        if type(os) == "table" and type(os.remove) == "function" then pcall(os.remove, probe) end
        return true
    end
    return false
end

local function ensure_dir(dir)
    if can_write(dir) then return true end
    if type(os) == "table" and type(os.execute) == "function" then
        pcall(os.execute, 'if not exist "' .. dir .. '" mkdir "' .. dir .. '"')
    end
    return can_write(dir)
end

local function pick_out_dir()
    if FC_DUMP_OUT_DIR then return FC_DUMP_OUT_DIR end
    local home = type(os) == "table" and type(os.getenv) == "function" and os.getenv("USERPROFILE") or nil
    if home then
        local bases = { home .. "\\OneDrive\\Desktop\\", home .. "\\Desktop\\" }
        for _, base in ipairs(bases) do
            local dir = base .. FOLDER .. "\\"
            if ensure_dir(dir) then return dir end
        end
    end
    return FALLBACK_DIR
end

local OUT_DIR = pick_out_dir()
local MARK = "FCDUMP"
local FLUSH_EVERY = 500

local TABLES = {
    { name = "players", columns = { "playerid", "overallrating", "potential" } },
    { name = "teams", columns = { "teamid", "teamname", "overallrating", "attackrating", "midfieldrating", "defenserating",
        "buildupplay", "defensivedepth", "youthdevelopment", "domesticprestige", "internationalprestige", "popularity",
        "clubworth", "profitability", "rivalteam", "gender" } },
    { name = "teamplayerlinks", columns = { "playerid", "teamid" } },
    { name = "leagueteamlinks", columns = { "teamid", "leagueid" } },
    { name = "leagues", columns = { "leagueid", "leaguename", "isinternationalleague" } },
}

local function csv(v)
    if v == nil then return "" end
    local s = tostring(v)
    if s:find('[",\r\n]') then
        s = '"' .. (s:gsub('"', '""')) .. '"'
    end
    return s
end

local function open_file(name)
    if type(io) ~= "table" or type(io.open) ~= "function" then return nil end
    local ok, f = pcall(io.open, OUT_DIR .. name .. ".csv", "wb")
    if ok and f then return f end
    return nil
end

local function dump_table(spec)
    local tbl = LE.db:GetTable(spec.name)
    if tbl == nil or tbl.fields == nil then
        Log(string.format("%s\tSKIP\t%s", MARK, spec.name))
        return 0
    end
    local file = open_file(spec.name)
    local header = {}
    for i, name in ipairs(spec.columns) do header[i] = csv(name) end
    local header_line = table.concat(header, ",")
    if file then file:write(header_line, "\n") else Log(string.format("%s\tHEADER\t%s\t%s", MARK, spec.name, header_line)) end

    local buffer, count = {}, 0
    local record = tbl:GetFirstRecord()
    while record > 0 do
        local row = {}
        for i, name in ipairs(spec.columns) do
            row[i] = csv(tbl:GetRecordFieldValue(record, name))
        end
        local line = table.concat(row, ",")
        count = count + 1
        if file then
            buffer[#buffer + 1] = line
            if #buffer >= FLUSH_EVERY then
                file:write(table.concat(buffer, "\n"), "\n")
                buffer = {}
            end
        else
            Log(string.format("%s\tROW\t%s\t%s", MARK, spec.name, line))
        end
        record = tbl:GetNextValidRecord()
    end
    if file then
        if #buffer > 0 then file:write(table.concat(buffer, "\n"), "\n") end
        file:close()
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

MessageBox("Disa aktarim bitti", table.concat(summary, "
") ..
    "

Veri su dosyaya yazildi (Live Editor klasoru):
Logs\live_editor_<tarih>.log
Bu dosyayi sitedeki 'Ice aktar' sayfasina yukle.")
