import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'app/src/main/assets/vehicle_models');
const catalog = JSON.parse(fs.readFileSync(path.join(assets, 'catalog.json'), 'utf8').replace(/^\uFEFF/, ''));
const normalize = s => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9]/g, '');
const aliases = new Map();
const errors = [];
for (const profile of catalog) {
  if (!profile.description?.trim() || !profile.facts?.length || !profile.sources?.length) errors.push(`${profile.id}: incomplete article`);
  for (const alias of profile.aliases) {
    const key = normalize(alias);
    if (aliases.has(key)) errors.push(`Ambiguous alias: ${alias}`);
    aliases.set(key, profile.id);
  }
  const photoSources = new Set();
  for (const photo of profile.photos) {
    const identity = decodeURIComponent(photo.source ?? '').replaceAll('_', ' ');
    if (photoSources.has(identity)) errors.push(`${profile.id}: duplicate photo source`);
    photoSources.add(identity);
    const file = path.join(assets, photo.asset);
    if (!fs.existsSync(file) || fs.statSync(file).size < 1000) errors.push(`${profile.id}: missing photo ${photo.asset}`);
    for (const field of ['author', 'caption', 'source', 'license', 'licenseUrl']) {
      if (!photo[field]?.trim()) errors.push(`${profile.id}: missing photo ${field}`);
    }
    if (!photo.source.startsWith('https://commons.wikimedia.org/wiki/File:')) errors.push(`${profile.id}: unreviewed source`);
    if (!/^(CC BY(?:-SA)? [234]\.0|Public domain|CC0)/.test(photo.license)) errors.push(`${profile.id}: unreviewed license ${photo.license}`);
  }
}
const reported = fs.readFileSync(path.join(root, 'app/src/test/resources/vehicle-models-2026-10-24.txt'), 'utf8')
  .replace(/^\uFEFF/, '').trim().split(/\r?\n/);
for (const name of reported) if (!aliases.has(normalize(name))) errors.push(`Uncovered reported model: ${name}`);
const pending = catalog.filter(p => !p.photos.length);
console.log(`${catalog.length} profiles, ${reported.length} reported names, ${catalog.reduce((n, p) => n + p.photos.length, 0)} photos.`);
console.log(`${catalog.filter(p => p.photos.length >= 3).length} profiles have at least three distinct photos.`);
if (pending.length) console.log(`Photos still required before release (${pending.length}):\n${pending.map(p => `- ${p.title}`).join('\n')}`);
if (process.argv.includes('--release')) errors.push(...pending.map(p => `${p.id}: release requires a verified, freely licensed fleet photo`));
if (errors.length) { console.error(errors.join('\n')); process.exitCode = 1; }
else console.log('Catalog integrity passed.');
