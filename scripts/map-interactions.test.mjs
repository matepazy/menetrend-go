import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const html=readFileSync(new URL('../app/src/main/assets/map.html',import.meta.url),'utf8');
const section=name=>html.split(`/* ${name}_START */`)[1].split(`/* ${name}_END */`)[0];
const palette=readFileSync(new URL('../app/src/main/assets/vehicle-icons.js',import.meta.url),'utf8');
const background=vm.runInNewContext(palette+'\n'+section('STOP_COLORS')+';mapStopBackground');
for(const [mode,color] of Object.entries({RAIL:'#003A7E',BUS:'#009EE3',COACH:'#FDC300',SUBURBAN_RAILWAY:'#005E3B',TRAMTRAIN:'#F29101',RAIL_REPLACEMENT_BUS:'#C4146E'}))
 assert.ok(background({modes:[mode]}).includes(color),mode);
assert.ok(background({modes:['BUS','RAIL','BUS']}).includes('#009EE3 0% 50%,#003A7E 50% 100%'));
assert.ok(background({modes:['UNKNOWN']}).includes('#4e6581'));
assert.ok(background({}).includes('#4e6581'));

function harness(){
 let now=1000,serial=0,zoom=16,enabled=true,selected=0;
 const timers=new Map(),listeners={},moves=[];
 const map={getContainer:()=>({addEventListener:(name,fn,options)=>{listeners[name]=fn;assert.equal(options.capture,true)}}),
  mouseEventToContainerPoint:p=>({x:p.clientX,y:p.clientY,distanceTo:q=>Math.hypot(p.clientX-q.x,p.clientY-q.y)}),
  dragging:{enabled:()=>enabled,disable:()=>enabled=false,enable:()=>enabled=true,_draggable:{finishDrag(){}}},
  getZoom:()=>zoom,getCenter:()=>[47,19],_stop(){},_limitZoom:z=>Math.max(1,Math.min(19,z)),
  _move:(center,z)=>{zoom=z;moves.push(z)},fire(){},_moveStart(){},_moveEnd(){},setZoomAround:(pt,z)=>zoom=z};
 const bind=vm.runInNewContext(section('MAP_GESTURES')+';bindMapActivation',{
  map,Date:{now:()=>now},setTimeout:(fn,ms)=>{timers.set(++serial,{fn,at:now+ms});return serial},
  clearTimeout:id=>timers.delete(id),requestAnimationFrame:fn=>{fn();return 1},cancelAnimationFrame(){}});
 let click;
 bind({on:(name,fn)=>click=fn},()=>selected++);
 const advance=ms=>{now+=ms;for(const [id,t] of timers)if(t.at<=now){timers.delete(id);t.fn()}};
 const touch=(name,y=100,target='.hit',count=1)=>listeners[name]({type:name,touches:Array.from({length:count},()=>({clientX:100,clientY:y})),target:{closest:s=>s===target},cancelable:true,preventDefault(){},stopPropagation(){}});
 return {touch,advance,click:(event={type:'click',detail:1})=>click({originalEvent:event}),
  get selected(){return selected},get zoom(){return zoom},get enabled(){return enabled},moves};
}
for(const target of ['.hit',null]){
 const h=harness();
 h.touch('touchstart',100,target);h.touch('touchend',100,target);h.click();
 h.advance(100);h.touch('touchstart',100,target);
 assert.equal(h.enabled,false);
 h.touch('touchmove',110,target);h.touch('touchmove',180,target);h.touch('touchend',180,target);
 h.click();h.advance(400);
 assert.equal(h.selected,0,'Zoom must cancel pending marker selection and compatibility clicks');
 assert.ok(h.zoom>16,'Drag must zoom over marker and background');assert.equal(h.enabled,true);
 h.advance(200);h.touch('touchstart');h.touch('touchend');h.click();h.advance(301);
 assert.equal(h.selected,1,'Single tap must still select after zoom');
}
{
 const h=harness();h.touch('touchstart');h.touch('touchend');h.click();h.advance(100);
 h.touch('touchstart');h.touch('touchend');h.click();h.advance(600);
 assert.equal(h.zoom,17);assert.equal(h.selected,0);assert.equal(h.enabled,true);
 h.click({type:'keypress'});assert.equal(h.selected,1,'Keyboard remains immediate');
}
for(const cancel of ['touchcancel','multitouch']){
 const h=harness();h.touch('touchstart');h.touch('touchend');h.click();h.advance(100);h.touch('touchstart');
 if(cancel==='multitouch')h.touch('touchstart',100,'.hit',2);else h.touch('touchcancel');
 h.advance(600);h.click();h.advance(301);
 assert.equal(h.selected,1,'Cancellation must release activation suppression');assert.equal(h.enabled,true);
}
console.log('Map colors and gestures passed: service/shared/unknown colors, marker/background swipe zoom, double tap, single tap, keyboard, cancellation.');
