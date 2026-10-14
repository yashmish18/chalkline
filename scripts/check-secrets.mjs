#!/usr/bin/env node
// check-secrets.mjs — repo-wide scan for committed credentials (CONSTRAINTS.md "Secrets" row).
// Scans every tracked and untracked-but-not-ignored text file. Exit 1 on any finding.
import { execFileSync } from 'node:child_process';
import { readFileSync, statSync } from 'node:fs';

const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard'], {
  encoding: 'utf8',
  maxBuffer: 64 * 1024 * 1024,
})
  .split('\n')
  .filter(Boolean)
  .filter((f) => !/\.(xlsx|xls|pdf|docx?|png|jpe?g|gif|ico|woff2?|lock)$/i.test(f))
  .filter((f) => !f.startsWith('graphify-out/') && f !== 'scripts/check-secrets.mjs');

const RULES = [
  ['private key', /-----BEGIN [A-Z ]*PRIVATE KEY-----/],
  ['AWS access key', /\bAKIA[0-9A-Z]{16}\b/],
  ['GitHub token', /\bgh[pousr]_[A-Za-z0-9]{36,}\b/],
  ['Slack token', /\bxox[abprs]-[A-Za-z0-9-]{10,}\b/],
  ['Google API key', /\bAIza[0-9A-Za-z_-]{35}\b/],
  ['bearer token', /bearer\s+[A-Za-z0-9_\-.]{25,}/i],
  // A literal value for a secret-looking key. ${ENV} placeholders and empty values are fine.
  ['hard-coded secret', /\b(client[_-]?secret|api[_-]?key|secret[_-]?key|password|passwd|token)\b\s*[:=]\s*["']?(?!\$\{)(?!["']?\s*$)[A-Za-z0-9_\-./+=!@#%^&*]{12,}/i],
];
// In source code a secret is a quoted string literal; `token = readToken()` is a call, not a secret.
const CODE = /\.(m?[jt]sx?|java|py)$/;
const CODE_SECRET = /\b(client[_-]?secret|api[_-]?key|secret[_-]?key|password|passwd|token)\b\s*[:=]\s*["'`](?!\$\{)[^"'`\s]{12,}["'`]/i;

const findings = [];
for (const file of files) {
  let text;
  try {
    if (statSync(file).size > 2 * 1024 * 1024) continue;
    text = readFileSync(file, 'utf8');
  } catch {
    continue; // deleted in the working tree, or unreadable
  }
  text.split('\n').forEach((line, i) => {
    for (const [name, pattern] of RULES) {
      const rule = name === 'hard-coded secret' && CODE.test(file) ? CODE_SECRET : pattern;
      if (rule.test(line)) findings.push(`${file}:${i + 1}  ${name}`);
    }
  });
}

if (findings.length) {
  console.error('❌ Possible secrets found (values not printed):');
  findings.forEach((f) => console.error('  ' + f));
  console.error('Move them to environment variables (see application.yml ${...} placeholders).');
  process.exit(1);
}
console.log(`✅ No secrets found in ${files.length} files.`);
