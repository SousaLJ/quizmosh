#!/usr/bin/env python3
"""Real multiplayer Mosh match: tactics, secrets, all four modes, BIS, and rematch."""
from smoke import request, wait_state, CATALOG, LANGUAGE, SCOPE

h = request('/api/rooms', {'nickname': 'Arena host', 'config': {'rounds': 4, 'seconds': 15, 'category': 'all', 'modes': ['classic-trivia', 'quick-fire', 'guess-it', 'closest-wins'], 'mosh': True, 'questionLanguage': LANGUAGE, 'contentScope': SCOPE, 'questionRegion': 'BR'}})
code, token = h['code'], h['token']
g = request(f'/api/rooms/{code}/join', {'nickname': 'Arena guest', 'role': 'PLAYER'})
tv = request(f'/api/rooms/{code}/join', {'nickname': 'Arena TV', 'role': 'DISPLAY'})
request(f'/api/rooms/{code}/start', token=token, method='POST')
state = wait_state(code, token, 'BACKSTAGE')
expected_scores = {h['playerId']: 0, g['playerId']: 0}
for index, card in enumerate(['DUET', 'SPOTLIGHT', 'ALL_IN', 'ALL_IN']):
    assert state['round'] is None
    assert state['mosh']['encore'] == (index == 3)
    body = {'stageId': state['mosh']['stageId'], 'card': card, 'target': g['playerId'] if card == 'DUET' else None}
    before = state['mosh']['energy'][h['playerId']]
    request(f'/api/rooms/{code}/plan', body, tv['token'], expected=403)
    committed = request(f'/api/rooms/{code}/plan', body, token)
    retry = request(f'/api/rooms/{code}/plan', body, token)
    assert retry['mosh']['energy'] == committed['mosh']['energy']
    for viewer in [g, tv]:
        view = request(f'/api/rooms/{code}', token=viewer['token'])
        assert h['playerId'] not in view['mosh']['plans']
        assert view['mosh']['energy'][h['playerId']] == before
    request(f'/api/rooms/{code}/plan', {**body, 'target': h['playerId'] if card == 'DUET' else None}, g['token'])
    state = wait_state(code, token, 'ROUND')
    assert len(state['mosh']['plans']) == 2
    q = state['round']
    assert q['language'] == LANGUAGE
    if SCOPE == 'REGIONAL': assert q['regions'] == ['BR']
    alternate = request(f'/api/rooms/{code}', token=g['token'], language='pt-BR' if LANGUAGE == 'en' else 'en')
    assert alternate['round'] == q
    entry = next(x for x in CATALOG if x['prompt'] == q['prompt'] and (q['type'] != 'guess' or x['clues'][0] == q['clues'][0]))
    value = chr(65+entry['correctIndex']) if q['type'] == 'choice' else entry['answers'][0] if q['type'] == 'guess' else str(entry['value'])
    request(f'/api/rooms/{code}/answer', {'roundId': q['id'], 'value': value}, token)
    state = request(f'/api/rooms/{code}/answer', {'roundId': q['id'], 'value': value}, g['token'])['state']
    assert state['phase'] == ('FINISHED' if index == 3 else 'REVEAL')
    for player_id, result in state['mosh']['results'].items():
        assert result['bonus'] == (600 if index == 0 else 300 if index == 1 else result['base'] * (2 if index == 3 else 1))
        assert result['reasonKey'].startswith('mosh.result.')
        assert result['total'] == result['base'] + result['bonus']
        assert state['reveal']['deltas'][player_id] == result['total']
        expected_scores[player_id] += result['total']
    if index < 3:
        state = request(f'/api/rooms/{code}/next', {}, token)
        assert state['phase'] == 'BACKSTAGE'
assert {p['id']: p['score'] for p in state['ranking']} == expected_scores
request(f'/api/rooms/{code}/start', token=token, method='POST')
state = request(f'/api/rooms/{code}', token=token)
assert state['mosh']['heat'] == 0 and all(e == 3 for e in state['mosh']['energy'].values())
print('PASS: Mosh HTTP full match; mutual duet, split spotlight, all-in, four modes, BIS, secret costs, retry protection, spectator permissions, totals and rematch reset.')
