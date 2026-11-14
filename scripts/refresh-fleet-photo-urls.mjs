// Refresh only manually reviewed Commons files. Never choose images by model-name search at runtime.
import fs from 'node:fs';
const file = new URL('../app/src/main/assets/vehicle_models/catalog.json', import.meta.url);
const catalog = JSON.parse(fs.readFileSync(file, 'utf8'));
if (catalog.some(profile => profile.photos.length !== 1)) throw new Error('Every published profile must have exactly one reviewed photo');
const title = source => decodeURIComponent(new URL(source).pathname.split('/wiki/')[1]).replaceAll('_', ' ');
const photos = catalog.flatMap(p => p.photos);
const titles = [...new Set(photos.map(p => title(p.source)))];
const clean = value => value.replace(/<[^>]*>/g, '').replace(/&amp;/g, '&').replace(/&quot;/g, '"')
  .replace(/&#39;/g, "'").replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n))).trim();
for (let start = 0; start < titles.length; start += 25) {
  const batch = titles.slice(start, start + 25);
  const params = new URLSearchParams({action: 'query', format: 'json', titles: batch.join('|'),
    redirects: '1', prop: 'imageinfo', iiprop: 'url|size|extmetadata', iiurlwidth: '1280'});
  const response = await fetch(`https://commons.wikimedia.org/w/api.php?${params}`, {
    headers: {'User-Agent': 'MenetrendGO fleet catalogue maintenance/1.0'}, signal: AbortSignal.timeout(30000),
  });
  if (!response.ok) throw new Error(`Commons HTTP ${response.status}`);
  const body = await response.json();
  if (body.error) throw new Error(JSON.stringify(body.error));
  const mappings = new Map([...(body.query.normalized ?? []), ...(body.query.redirects ?? [])].map(n => [n.from, n.to]));
  const pages = Object.values(body.query.pages);
  for (const original of batch) {
    let canonical = original;
    const seen = new Set();
    while (mappings.has(canonical) && !seen.has(canonical)) { seen.add(canonical); canonical = mappings.get(canonical); }
    const info = pages.find(p => p.title === canonical)?.imageinfo?.[0];
    if (!info) throw new Error(`Missing reviewed file: ${original}`);
    const meta = info.extmetadata;
    const license = meta.LicenseShortName?.value ?? '';
    if (!/^(CC BY(?:-SA)? [234]\.0|Public domain|CC0)$/.test(license)) throw new Error(`Review licence: ${original}: ${license}`);
    const url = new URL(info.thumburl ?? info.url);
    if (url.protocol !== 'https:' || !['upload.wikimedia.org', 'thumb.wikimedia.org'].includes(url.hostname)) throw new Error(`Unexpected image host: ${url}`);
    url.search = '';
    for (const photo of photos.filter(p => title(p.source) === original)) {
      photo.url = url.href;
      photo.width = info.width;
      photo.height = info.height;
      photo.license = license;
      photo.licenseUrl = (meta.LicenseUrl?.value || (license === 'Public domain' ? 'https://creativecommons.org/publicdomain/mark/1.0/' : '')).replace(/^http:/, 'https:').replace(/^\/\//, 'https://');
      photo.author ||= clean(meta.Artist?.value ?? '');
      if (!photo.author || !photo.licenseUrl.startsWith('https://')) throw new Error(`Incomplete credit: ${original}`);
      delete photo.asset;
    }
  }
  console.log(`Verified ${Math.min(start + batch.length, titles.length)}/${titles.length} Commons files`);
}
fs.writeFileSync(file, JSON.stringify(catalog, null, 2) + '\n');
console.log(`${photos.length} photo references updated. Review the diff before shipping.`);
