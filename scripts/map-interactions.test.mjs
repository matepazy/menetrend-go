import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {test} from 'node:test';

const html = readFileSync(new URL('../app/src/main/assets/map.html', import.meta.url), 'utf8');
const context = vm.createContext({});
for (const section of ['STOP_GROUPS', 'STOP_SELECTION', 'VEHICLE_MOTION']) {
  vm.runInContext(html.split(`/* ${section}_START */`)[1].split(`/* ${section}_END */`)[0], context);
}
const position=(lat,lon)=>({lat,lon});
test('vehicle interpolation follows both sides of a bend without cutting its corner',()=>{
  const path=context.vehiclePath([[19,47],[19.01,47],[19.01,47.01]]);
  const move=context.vehicleMovement(path,position(47,19),position(47.01,19.01));
  assert.ok(move);
  for(let t=0;t<=1;t+=.01){
    const p=context.vehiclePointAt(path,move.start+(move.end-move.start)*t);
    assert.ok(Math.abs(p.lat-47)<1e-9||Math.abs(p.lon-19.01)<1e-9);
  }
  const end=context.vehiclePointAt(path,move.end);
  assert.ok(Math.abs(end.lat-47.01)<1e-9&&Math.abs(end.lon-19.01)<1e-9);
});
test('interrupted updates continue from their displayed point on the route',()=>{
  const path=context.vehiclePath([[19,47],[19.01,47],[19.01,47.01]]);
  const first=context.vehicleMovement(path,position(47,19),position(47.005,19.01));
  const current=context.vehiclePointAt(path,(first.start+first.end)/2);
  const next=context.vehicleMovement(path,current,position(47.01,19.01));
  const start=context.vehiclePointAt(path,next.start);
  assert.ok(Math.hypot(start.lat-current.lat,start.lon-current.lon)<1e-9);
});
test('missing, malformed and zero-length geometry cannot invent a path',()=>{
  for(const input of [null,[],[[19,47]],[[19,47],[19,47]],[[19,47],[NaN,48]],[[19,47],[181,48]]])assert.equal(context.vehiclePath(input),null);
  assert.equal(context.vehicleMovement(null,position(47,19),position(47,20)),null);
});
test('off-route fixes, backwards jitter, stationary points and large corrections snap',()=>{
  const path=context.vehiclePath([[19,47],[19.1,47]]);
  for(const [a,b] of [[position(48,19),position(48,19.01)],[position(47,19.01),position(47,19)],[position(47,19),position(47,19)],[position(47,19),position(47,20)]])assert.equal(context.vehicleMovement(path,a,b),null);
});
test('duplicate vertices and crossings choose a short forward passage rather than a lap',()=>{
  const path=context.vehiclePath([[19,47],[19,47],[19.01,47],[19.01,47.01],[19,47.01],[19,47],[19.01,47]]);
  const move=context.vehicleMovement(path,position(47,19.002),position(47,19.008));
  assert.ok(move&&move.end-move.start<500);
});
const stop = (id, name, x, y = 0) => ({id, name, x, y});
const distance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

test('stop layers survive panning and refreshes, then remove only departed identities', () => {
  const layers = new Set();
  let creates = 0;
  const makeLayer = (position, options = {}) => {
    creates++;
    return {position, options, addTo() { layers.add(this); return this; },
      setLatLng(p) { this.position=p; }, setLatLngs(p) { this.position=p; },
      getElement() { return this; }};
  };
  const sandbox = vm.createContext({
    stopsEnabled:true, stopMinZoom:16, stops:[],
    stopMarkers:{removeLayer(layer) {layers.delete(layer);}},
    map:{getZoom:()=>17, project:([lat,lon])=>({x:lon,y:lat}),
      unproject:([x,y])=>({lat:y,lng:x}), getBounds:()=>({pad:()=>({contains:()=>true})})},
    L:{marker:makeLayer,polygon:makeLayer,divIcon:options=>options},
    document:{createElement:()=>({style:{},append(){}})},
    visibleMapStopGroups:items=>items.length?[items]:[],
    mapStopOutline:points=>points, mapStopBackground:()=> 'blue',
    bindMapActivation:(marker,handler)=>{marker.activate=handler;},
    window:{NativeMap:true}, NativeMap:{selectStop:id=>{sandbox.selected=id;}}
  });
  vm.runInContext(html.slice(html.indexOf('const stopEntries='),html.indexOf('function reportStopViewport')),sandbox);
  sandbox.stops=[{id:'a',name:'Station',lat:47,lon:19},{id:'b',name:'Station',lat:47.001,lon:19}];
  sandbox.drawStops();
  const initial=[...layers];
  assert.equal(creates,4); // Two circles, one outline, one name.
  for(let i=0;i<4;i++) {
    sandbox.stops=sandbox.stops.map(s=>({...s})).reverse();
    sandbox.drawStops();
    assert.equal(creates,4);
    assert.deepEqual([...layers],initial);
  }
  sandbox.stops=[{...sandbox.stops.find(s=>s.id==='a'),name:'Updated',lat:48}];
  sandbox.drawStops();
  assert.equal(layers.size,1);
  const retained=[...layers][0];
  assert.ok(initial.includes(retained));
  assert.deepEqual(Array.from(retained.position),[48,19]);
  assert.equal(retained.options.title,'Updated · indulási tábla');
  retained.activate();
  assert.equal(sandbox.selected,'a');
  sandbox.stopsEnabled=false;sandbox.drawStops();
  assert.equal(layers.size,0);
  sandbox.stopsEnabled=true;sandbox.drawStops();
  assert.equal(layers.size,1);
});

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
