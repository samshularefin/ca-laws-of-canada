const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');
const { parseActFile } = require('./parse');

const REPO = process.env.LAWS_XML_REPO || path.join(__dirname, 'laws-lois-xml');
const OUT_DB = process.argv[2] || path.join(__dirname, 'ca_laws.db');
const LANG = process.argv[3] || 'eng'; // 'eng' or 'fra'

if (fs.existsSync(OUT_DB)) fs.unlinkSync(OUT_DB);
const db = new Database(OUT_DB);

db.pragma('journal_mode = WAL');

db.exec(`
CREATE TABLE documents (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL,               -- 'act' | 'regulation'
  consolidated_number TEXT,
  short_title TEXT,
  long_title TEXT,
  in_force_start TEXT,
  last_amended TEXT,
  current_to TEXT,
  section_count INTEGER
);

CREATE TABLE sections (
  doc_id TEXT NOT NULL,
  seq INTEGER NOT NULL,
  label TEXT,
  marginal_note TEXT,
  heading_path TEXT,
  text TEXT,
  PRIMARY KEY (doc_id, seq),
  FOREIGN KEY (doc_id) REFERENCES documents(id)
);

CREATE VIRTUAL TABLE documents_fts USING fts5(
  id UNINDEXED, short_title, long_title, content='documents', content_rowid='rowid'
);

CREATE VIRTUAL TABLE sections_fts USING fts5(
  doc_id UNINDEXED, seq UNINDEXED, marginal_note, text
);

CREATE INDEX idx_sections_doc ON sections(doc_id);
`);

const insertDoc = db.prepare(`
  INSERT INTO documents (id, type, consolidated_number, short_title, long_title, in_force_start, last_amended, current_to, section_count)
  VALUES (@id, @type, @consolidated_number, @short_title, @long_title, @in_force_start, @last_amended, @current_to, @section_count)
`);
const insertDocFts = db.prepare(`INSERT INTO documents_fts (rowid, id, short_title, long_title) VALUES (?, ?, ?, ?)`);
const insertSection = db.prepare(`
  INSERT INTO sections (doc_id, seq, label, marginal_note, heading_path, text)
  VALUES (@doc_id, @seq, @label, @marginal_note, @heading_path, @text)
`);
const insertSectionFts = db.prepare(`INSERT INTO sections_fts (doc_id, seq, marginal_note, text) VALUES (?, ?, ?, ?)`);

function processDir(dirPath) {
  const files = fs.readdirSync(dirPath).filter(f => f.endsWith('.xml'));
  let ok = 0, failed = 0, skippedEmpty = 0;
  const txn = db.transaction((batch) => {
    for (const { parsed, rowid } of batch) {
      insertDoc.run({
        id: parsed.id,
        type: parsed.type,
        consolidated_number: parsed.consolidated_number,
        short_title: parsed.short_title,
        long_title: parsed.long_title,
        in_force_start: parsed.in_force_start,
        last_amended: parsed.last_amended,
        current_to: parsed.current_to,
        section_count: parsed.section_count,
      });
      insertDocFts.run(rowid, parsed.id, parsed.short_title, parsed.long_title);
      parsed.sections.forEach((s, i) => {
        insertSection.run({ doc_id: parsed.id, seq: i, label: s.label, marginal_note: s.marginal_note, heading_path: s.heading_path, text: s.text });
        insertSectionFts.run(parsed.id, i, s.marginal_note, s.text);
      });
    }
  });

  let rowid = 1;
  let batch = [];
  for (const file of files) {
    const filePath = path.join(dirPath, file);
    try {
      const parsed = parseActFile(filePath);
      if (!parsed || !parsed.short_title) { failed++; continue; }
      if (parsed.section_count === 0) { skippedEmpty++; continue; }
      batch.push({ parsed, rowid: rowid++ });
      ok++;
      if (batch.length >= 200) { txn(batch); batch = []; }
    } catch (e) {
      failed++;
      console.error(`FAILED ${file}: ${e.message}`);
    }
  }
  if (batch.length) txn(batch);
  return { ok, failed, skippedEmpty, total: files.length };
}

console.log(`Building DB (${LANG}) -> ${OUT_DB}`);
const actsRes = processDir(path.join(REPO, LANG, 'acts'));
console.log('Acts:', actsRes);
const regsRes = processDir(path.join(REPO, LANG, 'regulations'));
console.log('Regulations:', regsRes);

const totalDocs = db.prepare('SELECT COUNT(*) c FROM documents').get().c;
const totalSections = db.prepare('SELECT COUNT(*) c FROM sections').get().c;
console.log(`Done. ${totalDocs} documents, ${totalSections} sections.`);
console.log('DB size:', (fs.statSync(OUT_DB).size / (1024*1024)).toFixed(1), 'MB');

db.close();
