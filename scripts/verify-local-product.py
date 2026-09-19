#!/usr/bin/env python3
"""Verify the packaged JAR using an isolated temporary H2 database and real account cookies.
Requires Java 25, Python 3 and Node 22+. Never connects to production.
"""
import json, os, secrets, subprocess, tempfile, time, urllib.request, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
JAR=ROOT/'quizmosh-server/target/quizmosh-server-0.1.0-SNAPSHOT.jar'
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))

with tempfile.TemporaryDirectory(prefix='quizmosh-smoke-') as temp:
    temp=Path(temp)
    database='jdbc:h2:file:'+str(temp/'database')+';MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE'
    # Each invocation gets an independent account capability. No fixture is part of the application.
    env=dict(os.environ,QUIZMOSH_SMOKE_ACCOUNT_TOKEN=secrets.token_urlsafe(32))
    log=(temp/'server.log').open('w')
    def start():
        process=subprocess.Popen([JAVA,'-jar',str(JAR),'--server.port=18085','--spring.datasource.url='+database,
            '--quizmosh.secure-cookies=false','--server.servlet.session.cookie.secure=false'],cwd=temp,stdout=log,stderr=log)
        for _ in range(150):
            try:
                with opener.open('http://127.0.0.1:18085/actuator/health',timeout=1) as response:
                    if json.load(response)['status']=='UP':return process
            except Exception:time.sleep(.2)
        process.terminate();process.wait(timeout=10)
        raise RuntimeError('Temporary server did not become healthy: '+(temp/'server.log').read_text()[-6000:])
    process=start();process.terminate();process.wait(timeout=15)
    with zipfile.ZipFile(JAR) as archive:
        h2=next(n for n in archive.namelist() if n.startswith('BOOT-INF/lib/h2-') and n.endswith('.jar'))
        (temp/'h2.jar').write_bytes(archive.read(h2))
    sql=subprocess.check_output(['python3',str(ROOT/'scripts/seed-smoke-account.py')],env=env)
    (temp/'seed.sql').write_bytes(sql)
    subprocess.run([JAVA,'-cp',str(temp/'h2.jar'),'org.h2.tools.RunScript','-url',database,'-user','sa','-script',str(temp/'seed.sql')],check=True)
    process=start()
    try:
        base='http://127.0.0.1:18085'
        with opener.open(urllib.request.Request(base+'/api/account',headers={'Cookie':'QM_ACCOUNT='+env['QUIZMOSH_SMOKE_ACCOUNT_TOKEN']})) as r:
            assert json.load(r)['user']['nickname']=='CI Host'
        for args in [['python3','scripts/smoke.py',base],['python3','scripts/mosh-smoke.py',base,'en','REGIONAL'],['node','scripts/websocket-smoke.mjs',base]]:
            subprocess.run(args,cwd=ROOT,env=env,check=True,timeout=100)
        for path in ['/','/privacy','/terms','/cookies','/how-to-play','/join/ABCD','/sitemap.xml','/robots.txt','/social-preview.png','/actuator/health/readiness']:
            with opener.open(base+path) as r:assert r.status==200,path
        with opener.open(base+'/') as r:
            html=r.read().decode();assert 'og:image' in html and '<h1' in html and 'canonical' in html
        print('PASS: persisted account after restart; authenticated host/guest smoke; public routes, SEO, social card and readiness.')
    finally:
        process.terminate();process.wait(timeout=15);log.close()
