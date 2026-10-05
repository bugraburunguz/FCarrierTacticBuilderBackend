--- FC27 veritabani dokumu (SADECE OKUR, hicbir seyi degistirmez).
--- Kullanim: kariyerini yukle -> Live Editor -> Features -> Lua Engine -> execute -> bu dosyayi sec.
--- Cikti 1: C:\FC 27 Live Editor\export\<tablo>.csv  (io kullanilabiliyorsa)
--- Cikti 2 (yedek): Logs\live_editor_<tarih>.log icinde "FCDUMP" ile baslayan satirlar.

-- Dokulecek tablolar. Olmayan tablo atlanir; tum tablo adlari ayrica loga yazilir.
local TABLES = {
    "players",
    "playernames",
    "teams",
    "teamplayerlinks",
    "leagueteamlinks",
    "leagues",
    "career_playercontract",
    "career_users",
}
local OUT_DIR = FC_DUMP_OUT_DIR or "C:\\FC 27 Live Editor\\export\\"
local MARK = "FCDUMP"
local FLUSH_EVERY = 500
local PROGRESS_EVERY = 2000

local function csv(v)
    if v == nil then return "" end
    local s = tostring(v)
    if s:find('[",\r\n]') then
        s = '"' .. (s:gsub('"', '""')) .. '"'
    end
    return s
end

local function open_file(table_name)
    if type(io) ~= "table" or type(io.open) ~= "function" then return nil end
    local ok, f = pcall(io.open, OUT_DIR .. table_name .. ".csv", "wb")
    if ok and f then return f end
    return nil
end

local function sorted_field_names(tbl)
    local names = {}
    for name, _ in pairs(tbl.fields) do names[#names + 1] = name end
    table.sort(names)
    return names
end

local function dump_table(table_name)
    local tbl = LE.db:GetTable(table_name)
    if tbl == nil or tbl.fields == nil then
        Log(string.format("%s\tSKIP\t%s (tablo yok ya da alan bilgisi yok)", MARK, table_name))
        return 0
    end

    local names = sorted_field_names(tbl)
    local file = open_file(table_name)
    local via = file and "file" or "log"
    Log(string.format("%s\tBEGIN\t%s\tfields=%d\trecords=%d\tvia=%s", MARK, table_name, #names, tbl.written_records, via))

    local header = {}
    for i, name in ipairs(names) do header[i] = csv(name) end
    local header_line = table.concat(header, ",")
    if file then file:write(header_line, "\n") else Log(string.format("%s\tHEADER\t%s\t%s", MARK, table_name, header_line)) end

    local buffer, count = {}, 0
    local record = tbl:GetFirstRecord()
    while record > 0 do
        local row = {}
        for i, name in ipairs(names) do
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
            Log(string.format("%s\tROW\t%s\t%s", MARK, table_name, line))
        end

        if count % PROGRESS_EVERY == 0 then
            Log(string.format("%s\tPROGRESS\t%s\t%d", MARK, table_name, count))
        end
        record = tbl:GetNextValidRecord()
    end

    if file then
        if #buffer > 0 then file:write(table.concat(buffer, "\n"), "\n") end
        file:close()
    end
    Log(string.format("%s\tEND\t%s\t%d", MARK, table_name, count))
    return count
end

-- Tum tablo adlari (referans icin)
local ok_names, all_names = pcall(GetDBTablesNames)
if ok_names and type(all_names) == "table" then
    Log(string.format("%s\tTABLES\t%d\t%s", MARK, #all_names, table.concat(all_names, ",")))
end

local summary = {}
local total = 0
for _, name in ipairs(TABLES) do
    local ok, count = pcall(dump_table, name)
    if ok then
        summary[#summary + 1] = string.format("%s: %d", name, count)
        total = total + count
    else
        summary[#summary + 1] = string.format("%s: HATA", name)
        Log(string.format("%s\tERROR\t%s\t%s", MARK, name, tostring(count)))
    end
end

MessageBox("FC27 dokum bitti", table.concat(summary, "\n") ..
    "\n\nDosya: " .. OUT_DIR .. "<tablo>.csv\nDosya olusmadiysa: Logs klasorundeki live_editor_<tarih>.log (FCDUMP satirlari).")
