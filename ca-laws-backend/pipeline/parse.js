const fs = require('fs');
const path = require('path');
const { XMLParser } = require('fast-xml-parser');

// Regular (grouped) parser — good for Identification metadata where order doesn't matter.
const metaParser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: '@_',
  textNodeName: '#text',
});

// Order-preserving parser — required for Body, since Heading/Section siblings
// get grouped by tag name under a normal parse, destroying document order.
const orderedParser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: '@_',
  textNodeName: '#text',
  preserveOrder: true,
});

function clean(s) {
  return (s || '').replace(/\s+/g, ' ').trim();
}

// Recursively flatten a normal (non-ordered) parse node's text content.
function extractText(node) {
  if (node == null) return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(extractText).join(' ');
  if (typeof node === 'object') {
    let out = [];
    if (node['#text']) out.push(node['#text']);
    for (const key of Object.keys(node)) {
      if (key === '#text' || key.startsWith('@_')) continue;
      out.push(extractText(node[key]));
    }
    return out.join(' ');
  }
  return '';
}

// Flatten a preserveOrder-style node (array of {tagName: [...], ':@': {...}}) to text.
function extractOrderedText(node) {
  if (node == null) return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) {
    let out = [];
    for (const entry of node) {
      if (entry['#text'] !== undefined) {
        out.push(String(entry['#text']));
        continue;
      }
      for (const key of Object.keys(entry)) {
        if (key === ':@') continue;
        out.push(extractOrderedText(entry[key]));
      }
    }
    return out.join(' ');
  }
  return '';
}

// Find the first child element with a given tag name in a preserveOrder node array.
function findChild(orderedArr, tagName) {
  if (!Array.isArray(orderedArr)) return null;
  for (const entry of orderedArr) {
    if (entry[tagName] !== undefined) return entry[tagName];
  }
  return null;
}

// Walk the Body (ordered form), pulling out a flat list of {label, marginal_note, heading_path, text}
// in true document order, tracking heading hierarchy by level.
function walkOrderedBody(bodyArr, sections, headingStack) {
  if (!Array.isArray(bodyArr)) return;
  for (const entry of bodyArr) {
    if (entry.Heading !== undefined) {
      const attrs = entry[':@'] || {};
      const level = Number(attrs['@_level'] || '1');
      const titleTextNode = findChild(entry.Heading, 'TitleText');
      const titleText = clean(extractOrderedText(titleTextNode));
      while (headingStack.length && headingStack[headingStack.length - 1].level >= level) {
        headingStack.pop();
      }
      headingStack.push({ level, text: titleText });
    } else if (entry.Section !== undefined) {
      const labelNode = findChild(entry.Section, 'Label');
      const marginalNode = findChild(entry.Section, 'MarginalNote');
      const label = clean(extractOrderedText(labelNode));
      const marginalNote = clean(extractOrderedText(marginalNode));
      const text = clean(extractOrderedText(entry.Section));
      const heading_path = headingStack.map(h => h.text).join(' > ');
      sections.push({ label, marginal_note: marginalNote, heading_path, text });
    }
    // Other top-level containers (e.g. Part wrappers) — recurse if they hold nested arrays
    for (const key of Object.keys(entry)) {
      if (key === ':@' || key === 'Heading' || key === 'Section') continue;
      if (Array.isArray(entry[key])) {
        walkOrderedBody(entry[key], sections, headingStack);
      }
    }
  }
}

// Orders, proclamations and similar instruments keep their text in <Order><Provision>
// rather than <Body><Section>; each top-level Provision becomes one section.
function walkOrder(orderArr, sections) {
  if (!Array.isArray(orderArr)) return;
  const headingStack = [];
  for (const entry of orderArr) {
    if (entry.Heading !== undefined) {
      const level = Number((entry[':@'] || {})['@_level'] || '1');
      const titleText = clean(extractOrderedText(findChild(entry.Heading, 'TitleText')));
      while (headingStack.length && headingStack[headingStack.length - 1].level >= level) headingStack.pop();
      headingStack.push({ level, text: titleText });
    } else if (entry.Provision !== undefined) {
      const text = clean(extractOrderedText(entry.Provision));
      if (!text) continue;
      sections.push({
        label: clean(extractOrderedText(findChild(entry.Provision, 'Label'))),
        marginal_note: '',
        heading_path: headingStack.map(h => h.text).join(' > '),
        text,
      });
    }
  }
}

function parseActFile(filePath) {
  const xml = fs.readFileSync(filePath, 'utf-8');

  // Metadata via normal parser (order doesn't matter here)
  const metaJson = metaParser.parse(xml);
  const metaRoot = metaJson.Statute || metaJson.Regulation;
  if (!metaRoot) return null;
  const isRegulation = !!metaJson.Regulation;
  const ident = metaRoot.Identification || {};
  const shortTitle = clean(extractText(ident.ShortTitle)) || clean(extractText(ident.LongTitle));
  const longTitle = clean(extractText(ident.LongTitle));
  const consolidatedNumber = clean(extractText(ident.Chapter?.ConsolidatedNumber)) ||
    clean(extractText(ident.InstrumentNumber));

  // Body via order-preserving parser
  const orderedJson = orderedParser.parse(xml);
  // preserveOrder wraps everything as an array of {tagName: [...]} at each level
  const rootTag = isRegulation ? 'Regulation' : 'Statute';
  const rootArr = findChild(orderedJson, rootTag);
  const rootAttrs = (orderedJson.find(e => e[rootTag] !== undefined) || {})[':@'] || {};
  const bodyArr = findChild(rootArr, 'Body');

  const sections = [];
  walkOrderedBody(bodyArr, sections, []);
  if (sections.length === 0) walkOrder(findChild(rootArr, 'Order'), sections);

  return {
    id: path.basename(filePath, '.xml'),
    type: isRegulation ? 'regulation' : 'act',
    consolidated_number: consolidatedNumber,
    short_title: shortTitle,
    long_title: longTitle,
    in_force_start: rootAttrs['@_lims:inforce-start-date'] || null,
    last_amended: rootAttrs['@_lims:lastAmendedDate'] || null,
    current_to: rootAttrs['@_lims:current-date'] || null,
    section_count: sections.length,
    sections,
  };
}

module.exports = { parseActFile };
