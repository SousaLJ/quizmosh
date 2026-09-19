#!/usr/bin/env python3
"""Print SQL for a disposable CI database only. Never execute against production.

Uses the real account session mechanism. No HTTP backdoor or production auth flag.
Generate QUIZMOSH_SMOKE_ACCOUNT_TOKEN with secrets.token_urlsafe(32).
"""
import hashlib, os, re
token=os.environ['QUIZMOSH_SMOKE_ACCOUNT_TOKEN']
assert re.fullmatch(r'[A-Za-z0-9_-]{43}',token)
user='00000000-0000-4000-8000-000000000001'
print(f"INSERT INTO users(id,created_at) VALUES ('{user}',CURRENT_TIMESTAMP);")
print(f"INSERT INTO user_profiles(user_id,nickname) VALUES ('{user}','CI Host');")
print(f"INSERT INTO user_sessions(token_hash,user_id,created_at,expires_at) VALUES ('{hashlib.sha256(token.encode()).hexdigest()}','{user}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '1' HOUR);")
