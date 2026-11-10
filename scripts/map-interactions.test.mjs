import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {test} from 'node:test';

const html = readFileSync(new URL('../app/src/main/assets/map.html', import.meta.url), 'utf8');
const context = vm.createContext({});
for (const section of ['STOP_GROUPS', 'STOP_SELECTION']) {
  vm.runInContext(html.split(`/* ${section}_START */`)[1].split(`/* ${section}_END */`)[0], context);
}
const stop = (id, name, x, y = 0) => ({id, name, x, y});
const distance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

test('grouping preserves identities, normalizes whitespace, and is independent of feed order', () => {
  const items = [stop('b', ' Olof  Palme sétány ', 100), stop('a', 'Olof Palme sétány', 0)];
  const groups = context.groupMapStops(items, distance);
  assert.equal(groups.length, 1);
  assert.equal(groups[0].map(s => s.id).join(','), 'a,b');
  assert.equal(JSON.stringify(groups), JSON.stringify(context.groupMapStops(items.reverse(), distance)));
});

test('different names, distant namesakes, and long chains stay separate', () => {
  const groups = context.groupMapStops([
    stop('a', 'Megálló', 0), stop('b', 'Megálló', 250),
    stop('c', 'Megálló', 500), stop('d', 'Másik megálló', 5),
  ], distance);
  assert.equal(groups.length, 3);
  assert.equal(groups[0].map(s => s.id).join(','), 'a,b');
});

test('enclosure contains the full padded extent for pairs and interchanges', () => {
  for (const points of [[{x:0,y:0},{x:100,y:0}], [{x:0,y:0},{x:100,y:20},{x:40,y:100}]]) {
    const outline = context.mapStopOutline(points);
    assert.equal(Math.min(...outline.map(p => p.x)), -18);
    assert.equal(Math.max(...outline.map(p => p.x)), 118);
    assert.equal(Math.min(...outline.map(p => p.y)), -18);
    for (const p of points) for (let i = 0; i < outline.length; i++) {
      const a = outline[i], b = outline[(i + 1) % outline.length];
      assert.ok((b.x-a.x)*(p.y-a.y)-(b.y-a.y)*(p.x-a.x) >= -1e-8);
    }
  }
});

test('street-level selection reveals nearby platforms while suppressing coincident dots', () => {
  const items = [stop('a', 'Megálló', 0), stop('b', 'Megálló', 30), stop('c', 'Megálló', 2)];
  assert.equal(context.selectMapStops(items, s => s, 24).map(s => s.id).join(','), 'a,b');
});

test('one selected member reveals all group members including collision-suppressed platforms', () => {
  const items = [stop('a', 'Megálló', 0), stop('b', 'Megálló', 2), stop('c', 'Megálló', 30)];
  const groups = context.visibleMapStopGroups(items, s => s, distance, () => true);
  assert.equal(groups.length, 1);
  assert.equal(groups.flat().map(s => s.id).join(','), 'a,b,c');
});

test('visible group stays complete when the selected member is outside the viewport', () => {
  const items = [stop('a', 'Megálló', 0), stop('b', 'Megálló', 2)];
  const groups = context.visibleMapStopGroups(items, s => s, distance, s => s.id === 'b');
  assert.equal(groups.flat().map(s => s.id).join(','), 'a,b');
  assert.equal(context.visibleMapStopGroups(items, s => s, distance, () => false).length, 0);
});

test('fully suppressed groups stay hidden and distant namesakes are not revealed', () => {
  const items = [stop('a', 'Másik megálló', 0), stop('b', 'Megálló', 2), stop('c', 'Megálló', 3), stop('d', 'Megálló', 500)];
  const groups = context.visibleMapStopGroups(items, s => s, distance, s => s.x < 100);
  assert.equal(groups.flat().map(s => s.id).join(','), 'a');
});
