import {beforeEach,afterEach,describe,it,expect,vi} from 'vitest';
import {mount,flushPromises} from '@vue/test-utils';
import {account,preferences,productReady,loginOpen,privacyOpen,providers,requireHost,invitedRoom,track,initializeProduct,savePreferences} from './product';
import ProductPanels from './ProductPanels.vue';
import PublicPage from './PublicPage.vue';
import {setLocale} from './i18n';
beforeEach(()=>{setLocale('en');account.value=null;providers.value=['google','discord'];productReady.value=true;loginOpen.value=false;privacyOpen.value=false;preferences.value={policyVersion:'2026-09-17',analytics:false,advertising:false,personalization:false,decided:false};vi.stubGlobal('fetch',vi.fn());history.replaceState({},'','/');});
afterEach(()=>vi.unstubAllGlobals());
describe('Product flows',()=>{
 it('requires an account to host and opens login for guests',()=>{expect(requireHost()).toBe(false);expect(loginOpen.value).toBe(true);account.value={id:'1',nickname:'Host'};expect(requireHost()).toBe(true);});
 it('reads new and legacy invitation links',()=>{history.replaceState({},'','/join/h7kf?ref=abc');expect(invitedRoom()).toBe('H7KF');history.replaceState({},'','/?room=ABCD');expect(invitedRoom()).toBe('ABCD');});
 it('sends no analytics before consent or after rejection',async()=>{await track('LANDING_VIEWED');expect(fetch).not.toHaveBeenCalled();vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({...preferences.value,decided:true})} as Response);await savePreferences({...preferences.value,decided:true});vi.mocked(fetch).mockClear();await track('SHARE_OPENED');expect(fetch).not.toHaveBeenCalled();});
 it('does not block gameplay on telemetry failure',async()=>{preferences.value.analytics=true;vi.mocked(fetch).mockRejectedValue(new Error('offline'));await expect(track('LANDING_VIEWED')).resolves.toBeUndefined();});
 it('loads account, providers and server consent',async()=>{vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({user:{id:'u',nickname:'Host'},providers:['google'],csrf:'token',csrfHeader:'X-XSRF-TOKEN',preferences:{...preferences.value,decided:true}})} as Response);await initializeProduct();expect(account.value?.id).toBe('u');expect(privacyOpen.value).toBe(false);});
 it('renders balanced consent actions and allows customization',async()=>{privacyOpen.value=true;const w=mount(ProductPanels);expect(w.text()).toContain('Reject optional');expect(w.text()).toContain('Accept all');const b=w.findAll('button').find(b=>b.text()==='Customize')!;await b.trigger('click');expect(w.findAll('input[type=checkbox]')).toHaveLength(4);w.unmount();});
 it('shows both social providers and the no-account guest explanation',()=>{loginOpen.value=true;const w=mount(ProductPanels);expect(w.text()).toContain('Google');expect(w.text()).toContain('Discord');expect(w.text()).toContain('without an account');w.unmount();});
 it('renders public policies in PT and EN',async()=>{const w=mount(PublicPage,{props:{page:'privacy'}});expect(w.text()).toContain('Privacy at QuizMosh');setLocale('pt-BR');await flushPromises();expect(w.text()).toContain('Privacidade no QuizMosh');w.unmount();});
});
