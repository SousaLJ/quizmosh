// Node 22+. Test only against a disposable development instance.
import assert from 'node:assert/strict';
const base=process.argv[2]||'http://127.0.0.1:8080';
async function post(path,body,token) {
  const r=await fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify(body)});
  assert.equal(r.status,200);return r.json();
}
const host=await post('/api/rooms',{nickname:'WS Host'});
const ws=new WebSocket(base.replace(/^http/,'ws')+'/ws');
let last;
const messages=[];
const done=new Promise((resolve,reject)=>{
  const timeout=setTimeout(()=>reject(new Error('No room broadcast received')),8000);
  ws.onopen=()=>ws.send(JSON.stringify({code:host.code,token:host.token}));
  ws.onmessage=e=>{last=JSON.parse(e.data);messages.push(last);if(last.players.length===2){clearTimeout(timeout);resolve();}};
  ws.onerror=reject;
});
await post(`/api/rooms/${host.code}/join`,{nickname:'WS Guest'});
await done;
assert.equal(last.you.id,host.playerId);
assert(!JSON.stringify(messages).includes(host.token));
ws.close();
const invalid=new WebSocket(base.replace(/^http/,'ws')+'/ws');
await new Promise((resolve,reject)=>{
  const timeout=setTimeout(()=>reject(new Error('Invalid WebSocket not rejected')),4000);
  invalid.onopen=()=>invalid.send(JSON.stringify({code:host.code,token:'invalid'}));
  invalid.onclose=e=>{clearTimeout(timeout);assert.equal(e.code,1008);resolve();};
  invalid.onerror=()=>{};
});
console.log('PASS: authenticated WebSocket, personalized room broadcast, invalid session rejected.');
