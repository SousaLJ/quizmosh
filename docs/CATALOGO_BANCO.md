# Catálogo no banco · 0.6.0

| Categoria | Alternativas | Pistas | Numéricas | Total |
| --- | ---: | ---: | ---: | ---: |
| Cinema | 46 | 27 | 27 | 100 |
| Conhecimentos gerais | 48 | 24 | 28 | 100 |
| Futebol | 50 | 25 | 25 | 100 |
| Videogames | 50 | 25 | 25 | 100 |
| Cultura pop | 50 | 25 | 25 | 100 |
| Música | 50 | 25 | 25 | 100 |
| **Total canônico** | **294** | **151** | **155** | **600** |

Cada pergunta tem português do Brasil e inglês: são 1.200 linhas de tradução, não 1.200 perguntas diferentes. Os 92 IDs anteriores foram preservados. Os quatro modos continuam disponíveis. O filtro Brasil reúne 181 perguntas; o Global reúne 419. Cada categoria comporta 12 rodadas do mix em qualquer filtro. Modos isolados em filtros regionais menores continuam sujeitos ao aviso de capacidade na interface e à validação do servidor.

## Primeira instalação e atualização

Com Docker instalado, execute `bash scripts/start-docker.sh` (ou `iniciar-docker.bat` no Windows). Os scripts criam as credenciais locais quando necessário e executam `docker compose up -d --build --wait`.

1. O PostgreSQL 17 inicia com seu volume persistente.
2. O serviço de jogo aguarda o healthcheck do banco.
3. O Flyway aplica as migrações pendentes em `quizmosh-server/src/main/resources/db/migration/`.
4. `Catalog` lê e valida categorias, perguntas e traduções do banco antes de o jogo ficar disponível.

| Migração | Finalidade |
| --- | --- |
| `V1__match_results.sql` | Arquivo de resultados existente, preservado |
| `V2__question_catalog.sql` | Estrutura relacional do catálogo |
| `V3__seed_question_catalog.sql` | Inserts de todas as categorias e das 600 perguntas, regiões e traduções |

A carga usa o histórico do Flyway para executar uma vez. Também funciona em um volume que já tinha a V1; não depende da inicialização de um volume vazio do PostgreSQL. Reinícios não reaplicam inserts nem sobrescrevem revisões. As migrações são transacionais no PostgreSQL. Erros de migração ou traduções obrigatórias ausentes impedem uma inicialização incompleta.

O JAR direto continua usando H2 em arquivo por padrão, com as mesmas migrações. PostgreSQL é o banco configurado no Compose. Contas de usuários e recuperação de salas ativas continuam fora deste incremento; o banco persiste o catálogo e os resultados concluídos.

## Estrutura

- `quiz_categories`: ID estável, ordem de exibição e ativação.
- `quiz_category_texts`: nomes das categorias por idioma.
- `quiz_questions`: categoria, tipo, índice correto ou valor numérico e ativação.
- `quiz_question_regions`: vínculos regionais; ausência de linha significa global.
- `quiz_question_texts`: enunciado, explicação, alternativas, pistas, aliases e unidade por idioma.

Listas são armazenadas como JSON em colunas TEXT, validadas pelo servidor, para manter as migrações compatíveis com PostgreSQL e H2. Chaves estrangeiras, chaves primárias e restrições protegem a estrutura comum. Os gabaritos permanecem no servidor durante rodadas abertas. `/api/meta` publica somente nomes e inventário agregado.

O catálogo é uma fotografia carregada no início do processo. Alterações diretas no banco exigem reinício para aparecer nas partidas. Uma categoria desativada e suas perguntas deixam de ser oferecidas; os resultados antigos permanecem preservados.

## Fonte editorial e validação

`content/catalog/v1/categories.json`, `questions.pt-BR.json` e `questions.en.json` são a fotografia editorial da carga inicial. Não são servidos ao navegador nem usados como fallback no servidor.

```bash
# Conferir integridade e correspondência exata com o SQL versionado
python3 scripts/catalog.py --check
```

O validador verifica IDs únicos, traduções correspondentes, categorias válidas, quatro alternativas distintas, quatro pistas distintas, aliases, respostas numéricas finitas e cobertura por formato. Também verifica mínimo de 100 perguntas por categoria e capacidade global de 12 rodadas por modo. `python3 scripts/catalog.py` reproduz o SQL **antes da primeira publicação desta migração**. O CI usa `--check` para detectar divergências.

Depois que V3 tiver sido publicada, não altere essa fotografia nem gere um novo conteúdo para V3. Crie uma nova migração, por exemplo `V4__review_football_question.sql`, e mantenha o registro editorial da mudança. Não execute o seed manualmente após o Flyway.

Exemplo de correção pontual em uma futura migração:

```sql
UPDATE quiz_question_texts
SET prompt = 'Enunciado revisado em português'
WHERE question_id = 'futebol-001' AND language = 'pt-BR';

UPDATE quiz_question_texts
SET prompt = 'Revised English prompt'
WHERE question_id = 'futebol-001' AND language = 'en';
```

Para uma categoria nova, insira `quiz_categories` e os dois nomes em `quiz_category_texts`; então insira perguntas, regiões e traduções com IDs novos. O servidor e a interface descobrem categorias do banco. A interface usa um ícone genérico quando ainda não há um ícone específico para o novo ID.

As referências de apoio consultadas estão em [REFERENCIAS_CATALOGO.md](REFERENCIAS_CATALOGO.md). Revise fatos e traduções antes de publicar. Use ano, edição, versão ou unidade explícita quando a resposta depender deles. Evite letras de músicas, diálogos longos e perguntas que dependam de recordes sem uma data de corte. As verificações automáticas de estrutura não certificam a exatidão editorial de um fato.

## Verificações de instalação

```bash
docker compose exec -T database psql -U quizmosh -d quizmosh -c \
  "SELECT category_id, COUNT(*) FROM quiz_questions GROUP BY category_id ORDER BY category_id;"
```

Uma instalação desta versão retorna seis linhas de 100 perguntas. `/api/meta` retorna `questions: 600` e seis categorias. O CI sobe os containers, consulta PostgreSQL, reinicia o serviço e confirma que perguntas, traduções, histórico de migrações e um resultado persistido permanecem intactos. Os testes Java também simulam atualização de V1 para V3, leitura efetiva do banco, desativação de conteúdo e falhas de tradução.

Atualize com `git pull --ff-only` e `docker compose up -d --build --wait`. Faça backup conforme [OPERACAO.md](OPERACAO.md); o volume inclui tanto os resultados quanto o catálogo. Reiniciar a aplicação encerra salas em andamento, pois elas ainda ficam em memória.
