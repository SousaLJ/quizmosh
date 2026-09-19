import {beforeEach,afterEach,it,expect,vi} from 'vitest';
import {mount,flushPromises} from '@vue/test-utils';
import Sharing from './Sharing.vue';
import {api} from './game';
import {setLocale} from './i18n';
vi.mock('./game',()=>({api:vi.fn()}));
beforeEach(()=>{setLocale('en');vi.mocked(api).mockReset();});
afterEach(()=>vi.restoreAllMocks());
it('copies the server referral invitation and supports clipboard feedback',async()=>{
 vi.mocked(api).mockResolvedValue({path:'/join/ABCD?ref=referral'});
 const copy=vi.fn().mockResolvedValue(undefined);Object.defineProperty(navigator,'clipboard',{value:{writeText:copy},configurable:true});
 const w=mount(Sharing,{props:{code:'ABCD'}});await w.findAll('button')[0].trigger('click');await flushPromises();
 expect(copy).toHaveBeenCalledWith(expect.stringContaining('/join/ABCD?ref=referral'));expect(w.text()).toContain('Copied!');w.unmount();
});
it('shares result text through Web Share and falls back to a direct link if attribution is unavailable',async()=>{
 vi.mocked(api).mockRejectedValue(new Error('analytics offline'));const share=vi.fn().mockResolvedValue(undefined);Object.defineProperty(navigator,'share',{value:share,configurable:true});
 const w=mount(Sharing,{props:{code:'ABCD',ranking:[{rank:1,nickname:'Player',score:8450}]}});
 await w.findAll('button')[1].trigger('click');await flushPromises();
 expect(share).toHaveBeenCalledWith(expect.objectContaining({text:expect.stringContaining('1. Player — 8450')}));
 expect(share.mock.calls[0][0].text).toContain('/join/ABCD');expect(w.text()).toContain('nicknames');w.unmount();
});
