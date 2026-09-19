# Validação — 0.5.0

## Baseline antes da evolução

- main e produção Railway: 0.4.0, commit d926a82; CI GitHub anterior verde.
- 31 testes Java e 18 testes frontend aprovados.
- Smoke HTTP clássico, Mosh Arena com BIS e WebSocket aprovados com o JAR anterior.

## Verificações da implementação

- 44 testes Java aprovados: gameplay anterior, migrations, contas, identidade estável, sessões, logout, expiração, revogação total, quotas, exportação/exclusão, guest join e proibição de guest create, CSRF, consentimento, referral, isolamento de preferências e analytics. A verificação completa executou 43 testes; em seguida, a classe de produto foi reexecutada com seus 11 testes, incluindo a regressão adicional de rejeição de analytics preservada após logout.
- Fixture OAuth local: authorization code completo para Discord e Google, PKCE, ID token Google assinado, reutilização da mesma identidade e rejeição de state/nonce inválidos. Não utiliza um endpoint de login de desenvolvimento na aplicação.
- Frontend: 28 testes de gameplay, PT/EN, internacionalização, criação autenticada, login, consentimento, privacidade, links e compartilhamento; TypeScript e build Vite aprovados.
- Smoke do JAR 0.5: anfitrião autenticado e convidados, quatro modos, Mosh Arena/BIS, permissões, isolamento de respostas, reconexão, transferência de host, revanche e WebSocket aprovados.
- Persistência: conta real no schema do H2 temporário preservada após reinício; endpoint de conta reconhece o cookie previamente emitido.
- Páginas `/`, `/how-to-play`, `/privacy`, `/terms`, `/cookies`, `/join/ABCD`, sitemap, robots, PNG social e readiness retornaram HTTP 200. HTML inicial contém conteúdo, canonical e Open Graph sem executar JS.

## Limites de evidência

Verificação local concluída em 2026-09-19. Após autorização explícita do proprietário, a branch `feature/product-foundations-0.5` foi publicada e o [PR #1](https://github.com/SousaLJ/quizmosh/pull/1) foi aberto. A árvore do primeiro commit remoto `d39f1fc` é idêntica à do commit local validado `0676c9b`. Não houve deploy; a produção permanece na 0.4.0. Docker não está disponível no ambiente local; o resultado de build Docker e integração PostgreSQL real deve ser consultado nos checks do PR.

O teste local de persistência usa H2 em modo PostgreSQL, não prova compatibilidade integral com PostgreSQL. O workflow do GitHub constrói Docker, sobe PostgreSQL 17 e executa os três smoke tests com conta semeada somente no banco descartável do CI. Consultar o resultado do workflow associado ao commit final antes de promover.

Google/Discord reais no domínio Railway dependem das credenciais e configurações externas descritas em DEPLOY_0.5.md. Não foram validados com credenciais de produção. Não alegamos que cookies, backup ou PostgreSQL remoto já estejam configurados. A produção 0.4.0 deve permanecer funcional até esses pré-requisitos serem atendidos.

Comando adicional reproduzível, após build do JAR:

```bash
python3 scripts/verify-local-product.py
```

Esse comando cria seu próprio banco temporário, reinicia o JAR, semeia uma sessão descartável e executa os testes de rede; não alcança produção.
