#!/usr/bin/env python3
"""Real HTTP multiplayer smoke test. Run against a disposable local server."""
import json
import os
import http.cookiejar
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = (sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8080').rstrip('/')
LANGUAGE = sys.argv[2] if len(sys.argv) > 2 else 'pt-BR'
SCOPE = sys.argv[3] if len(sys.argv) > 3 else 'ALL'
RESOURCE = 'questions.en.json' if LANGUAGE == 'en' else 'questions.json'
CATALOG = json.loads((Path(__file__).resolve().parents[1] / 'quizmosh-server/src/main/resources' / RESOURCE).read_text(encoding='utf-8'))
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
host_cookies = http.cookiejar.CookieJar()
host_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(host_cookies))
host_csrf = None

def request(path, body=None, token=None, method=None, expected=200, language=None):
    global host_csrf
    headers = {'Content-Type': 'application/json', 'Accept-Language': language or LANGUAGE}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    active_opener = opener
    if path == '/api/rooms' and body is not None:
        token_account = os.environ.get('QUIZMOSH_SMOKE_ACCOUNT_TOKEN')
        if not token_account: raise RuntimeError('Set QUIZMOSH_SMOKE_ACCOUNT_TOKEN to a valid account session; no guest host bypass exists.')
        if host_csrf is None:
            bootstrap = urllib.request.Request(BASE + '/api/account', headers={'Cookie':'QM_ACCOUNT='+token_account})
            with host_opener.open(bootstrap,timeout=10) as r: host_csrf=json.load(r)
            assert host_csrf.get('user'), 'Smoke account is not authenticated'
        headers['Cookie']='QM_ACCOUNT='+token_account+'; '+ '; '.join(c.name+'='+c.value for c in host_cookies)
        headers[host_csrf['csrfHeader']]=host_csrf['csrf']
        active_opener = host_opener
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method or ('GET' if body is None else 'POST'))
    try:
        response = active_opener.open(req, timeout=10)
    except urllib.error.HTTPError as e:
        response = e
    with response:
        data = json.load(response)
        assert response.status == expected, (path, response.status, data)
        return data

def wait_state(code, token, phase, timeout=8):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        state = request('/api/rooms/' + code, token=token)
        if state['phase'] == phase:
            return state
        time.sleep(.15)
    raise AssertionError('Timed out waiting for ' + phase)

def run():
    assert request('/actuator/health')['status'] == 'UP'
    metadata = request('/api/meta')
    assert metadata['questionLanguages'] == ['pt-BR', 'en']
    assert len(metadata['catalog']) == 18
    assert not any(key in json.dumps(metadata) for key in ['correctIndex','answers','explanation'])
    h = request('/api/rooms', {'nickname': 'Host smoke', 'config': {'rounds': 4, 'seconds': 15, 'category': 'all', 'modes': ['classic-trivia', 'quick-fire', 'guess-it', 'closest-wins'], 'mosh': False, 'questionLanguage': LANGUAGE, 'contentScope': SCOPE, 'questionRegion': 'BR'}})
    code, token = h['code'], h['token']
    p = request(f'/api/rooms/{code}/join', {'nickname': 'Guest smoke', 'role': 'PLAYER'})
    tv = request(f'/api/rooms/{code}/join', {'nickname': 'TV smoke', 'role': 'DISPLAY'})
    request(f'/api/rooms/{code}', expected=401)
    request(f'/api/rooms/{code}/start', {}, p['token'], expected=403)
    request(f'/api/rooms/{code}/start', token=token, method='POST')
    state = wait_state(code, token, 'ROUND')
    for index in range(4):
        q = state['round']
        assert q['number'] == index + 1
        assert q['language'] == LANGUAGE
        if SCOPE == 'GLOBAL': assert q['regions'] == []
        if SCOPE == 'REGIONAL': assert q['regions'] == ['BR']
        other_ui = request(f'/api/rooms/{code}', token=p['token'], language='en' if LANGUAGE == 'pt-BR' else 'pt-BR')
        assert other_ui['round'] == q
        assert not any(key in json.dumps(q) for key in ['correctOptionId', 'acceptedAnswers', 'correctValue'])
        entry = next(x for x in CATALOG if x['prompt'] == q['prompt'] and (q['type'] != 'guess' or x['clues'][0] == q['clues'][0]))
        if q['type'] == 'choice':
            value = chr(65 + entry['correctIndex'])
        elif q['type'] == 'guess':
            value = entry['answers'][0]
        else:
            value = str(entry['value'])
        body = {'roundId': q['id'], 'value': value}
        request(f'/api/rooms/{code}/answer', body, tv['token'], expected=409)
        result = request(f'/api/rooms/{code}/answer', body, token)
        assert result['receipt']['accepted'] is True
        result = request(f'/api/rooms/{code}/answer', body, token)
        assert result['receipt']['accepted'] is False
        stranger_state = request(f'/api/rooms/{code}', token=p['token'])
        assert stranger_state['you']['answer'] is None
        state = request(f'/api/rooms/{code}/answer', body, p['token'])['state']
        assert state['phase'] == ('FINISHED' if index == 3 else 'REVEAL')
        assert state['reveal']['answer']
        request(f'/api/rooms/{code}/answer', body, token, expected=409)
        if index < 3:
            state = request(f'/api/rooms/{code}/next', {}, token)
    assert len(state['ranking']) == 2
    assert state['ranking'][0]['score'] >= 3700
    # Refresh/reconnect returns the final result under the same guest identity.
    reconnected = request(f'/api/rooms/{code}', token=token)
    assert reconnected['phase'] == 'FINISHED'
    assert reconnected['ranking'] == state['ranking']
    request(f'/api/rooms/{code}/start', token=token, method='POST')
    assert request(f'/api/rooms/{code}', token=token)['phase'] == 'COUNTDOWN'
    request(f'/api/rooms/{code}/leave', {}, token)
    assert request(f'/api/rooms/{code}', token=p['token'])['you']['owner']
    request(f'/api/rooms/{code}', token=token, expected=401)
    print('PASS: HTTP, 4 modes, 2 players + display, permissions, no answer leaks, duplicate/stale answers, final ranking, reconnect, rematch, host transfer.')

if __name__ == '__main__':
    run()
