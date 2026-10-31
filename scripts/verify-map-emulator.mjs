// Requires adb forwarding tcp:9222 to the running debug WebView socket.
// Uses temporary fixtures inside the emulator; reloads the map when finished.
import assert from 'node:assert/strict';
import {writeFileSync} from 'node:fs';
const pages=await (await fetch('http://127.0.0.1:9222/json')).json();
const page=pages.find(p=>p.webSocketDebuggerUrl);
assert.ok(page,'Open the map in the debug app first');
const socket=new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
let serial=0;const pending=new Map();
socket.onmessage=e=>{const data=JSON.parse(e.data);if(data.id){const handler=pending.get(data.id);pending.delete(data.id);data.error?handler.reject(data.error):handler.resolve(data.result)}};
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++serial;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}))});
const evaluate=async expression=>{const r=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});assert.ok(!r.exceptionDetails,JSON.stringify(r.exceptionDetails));return r.result.value};
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const touch=(type,x,y)=>send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{x,y}]});
const tap=async(x,y)=>{await touch('touchStart',x,y);await touch('touchEnd',x,y)};
try{
 await evaluate(`window.savedNativeMap=window.NativeMap;window.selections=[];window.NativeMap={selectStop:id=>selections.push('stop:'+id),selectVehicle:id=>selections.push('vehicle:'+id),stopViewport(){},locationInView(){}};
 window.setMapType('standard');map.setView([47.5,19.05],16);window.setStopsEnabled(true);
 window.updateStops(['RAIL','BUS','COACH','SUBURBAN_RAILWAY','TRAMTRAIN','RAIL_REPLACEMENT_BUS'].map((mode,i)=>({id:'fixture-'+mode,name:mode,modes:[mode],lat:47.5+(i-2)*.002,lon:19.05})));
 window.updateVehicles([{id:'fixture-vehicle',lat:47.5,lon:19.0515,mode:'RAIL',type:'Vonat',name:'Teszt',headsign:'Teszt'}]);`);
 const position=kind=>evaluate(`(()=>{const marker=${kind==='stop'?'stopMarkers':'markers'}.getLayers()[0],p=map.latLngToContainerPoint(marker.getLatLng()),r=map.getContainer().getBoundingClientRect();return {x:p.x+r.left,y:p.y+r.top}})()`);
 for(const kind of ['stop','vehicle']){
  await evaluate('map.setView([47.5,19.05],16);selections.length=0');await wait(600);
  const {x,y}=await position(kind),before=await evaluate('map.getZoom()');
  await tap(x,y);await wait(70);await touch('touchStart',x,y);await touch('touchMove',x,y+15);await touch('touchMove',x,y+100);await touch('touchEnd',x,y+100);await wait(600);
  assert.ok(await evaluate('map.getZoom()')>before,kind+' swipe must zoom');
  assert.deepEqual(await evaluate('selections'),[],kind+' swipe must not select');
  const single=await position(kind);await tap(single.x,single.y);await wait(450);
  assert.equal((await evaluate('selections')).length,1,kind+' single tap must select');
 }
 await evaluate('map.setView([47.5,19.05],16);selections.length=0');await wait(600);
 const {x,y}=await position('vehicle');await tap(x,y);await wait(70);await tap(x,y);await wait(600);
 assert.equal(await evaluate('map.getZoom()'),17,'Double tap on vehicle must zoom one level');
 assert.deepEqual(await evaluate('selections'),[],'Double tap must not select');
 await evaluate('map.setView([47.5,19.05],16);void 0');await wait(350);
 const backgrounds=await evaluate("stopMarkers.getLayers().map(marker=>getComputedStyle(marker.getElement().querySelector('.hit>div')).backgroundImage)");
 assert.equal(backgrounds.length,6);assert.ok(backgrounds.every(value=>value.includes('conic-gradient')),'WebView must render service rings');
 const screenshot=await send('Page.captureScreenshot',{format:'png'});
 writeFileSync(new URL('../artifacts/map-service-colors-emulator.png',import.meta.url),Buffer.from(screenshot.data,'base64'));
 console.log('Android WebView real touch checks passed: stop/vehicle double-tap-drag zoom, single-tap selection, vehicle double-tap zoom.');
}finally{
 await evaluate('window.NativeMap=window.savedNativeMap;location.reload()');socket.close();
}
