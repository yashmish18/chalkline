#!/usr/bin/env node
// floor-guard.mjs — diff-scoped enforcement of the CONSTRAINTS.md floor.
import { execFileSync } from 'node:child_process';

const base = (() => {
  const i = process.argv.indexOf('--base');
  return i > -1 ? process.argv[i + 1] : 'HEAD';
})();

const git = (args, { diffExit = false } = {}) => {
  try { return execFileSync('git', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }); }
  catch (e) { return diffExit && e.status === 1 && typeof e.stdout === 'string' ? e.stdout : null; }
};

const bail = (msg) => { console.error('floor-guard: ' + msg); process.exit(2); };

// Detect diff against base or working tree
const tracked = git(['diff', '--unified=0', base, '--']);
if (tracked === null) bail('could not diff against ' + base);

const untrackedFiles = git(['ls-files', '--others', '--exclude-standard']);
const untracked = (untrackedFiles || '').split('\n').filter(Boolean).map((f) => {
  const d = git(['diff', '--no-index', '--unified=0', '/dev/null', f], { diffExit: true });
  return d || '';
}).join('\n');

const diff = (tracked || '') + '\n' + untracked;

const added = [], removed = [], deleted = [];
const pathOf = (s) => s.replace(/^[ab]\//, '');
let file = '', oldFile = '';

for (const line of diff.split('\n')) {
  if (line.startsWith('--- ')) oldFile = pathOf(line.slice(4));
  else if (line.startsWith('+++ ')) {
    const newFile = pathOf(line.slice(4));
    file = newFile === '/dev/null' ? oldFile : newFile;
    if (newFile === '/dev/null') deleted.push(file);
  }
  else if (line.startsWith('+') && !line.startsWith('+++')) added.push({ file, text: line.slice(1) });
  else if (line.startsWith('-') && !line.startsWith('---')) removed.push({ file, text: line.slice(1) });
}

const findings = [];
const flag = (rule, f, text) => findings.push({ rule, file: f, text: text.trim().slice(0, 120) });
const isTest = (f) => /\.(test|spec)\.|_test\.|Test\.java$/.test(f);
const isConstraints = (f) => /CONSTRAINTS\.md$/.test(f);
const isDocOrScript = (f) => /\.md$/.test(f) || /floor-guard\.mjs$/.test(f);

// 1. Silenced checker
const SUPPRESSIONS = /@ts-ignore|@ts-nocheck|eslint-disable|biome-ignore|# *noqa|istanbul ignore|nosemgrep|gitleaks:allow/;
// 2. Unfinished work
const STUBS = /throw new (Error|NotImplemented|UnsupportedOperationException).*[Nn]ot implemented|catch\s*\([^\)]*\)\s*\{\s*\}|\bTODO\b/;
// 3. A test made easier
const SKIPS = /\.(skip|todo)\b|\bxit\(|\bxdescribe\(|@Disabled\b|@Ignore\b/;
// 4. Secrets pattern check
const SECRETS = /(bearer\s+[a-zA-Z0-9_\-\.]{25,}|ghp_[a-zA-Z0-9]{36}|client_secret\s*=\s*['"][a-zA-Z0-9_\-\.]{10,}['"])/i;

for (const { file, text } of added) {
  if (isDocOrScript(file)) continue;
  if (SUPPRESSIONS.test(text)) flag('silenced-checker', file, text);
  if (STUBS.test(text)) flag('unfinished-work', file, text);
  if (SKIPS.test(text) && !text.includes('// reason:')) flag('test-made-easier', file, text);
  if (SECRETS.test(text)) flag('secret-detected', file, '[REDACTED SECRET PATTERN]');
}

for (const f of deleted) {
  if (isTest(f)) flag('test-deleted', f, 'Test file was deleted');
}

if (findings.length > 0) {
  console.error('\n❌ FLOOR GUARD FAILED: Found quality constraint violations in diff:');
  for (const f of findings) {
    console.error(`  - [${f.rule}] in ${f.file}: ${f.text}`);
  }
  process.exit(1);
} else {
  console.log('✅ Floor guard passed: No suppressions, stubs, secret leaks, or weakened tests found.');
  process.exit(0);
}
