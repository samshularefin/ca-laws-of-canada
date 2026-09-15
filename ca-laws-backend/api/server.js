const express = require('express');
const cors = require('cors');
const Database = require('better-sqlite3');
const path = require('path');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '..', '..', 'ca_laws.db', 'ca_laws.db');
const PORT = process.env.PORT || 3000;

const db = new Database(DB_PATH, { readonly: true, fileMustExist: true });
db.pragma('journal_mode = WAL');

const app = express();
app.use(cors());
app.use(express.json());

// Escape a raw user query for FTS5 MATCH: wrap in quotes, prefix-match the last token.
function ftsQuery(q) {
  const cleaned = q.replace(/["]/g, ' ').trim();
  if (!cleaned) return null;
  const tokens = cleaned.split(/\s+/).map(t => `"${t}"`);
  return tokens.join(' ') + '*';
}

// GET /api/documents?type=act|regulation&page=1&pageSize=50
app.get('/api/documents', (req, res) => {
  const { type, page = 1, pageSize = 50 } = req.query;
  const p = Math.max(1, parseInt(page));
  const size = Math.min(200, Math.max(1, parseInt(pageSize)));
  const offset = (p - 1) * size;

  let where = '1=1';
  const params = {};
  if (type === 'act' || type === 'regulation') {
    where = 'type = @type';
    params.type = type;
  }

  const rows = db.prepare(`
    SELECT id, type, consolidated_number, short_title, long_title, in_force_start, last_amended, current_to, section_count
    FROM documents WHERE ${where}
    ORDER BY short_title
    LIMIT @size OFFSET @offset
  `).all({ ...params, size, offset });

  const total = db.prepare(`SELECT COUNT(*) c FROM documents WHERE ${where}`).get(params).c;

  res.json({ page: p, pageSize: size, total, results: rows });
});

// GET /api/documents/:id  (full doc with all sections)
app.get('/api/documents/:id', (req, res) => {
  const doc = db.prepare(`SELECT * FROM documents WHERE id = ?`).get(req.params.id);
  if (!doc) return res.status(404).json({ error: 'Not found' });

  const sections = db.prepare(`
    SELECT seq, label, marginal_note, heading_path, text
    FROM sections WHERE doc_id = ? ORDER BY seq
  `).all(req.params.id);

  res.json({ ...doc, sections });
});

// GET /api/documents/:id/sections/:seq  (single section, for deep-linking)
app.get('/api/documents/:id/sections/:seq', (req, res) => {
  const section = db.prepare(`
    SELECT seq, label, marginal_note, heading_path, text
    FROM sections WHERE doc_id = ? AND seq = ?
  `).get(req.params.id, req.params.seq);
  if (!section) return res.status(404).json({ error: 'Not found' });
  res.json(section);
});

// GET /api/search/titles?q=immigration
app.get('/api/search/titles', (req, res) => {
  const q = ftsQuery(req.query.q || '');
  if (!q) return res.json({ results: [] });

  const rows = db.prepare(`
    SELECT d.id, d.type, d.consolidated_number, d.short_title, d.long_title
    FROM documents_fts f
    JOIN documents d ON d.id = f.id
    WHERE documents_fts MATCH ?
    ORDER BY rank
    LIMIT 30
  `).all(q);

  res.json({ results: rows });
});

// GET /api/search/text?q=impaired driving&limit=30
app.get('/api/search/text', (req, res) => {
  const q = ftsQuery(req.query.q || '');
  if (!q) return res.json({ results: [] });
  const limit = Math.min(100, parseInt(req.query.limit) || 30);

  const rows = db.prepare(`
    SELECT s.doc_id, s.seq, s.marginal_note,
           snippet(sections_fts, 3, '[', ']', '...', 20) as snippet,
           d.short_title, d.type
    FROM sections_fts s
    JOIN documents d ON d.id = s.doc_id
    WHERE sections_fts MATCH ?
    ORDER BY rank
    LIMIT ?
  `).all(q, limit);

  res.json({ results: rows });
});

// GET /api/stats
app.get('/api/stats', (req, res) => {
  const totalDocs = db.prepare('SELECT COUNT(*) c FROM documents').get().c;
  const totalSections = db.prepare('SELECT COUNT(*) c FROM sections').get().c;
  const byType = db.prepare('SELECT type, COUNT(*) c FROM documents GROUP BY type').all();
  res.json({ totalDocs, totalSections, byType });
});

app.get('/health', (req, res) => res.json({ ok: true }));

app.listen(PORT, () => console.log(`CA Laws API listening on :${PORT}`));
