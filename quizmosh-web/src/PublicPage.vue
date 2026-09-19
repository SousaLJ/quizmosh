<script setup lang="ts">
import {t} from './i18n';
defineProps<{page:string}>();
</script>
<template><main class="public-page"><a href="/">← {{t('ui.backToHome')}}</a><h1>{{t('legal.'+page+'.title')}}</h1><p>{{t('legal.'+page+'.intro')}}</p><section v-for="n in 4" :key="n"><h2>{{t('legal.'+page+'.h'+n)}}</h2><p>{{t('legal.'+page+'.p'+n)}}</p></section><p>{{t('legal.version')}}</p></main></template>
