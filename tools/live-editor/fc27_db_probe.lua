--- Veritabanı teşhisi (SADECE OKUR). Kariyerin İÇİNDEYKEN çalıştır; çıktı log'a DBPROBE satırları olarak düşer.
--- Neden: 2.0.2 log'unda 2. veritabanı adsız tablolardan oluşuyor (meta'da adı yok → DBMAP 2 {}), career_* tabloları eksik ve
--- kariyerde kiralanan oyuncular playerloans'ta yok. Bu betik her tablonun kısa adını/şemasını ve aranan oyuncu kimliklerinin
--- hangi tablo/sütunda geçtiğini yazar; adsız tablolarda alanlar ham bit genişliğiyle (min=0) çözülür.

local MARK = "DBPROBE"
local function say(...) Log(table.concat({ MARK, ... }, "\t")) end

-- Kullanıcının kiralıkları + kıyas için iki transfer (Román, Gutiérrez). Kimlikler ana katalogdan.
local TARGETS = { 87512, 279128, 82789, 86957, 88567, 72170, 85660, 72419, 88043, 278776, 71740 }
local WANT = {} for _, id in ipairs(TARGETS) do WANT[id] = true end
local MAX_ROWS_PER_TABLE = 40

local function nrec(n) if n < 0 then n = n + 65536 end return n end
local function short(addr, off) local b = MEMORY:ReadBytes(addr + off, 4) local s = ""
  for i = 1, 4 do s = s .. string.char(b[i]) end return s end
local function printable(s) return (s:gsub("[^%w_]", "?")) end

-- Tablonun başlığını meta'ya bakmadan okur (t3db/table.lua ile aynı ofsetler).
local function rawTable(tp, meta)
  local t = { shortname = short(tp, 0x40) }
  t.name = meta.shortname_name_tables_map[t.shortname]
  t.first = MEMORY:ReadQword(tp + 0x30)
  t.size = MEMORY:ReadInt(tp + 0x44)
  t.count = nrec(MEMORY:ReadShort(tp + 0x7C))
  t.cols = {}
  local fmeta = meta.field_desc_map[t.shortname]
  local n, ca = MEMORY:ReadChar(tp + 0x82), tp + 0x84
  for i = 1, n do
    local c = { shortname = short(ca, 0x8), type = MEMORY:ReadInt(ca), bit = MEMORY:ReadInt(ca + 0x4), sz = MEMORY:ReadInt(ca + 0xc) }
    local d = fmeta and fmeta[c.shortname]
    c.name, c.depth, c.min = d and d.name or nil, d and d.depth or c.sz, d and d.min or 0
    t.cols[i] = c
    ca = ca + 0x10
  end
  return t
end

local function valid(t, r) return (MEMORY:ReadBytes(r + t.size - 1, 1)[1] & 0x80) == 0 end
local function int(c, r)
  if c.type ~= 3 or c.depth <= 0 or c.depth > 32 then return nil end
  local v = MEMORY:ReadQword(r + (c.bit // 8)) >> (c.bit % 8)
  return (v & ((1 << c.depth) - 1)) + c.min
end
local function label(c) return c.name or ("?" .. printable(c.shortname)) end

local ok, err = pcall(function()
  local db = LE.db
  db:LoadMeta()
  local meta = db.meta

  -- 1) Meta'daki kariyer/kiralık/sözleşme tabloları (adı biliniyor mu, kısa adı ne?).
  local known = {}
  for sn, name in pairs(meta.shortname_name_tables_map) do
    if name:find("career") or name:find("loan") or name:find("contract") or name:find("transfer") then
      known[#known + 1] = name .. "=" .. printable(sn)
    end
  end
  table.sort(known)
  say("META", tostring(#known), table.concat(known, ","))

  -- 2) Her veritabanı, her tablo: şema + aranan kimliklerin geçtiği satırlar.
  local d, di = db:GetDBFromService(), 0
  while d and d > 0 do
    di = di + 1
    local tp, ti = db:GetTableFromDB(d), 0
    while tp and tp > 0 do
      ti = ti + 1
      local okT, t = pcall(rawTable, tp, meta)
      if not okT then
        say("TERR", tostring(di), tostring(ti), tostring(t))
      else
        local schema = {}
        for i, c in ipairs(t.cols) do schema[i] = label(c) .. ":" .. c.type .. ":" .. c.bit .. ":" .. c.depth end
        if not t.name then
          say("TABLE", tostring(di), tostring(ti), printable(t.shortname), "-", tostring(t.count), tostring(t.size), table.concat(schema, ","))
        elseif t.name == "playerloans" or t.name == "career_calendar" then
          -- Ölçüm: adsız tablolarda bit genişliği yerine bellekteki sz kullanılıyor; adlı tabloda meta derinliğiyle kıyasla.
          local cal = {}
          for i, c in ipairs(t.cols) do cal[i] = label(c) .. ":sz=" .. c.sz .. ":depth=" .. c.depth .. ":min=" .. c.min end
          say("CAL", tostring(di), t.name, tostring(t.count), table.concat(cal, ","))
        end
        -- Kimlik taşıyabilecek sütunlar: tamsayı, en az 17 bit (72170 > 2^16).
        local idcols = {}
        for i, c in ipairs(t.cols) do if c.type == 3 and c.depth >= 17 and c.depth <= 32 then idcols[#idcols + 1] = i end end
        if #idcols > 0 and t.count > 0 and t.first > 0 then
          local hits, dumped = {}, 0
          for k = 0, t.count - 1 do
            local r = t.first + t.size * k
            if valid(t, r) then
              local hit = nil
              for _, i in ipairs(idcols) do local v = int(t.cols[i], r) if v and WANT[v] then hit = i break end end
              if hit then
                local col = label(t.cols[hit])
                hits[col] = (hits[col] or 0) + 1
                if dumped < MAX_ROWS_PER_TABLE and #t.cols <= 60 then
                  dumped = dumped + 1
                  local vals = {}
                  for i, c in ipairs(t.cols) do local v = int(c, r) vals[i] = label(c) .. "=" .. (v and tostring(v) or "-") end
                  say("ROW", tostring(di), printable(t.shortname), t.name or "-", table.concat(vals, ","))
                end
              end
            end
          end
          local hs = {}
          for col, n in pairs(hits) do hs[#hs + 1] = col .. ":" .. n end
          if #hs > 0 then say("HITS", tostring(di), printable(t.shortname), t.name or "-", tostring(t.count), table.concat(hs, ",")) end
        end
      end
      tp = db:GetNextTable(tp)
    end
    say("DB", tostring(di), tostring(ti))
    d = db:GetNextDB(d)
  end
end)

say("DONE", ok and "ok" or tostring(err))
MessageBox("DB teşhisi", ok and "Bitti. Log dosyasını gönder (DBPROBE satırları)." or ("Hata: " .. tostring(err)))
