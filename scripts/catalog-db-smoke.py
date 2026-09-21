#!/usr/bin/env python3
"""Check PostgreSQL seed and restart persistence in the disposable Compose CI stack."""
import json
import os
import subprocess
import time
import urllib.request

if os.environ.get('CI') != 'true':
    raise SystemExit('This check restarts the game and adds a test result. Run only in the disposable CI stack (CI=true).')


def sql(statement):
    return subprocess.check_output(['docker', 'compose', 'exec', '-T', 'database',
        'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'quizmosh', '-d', 'quizmosh', '-At', '-c', statement], text=True).strip()


def snapshot():
    return sql("""
        SELECT 'questions='||COUNT(*) FROM quiz_questions;
        SELECT 'translations='||COUNT(*) FROM quiz_question_texts;
        SELECT category_id||'='||COUNT(*) FROM quiz_questions GROUP BY category_id ORDER BY category_id;
        SELECT 'migrations='||COUNT(*) FROM flyway_schema_history WHERE success;
        SELECT 'marker='||COUNT(*) FROM match_results WHERE id='catalog-restart-smoke';
    """)


assert sql('SELECT COUNT(*) FROM quiz_questions') == '600'
assert sql('SELECT COUNT(*) FROM quiz_question_texts') == '1200'
assert sql('SELECT COUNT(*) FROM quiz_categories') == '6'
assert sql('SELECT MIN(n) FROM (SELECT COUNT(*) AS n FROM quiz_questions GROUP BY category_id) counts') == '100'
assert sql("SELECT COUNT(*) FROM quiz_questions q WHERE NOT EXISTS (SELECT 1 FROM quiz_question_texts t WHERE t.question_id=q.id AND t.language='pt-BR') OR NOT EXISTS (SELECT 1 FROM quiz_question_texts t WHERE t.question_id=q.id AND t.language='en')") == '0'
sql("INSERT INTO match_results(id,room_code,finished_at,payload) VALUES ('catalog-restart-smoke','TEST',CURRENT_TIMESTAMP,'{}')")
before = snapshot()
subprocess.run(['docker', 'compose', 'restart', 'game'], check=True)
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
deadline = time.monotonic() + 90
while True:
    try:
        with opener.open('http://127.0.0.1:8080/api/meta', timeout=3) as response:
            metadata = json.load(response)
        assert metadata['questions'] == 600 and len(metadata['categories']) == 6
        break
    except (OSError, ValueError, AssertionError):
        if time.monotonic() > deadline:
            raise
        time.sleep(0.5)
assert snapshot() == before, 'Restart changed seeded rows, migration history or the stored result'
print('PostgreSQL: six categories, 600 questions, 1200 translations; restart preserved catalogue and result.')
