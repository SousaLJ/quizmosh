import { ref } from "vue";
import { t, locale } from "./i18n";

export type Preferences = {policyVersion:string;analytics:boolean;advertising:boolean;personalization:boolean;decided:boolean};
export const account = ref<{id:string;nickname:string}|null>(null);
export const providers = ref<string[]>([]);
export const preferences = ref<Preferences>({policyVersion:"2026-09-17",analytics:false,advertising:false,personalization:false,decided:false});
export const productReady=ref(false), privacyOpen=ref(false), loginOpen=ref(false);
export const loginError=ref(false);
let csrf="", csrfHeader="X-XSRF-TOKEN";
export function csrfHeaders():Record<string,string> {return csrf?{[csrfHeader]:csrf}:{};}
export async function productApi(path:string,body?:unknown) {
  const response=await fetch("/api"+path,{method:body===undefined?"GET":"POST",credentials:"same-origin",keepalive:path==='/events',headers:{"Content-Type":"application/json","Accept-Language":locale.value,...csrfHeaders()},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
  const data=await response.json();
  if(!response.ok) throw new Error(data.message || t("ui.weCouldnTCompleteTheAction"));
  return data;
}
export async function initializeProduct() {
  try {
    const data=await productApi("/account");
    account.value=data.user;providers.value=data.providers;csrf=data.csrf;csrfHeader=data.csrfHeader;
    preferences.value=data.preferences;productReady.value=true;
    privacyOpen.value=!data.preferences.decided;
    if(new URLSearchParams(location.search).get('login')==='failed') {loginError.value=true;loginOpen.value=true;}
    await track("LANDING_VIEWED");
    if(new URLSearchParams(location.search).has("ref")) await track("SHARE_OPENED");
  } catch {productReady.value=false;}
}
export async function savePreferences(choice:Preferences) {
  // Stop optional tracking immediately, even if saving the revocation fails.
  if(!choice.analytics) preferences.value={...preferences.value,analytics:false};
  preferences.value=await productApi("/privacy",choice);
  privacyOpen.value=false;
  if(choice.analytics && new URLSearchParams(location.search).has("ref")) await track("SHARE_OPENED");
}
export async function track(event:string) {
  if(!productReady.value || !preferences.value.analytics) return;
  try {await productApi("/events",{event,referral:new URLSearchParams(location.search).get("ref")});} catch { /* Telemetry never blocks play. */ }
}
export async function logout() {await productApi("/account/logout",{});account.value=null;await initializeProduct();}
export function login(provider:string) {void track("LOGIN_STARTED");location.assign("/oauth2/authorization/"+encodeURIComponent(provider));}
export function requireHost():boolean {
  void track("CREATE_GAME_CLICKED");
  if(account.value) return true;
  loginOpen.value=true;return false;
}
export function invitedRoom():string {return /^\/join\/([A-Za-z0-9]{4,8})\/?$/.exec(location.pathname)?.[1]?.toUpperCase() || new URLSearchParams(location.search).get("room") || "";}
