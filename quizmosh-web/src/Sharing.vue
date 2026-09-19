<script setup lang="ts">
import {ref,computed} from "vue";
import {t} from "./i18n";
import {api} from "./game";
import {track} from "./product";
const props=defineProps<{code:string;ranking?:{rank:number;nickname:string;score:number}[]}>();
const message=ref(""), link=ref("");
const url=computed(()=>link.value || `${location.origin}/join/${props.code}`);
const text=computed(()=>props.ranking?`QuizMosh\n${props.ranking.map(p=>`${p.rank}. ${p.nickname} — ${p.score}`).join('\n')}\n${url.value}`:t('product.inviteText',{code:props.code})+' '+url.value);
async function prepare() {try{link.value=location.origin+(await api('/shares',{code:props.code})).path;}catch{link.value=`${location.origin}/join/${props.code}`;}}
async function copy() {await prepare();try{await navigator.clipboard.writeText(text.value);message.value=t('product.copied');}catch{message.value=text.value;}}
async function share() {await prepare();try{if(navigator.share) await navigator.share({title:'QuizMosh',text:text.value});else await navigator.clipboard.writeText(text.value);}catch(e){if((e as Error).name!=='AbortError') message.value=text.value;}}
async function whatsapp() {await prepare();location.href='https://wa.me/?text='+encodeURIComponent(text.value);}
async function card() {
  const canvas=document.createElement('canvas');canvas.width=1080;canvas.height=1350;
  const ctx=canvas.getContext('2d');if(!ctx)return;
  ctx.fillStyle='#181a26';ctx.fillRect(0,0,1080,1350);ctx.fillStyle='#c7ff5c';ctx.font='bold 86px sans-serif';ctx.fillText('QUIZMOSH',70,135);
  ctx.fillStyle='#f5f3ff';ctx.font='30px sans-serif';ctx.fillText(t('product.resultCard'),70,210);
  for(const [i,p] of (props.ranking||[]).entries()) {ctx.fillStyle=i===0?'#c7ff5c':'#f5f3ff';ctx.font='bold 34px sans-serif';ctx.fillText(`${p.rank}. ${p.nickname}`,70,310+i*70,720);ctx.textAlign='right';ctx.fillText(String(p.score),1010,310+i*70);ctx.textAlign='left';}
  ctx.fillStyle='#bca5ff';ctx.font='28px sans-serif';ctx.fillText(location.host,70,1260,940);
  canvas.toBlob(async blob=>{if(!blob)return;const file=new File([blob],'quizmosh-result.png',{type:'image/png'});
    try{if(navigator.canShare?.({files:[file]})) await navigator.share({files:[file],title:'QuizMosh'});else{const u=URL.createObjectURL(blob),a=document.createElement('a');a.href=u;a.download=file.name;a.click();URL.revokeObjectURL(u);}void track('SHARE_CREATED');}catch(e){if((e as Error).name!=='AbortError')message.value=t('product.shareFailed');}
  },'image/png');
}
</script>
<template><div class="share-actions"><button @click="copy">{{t('product.copyLink')}}</button><button @click="share">{{t(ranking?'product.shareResult':'product.share')}}</button><button @click="whatsapp">WhatsApp</button><button v-if="ranking" @click="card">{{t('product.saveCard')}}</button></div><p v-if="ranking" class="muted">{{t('product.shareNames')}}</p><p v-if="message" role="status">{{message}}</p></template>
