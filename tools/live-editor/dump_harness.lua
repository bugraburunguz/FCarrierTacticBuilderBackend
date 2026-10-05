-- fc27_dump.lua mantik testi: sahte LE.db / Log / MessageBox ile calisir (oyun gerekmez).
-- Kullanim: lua5.4 dump_harness.lua <cikti_klasoru/> <file|log> fc27_dump.lua
local out_dir = arg[1] or "/tmp/dump/"
local mode = arg[2] or "file"

FC_DUMP_OUT_DIR = (mode == "file") and out_dir or "/yok/boyle/bir/klasor/"
local logs, box = {}, nil
function Log(text) logs[#logs + 1] = text end
function MessageBox(title, text) box = title .. "|" .. text end
function GetDBTablesNames() return { "players", "teams", "ghost" } end

local function fake_table(fields, rows)
    local t = { fields = {}, written_records = #rows, i = 0 }
    for _, name in ipairs(fields) do t.fields[name] = { name = name } end
    function t:GetFirstRecord() self.i = 1; return self.i end
    function t:GetNextValidRecord() self.i = self.i + 1; return self.i <= #rows and self.i or 0 end
    function t:GetRecordFieldValue(rec, name) return rows[rec][name] end
    return t
end

local players = fake_table({ "playerid", "potential", "overallrating", "nick" }, {
    { playerid = 231747, potential = 91, overallrating = 91, nick = 'Kylian "KM" Mbappe, Jr' },
    { playerid = 1, potential = 70, overallrating = 55, nick = "A" },
})
local teams = fake_table({ "teamid", "teamname" }, { { teamid = 243, teamname = "Real Madrid" } })
local broken = { fields = { x = {} }, written_records = 1 }
function broken:GetFirstRecord() error("bellek okuma hatasi") end

LE = { db = { GetTable = function(_, name)
    if name == "players" then return players end
    if name == "teams" then return teams end
    if name == "career_users" then return broken end
    return nil
end } }

dofile(arg[3] or "fc27_dump.lua")

print("LOGLINES=" .. #logs)
for _, l in ipairs(logs) do print(l) end
print("BOX=" .. tostring(box))
