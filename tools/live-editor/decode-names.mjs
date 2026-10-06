// Live Editor 'playernames' tablosunun sıkıştırılmış isimlerini çözer.
// Girdi : fc27_names_rawdump.lua'nın log'a yazdığı NAMERAW satırları (Logs/live_editor_<tarih>.log)
// Çıktı : FCDUMP biçiminde 'playernames' bloğu (nameid,name). Bu dosya, base/kariyer log'unun SONUNA eklenip
//         birlikte yüklenir (import son BEGIN..END bloğunu kullanır).
//
// Biçim (45 bin isim için doğrulandı, katalogda adı bilinen 19 bin oyuncuyla):
//  - Tablo sonrası bellekte: 24 bayt başlık, sonra Huffman ağacı (138 düğüm, her düğüm iki u16; >=256 yaprak = bayt<<8).
//  - Her kaydın 'key'i, ağaçtan sonraki bit akışında dizinin başladığı bayt konumudur (akış tabanı = AFTER[25]).
//  - Dizi: Huffman kodlu UTF-8 baytlar, sıfır bitle hizalama, son 9 bit: 'd' kodu (00000) + 4 bit sabit sonluk.
//  - Hizalama sıfırları 'd' (00000) olarak çözülebildiği için sondaki tek 'd' belirsizdir; kalan bit sayısı ve
//    çözülmüş diğer isimlerdeki sıklık ile çözülür.
import fs from 'node:fs'

const [, , logPath, outPath, knownPath] = process.argv
const NL = String.fromCharCode(10)
const known = new Set(knownPath ? fs.readFileSync(knownPath, 'utf8').split(NL).map((l) => l.trim()).filter(Boolean) : [])
if (!logPath || !outPath) {
  console.error('kullanım: node decode-names.mjs <log> <çıktı>')
  process.exit(1)
}

const parts = { RECORDS: [], AFTER: [] }
const lines = fs.readFileSync(logPath, 'latin1').split('\n')
let lastInfo = -1
lines.forEach((l, i) => {
  if (l.includes('NAMERAW') && l.split('\t')[3] === 'INFO') lastInfo = i
})
for (const line of lines.slice(lastInfo)) {
  if (!line.includes('NAMERAW')) continue
  const p = line.replace(/\r$/, '').split('\t')
  if (parts[p[3]]) parts[p[3]].push(Buffer.from(p[5], 'hex'))
}
const records = Buffer.concat(parts.RECORDS)
const after = Buffer.concat(parts.AFTER)
if (records.length === 0 || after.length === 0) throw new Error('NAMERAW verisi bulunamadı')

const BASE = 25
const TRAILER_BITS = 9
const D = 100
const NO_KEY = 0xffffffff

const keyById = new Map()
for (let i = 0; i < records.length; i += 12) keyById.set(records.readUInt16LE(i + 4), records.readUInt32LE(i))
const maxId = Math.max(...keyById.keys())

const entries = []
for (let o = 24; entries.length < 2 * 400 && o + 2 <= after.length; o += 2) entries.push(after.readUInt16LE(o))
let nodeCount = 0
const seen = new Set()
const stack = [0]
while (stack.length) {
  const n = stack.pop()
  if (seen.has(n)) continue
  seen.add(n)
  nodeCount = Math.max(nodeCount, n + 1)
  for (const c of [entries[2 * n], entries[2 * n + 1]]) if (c < 0x100 && c !== 0) stack.push(c)
}

const bitAt = (pos) => (after[pos >> 3] >> (7 - (pos & 7))) & 1

function nextKey(id) {
  const key = keyById.get(id)
  for (let i = id + 1; i <= maxId; i++) {
    const k = keyById.get(i)
    if (k !== undefined && k !== NO_KEY && k > key) return k
  }
  return key + 24
}

function symbols(start, end) {
  let node = 0
  const out = []
  let last = start
  for (let b = start; b < end; b++) {
    const next = entries[2 * node + bitAt(b)]
    if (next >= 256) {
      out.push(next >> 8)
      node = 0
      last = b + 1
    } else node = next
  }
  return { out, leftover: end - last }
}

const text = (bytes) => Buffer.from(bytes).toString('utf8')

function candidates(id) {
  const key = keyById.get(id)
  if (key === undefined || key === NO_KEY) return []
  const start = (key + BASE) * 8
  const end = (nextKey(id) + BASE) * 8 - TRAILER_BITS
  const { out, leftover } = symbols(start, end)
  if (out[out.length - 1] !== D || leftover >= 3) return [text(out)]
  return [text(out.slice(0, -1)), text(out)]
}

const raw = new Map()
const frequency = new Map()
for (const id of keyById.keys()) {
  const c = candidates(id)
  if (c.length === 0) continue
  raw.set(id, c)
  if (c.length === 1) frequency.set(c[0], (frequency.get(c[0]) ?? 0) + 1)
}

// Belirsiz sondaki 'd': hizalama sıfırları 'd' gibi çözülür. Kalan bit sayısına bakınca iki durum (gerçek 'd' / dolgu)
// eşit olasılıklıdır; isimlerin çok azı 'd' ile bittiği için varsayılan 'd'siz seçilir. Bilinen sözcük listesi
// (katalogdaki adlar, isteğe bağlı 3. argüman) ve kesin çözülmüş diğer isimler 'd'li hali doğrularsa o seçilir.
const rows = []
for (const [id, c] of raw) {
  let chosen = c[0]
  if (c.length > 1) {
    const [plain, withD] = c
    const withDScore = (frequency.get(withD) ?? 0) + (known.has(withD) ? 100 : 0)
    const plainScore = (frequency.get(plain) ?? 0) + (known.has(plain) ? 100 : 0)
    chosen = withDScore > plainScore ? withD : plain
  }
  if (chosen.length > 0 && !/[\u0000-\u0008]/.test(chosen)) rows.push([id, chosen])
}

const stamp = '00:00:00.000000\tINFO\t[LUA API] FCDUMP\t'
const csv = (v) => (/[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v)
const out = [`${stamp}BEGIN\tplayernames\tfields=2\trecords=${rows.length}\tvia=decoded`, `${stamp}HEADER\tplayernames\tnameid,name`]
for (const [id, name] of rows) out.push(`${stamp}ROW\tplayernames\t${id},${csv(name)}`)
out.push(`${stamp}END\tplayernames\t${rows.length}`)
fs.writeFileSync(outPath, out.join('\n') + '\n')
console.log(`nodes=${nodeCount} names=${rows.length} (belirsiz sondaki 'd': ${[...raw.values()].filter((c) => c.length > 1).length})`)
