<script setup lang="ts">
import {ref,watch,nextTick} from "vue";
import {t} from "./i18n";
import {account,providers,preferences,productReady,privacyOpen,loginOpen,loginError,login,logout,savePreferences,productApi,initializeProduct} from "./product";
const custom=ref(false),error=ref(""),busy=ref(false),profileOpen=ref(false),deleteConfirm=ref(false),nickname=ref("");
const draft=ref({...preferences.value});
const modal=ref<HTMLElement|null>(null);
let previousFocus:HTMLElement|null=null;
watch(()=>loginOpen.value || profileOpen.value,async open=>{
  if(open) {previousFocus=document.activeElement as HTMLElement;await nextTick();modal.value?.querySelector<HTMLElement>('button')?.focus();}
  else previousFocus?.focus();
});
function modalKeys(event:KeyboardEvent) {
  if(event.key!=='Tab')return;
  const items=Array.from(modal.value?.querySelectorAll<HTMLElement>('button:not([disabled]),a[href],input:not([disabled]),select') || []);
  if(!items.length)return;
  const target=event.shiftKey?items.at(-1)!:items[0];
  if((event.shiftKey && document.activeElement===items[0]) || (!event.shiftKey && document.activeElement===items.at(-1))) {event.preventDefault();target.focus();}
}
watch(privacyOpen,()=>{draft.value={...preferences.value};error.value="";});
watch(account,()=>{nickname.value=account.value?.nickname || "";},{immediate:true});
watch(profileOpen,()=>{deleteConfirm.value=false;error.value="";});
async function action(work:()=>Promise<unknown>) {busy.value=true;error.value="";try{await work();}catch(e){error.value=e instanceof Error?e.message:t("ui.weCouldnTCompleteTheAction");}finally{busy.value=false;}}
async function choose(all:boolean) {await action(()=>savePreferences({...preferences.value,analytics:all,advertising:all,personalization:all,decided:true}));}
async function exportData() {const data=await productApi("/account/export");const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:"application/json"}));const a=document.createElement("a");a.href=url;a.download="quizmosh-data.json";a.click();URL.revokeObjectURL(url);}
async function remove() {await productApi("/account/delete",{confirm:true});account.value=null;profileOpen.value=false;await initializeProduct();}
</script>
<template>
  <div class="product-tools">
    <button v-if="account" class="text-button" @click="profileOpen=true">{{t('product.account')}}</button>
    <button v-else class="text-button" @click="loginOpen=true">{{t('product.signIn')}}</button>
    <button class="text-button" @click="privacyOpen=true;custom=true">{{t('product.privacySettings')}}</button>
  </div>
  <section v-if="privacyOpen" class="consent-panel" role="region" :aria-label="t('product.privacySettings')">
    <h2>{{t('product.privacyTitle')}}</h2><p>{{t('product.privacyIntro')}}</p>
    <div v-if="custom" class="consent-options">
      <label><input type="checkbox" checked disabled />{{t('product.necessary')}}</label>
      <label><input type="checkbox" v-model="draft.analytics" />{{t('product.analytics')}}</label>
      <label><input type="checkbox" v-model="draft.advertising" />{{t('product.advertising')}}</label>
      <label><input type="checkbox" v-model="draft.personalization" />{{t('product.personalization')}}</label>
      <p>{{t('product.optionalUnused')}}</p>
    </div>
    <p v-if="!productReady">{{t('product.unavailable')}}</p>
    <p v-if="error" role="alert">{{error}}</p>
    <div class="product-actions">
      <button :disabled="busy || !productReady" @click="choose(false)">{{t('product.reject')}}</button>
      <button :disabled="busy || !productReady" @click="choose(true)">{{t('product.accept')}}</button>
      <button v-if="!custom" @click="custom=true">{{t('product.customize')}}</button>
      <button v-else :disabled="busy || !productReady" @click="action(()=>savePreferences({...draft,decided:true}))">{{t('product.save')}}</button>
      <button v-if="preferences.decided" @click="privacyOpen=false">{{t('product.close')}}</button>
    </div><a href="/cookies">{{t('product.cookies')}}</a> · <a href="/privacy">{{t('product.privacy')}}</a>
  </section>
  <div v-if="loginOpen || profileOpen" class="product-overlay" @keydown.esc="loginOpen=false;profileOpen=false">
    <section ref="modal" class="product-modal" role="dialog" aria-modal="true" :aria-label="t(loginOpen?'product.signIn':'product.account')" tabindex="-1" @keydown="modalKeys">
      <button class="text-button" @click="loginOpen=false;profileOpen=false;error=''">{{t('product.close')}}</button>
      <template v-if="loginOpen"><h2>{{t('product.loginTitle')}}</h2><p>{{t('product.loginIntro')}}</p>
        <p v-if="loginError" role="alert">{{t('product.loginFailed')}}</p>
        <div class="product-actions"><button v-for="provider in providers" :key="provider" @click="login(provider)">{{t('product.continueWith',{provider:provider==='google'?'Google':'Discord'})}}</button></div>
        <p v-if="!providers.length">{{t('product.loginUnavailable')}}</p>
        <p><a href="/terms">{{t('product.terms')}}</a> · <a href="/privacy">{{t('product.privacy')}}</a></p>
      </template>
      <template v-else><h2>{{t('product.account')}}</h2>
        <label>{{t('ui.yourNickname')}}<input v-model="nickname" maxlength="24" autocomplete="nickname" /></label>
        <div class="product-actions"><button :disabled="busy" @click="action(async()=>{account=await productApi('/account/profile',{nickname})})">{{t('product.save')}}</button>
        <button :disabled="busy" @click="action(exportData)">{{t('product.export')}}</button>
        <button :disabled="busy" @click="action(async()=>{await logout();profileOpen=false})">{{t('product.logout')}}</button>
        <button :disabled="busy" @click="action(async()=>{await productApi('/account/revoke-all',{});await initializeProduct();profileOpen=false})">{{t('product.revokeAll')}}</button></div>
        <label><input type="checkbox" v-model="deleteConfirm" />{{t('product.deleteConfirm')}}</label>
        <button :disabled="busy || !deleteConfirm" @click="action(remove)">{{t('product.delete')}}</button>
      </template>
      <p v-if="error" role="alert">{{error}}</p>
    </section>
  </div>
</template>
