import {readFile,writeFile,mkdir} from 'node:fs/promises';
const escape=s=>s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const root=new URL('./',import.meta.url);
const messages=JSON.parse(await readFile(new URL('src/messages/pt-BR.json',root),'utf8'));
const base=(process.env.VITE_PUBLIC_BASE_URL || 'https://quizmosh-production.up.railway.app').replace(/\/$/,'');
if(!/^https?:\/\/[A-Za-z0-9.:-]+$/.test(base))throw new Error('Invalid PUBLIC_BASE_URL');
const template=await readFile(new URL('dist/index.html',root),'utf8');
const pages=['','how-to-play','privacy','terms','cookies'];
for(const page of pages){
 const title=page?messages[`legal.${page}.title`]+' | QuizMosh':'QuizMosh — party quiz multiplayer para amigos';
 const description=page?messages[`legal.${page}.intro`]:'Reúna amigos, escolha um modo e jogue QuizMosh no celular ou PC. Convidados entram com apelido, sem instalar. Cinema, conhecimentos gerais e Mosh Arena.';
 const url=base+(page?'/'+page:'/');
 const content=page?`<main><h1>${escape(title)}</h1><p>${escape(description)}</p>${[1,2,3,4].map(n=>`<section><h2>${escape(messages[`legal.${page}.h${n}`])}</h2><p>${escape(messages[`legal.${page}.p${n}`])}</p></section>`).join('')}<a href="/">Jogar QuizMosh</a></main>`:
 `<main itemscope itemtype="https://schema.org/VideoGame"><h1 itemprop="name">QuizMosh — um party quiz para amigos e grupos</h1><p itemprop="description">${escape(description)}</p><a href="/#join">Entrar em uma partida</a> <a href="/#create">Criar uma partida</a><h2>Jogue junto em quatro modos</h2><p>Na mosca, Bate-pronto, Qual é a boa? e Quase lá. Mosh Arena traz cartas, duetos e BIS. Cinema e conhecimentos gerais para festas, família, faculdade e grupos online.</p><ol>${[1,2,3,4].map(n=>`<li>${escape(messages['product.step'+n])}</li>`).join('')}</ol><p>Disponível no navegador de celular e PC, em português e inglês. O anfitrião define o idioma e o conteúdo global ou brasileiro das perguntas.</p><meta itemprop="gamePlatform" content="Web browser" /><a href="/how-to-play">Como jogar</a></main>`;
 const meta=`<link rel="canonical" href="${url}" /><meta property="og:type" content="website" /><meta property="og:title" content="${escape(title)}" /><meta property="og:description" content="${escape(description)}" /><meta property="og:url" content="${url}" /><meta property="og:image" content="${base}/social-preview.png" /><meta property="og:image:width" content="1200" /><meta property="og:image:height" content="630" /><meta name="twitter:card" content="summary_large_image" /><meta name="twitter:title" content="${escape(title)}" /><meta name="twitter:description" content="${escape(description)}" /><meta name="twitter:image" content="${base}/social-preview.png" />`;
 const html=template.replace(/<title>.*?<\/title>/,`<title>${escape(title)}</title>`).replace(/<meta name="description"[^>]+>/,`<meta name="description" content="${escape(description)}" />`).replace('</head>',meta+'</head>').replace('<div id="app"></div>',`<div id="app">${content}<nav><a href="/privacy">Privacidade</a> · <a href="/terms">Termos</a> · <a href="/cookies">Cookies</a></nav></div>`);
 const dir=new URL('dist/'+(page?page+'/':''),root);await mkdir(dir,{recursive:true});await writeFile(new URL('index.html',dir),html);
}
await writeFile(new URL('dist/robots.txt',root),`User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /join/\nDisallow: /oauth2/\nDisallow: /login/\nSitemap: ${base}/sitemap.xml\n`);
await writeFile(new URL('dist/sitemap.xml',root),`<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${pages.map(p=>`<url><loc>${base}/${p}</loc></url>`).join('')}</urlset>`);
console.log('Prerendered five public pages, sitemap and social metadata.');
