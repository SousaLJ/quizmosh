# Configuração e publicação da 0.5.0

## Estado da publicação

O ponto de partida é main 0.4.0 `d926a82`, já publicado no Railway. A 0.5.0 exige conta para criar qualquer sala (inclusive bots). **Não promover sem pelo menos um provedor OAuth funcionando**, pois isso impediria novas partidas. O aceite completo desta entrega exige testar Google e Discord reais.

## Configuração local

Java 25, Maven Wrapper, Node 22.12+. `bash scripts/build.sh`; `npm test --prefix quizmosh-web`. Docker: copiar `.env.example`, definir senha PostgreSQL e credenciais OAuth, executar `docker compose up -d --build --wait`. A rede externa do serviço Java permite as chamadas aos provedores; PostgreSQL continua sem porta pública e isolado na rede interna. Vite encaminha `/api`, `/ws`, `/oauth2` e `/login` ao servidor.

JAR: `SECURE_COOKIES=false PUBLIC_BASE_URL=http://localhost:8080 java -jar releases/quizmosh.jar`. O padrão seguro exige HTTPS; desative Secure apenas para desenvolvimento HTTP. Sem providers configurados, guest join funciona em salas existentes, mas não há criação de salas. Nenhum usuário, senha ou login de teste é habilitado automaticamente.

## Variáveis

| Nome | Valor / finalidade |
|---|---|
| PUBLIC_BASE_URL | Origem pública exata, sem caminho, ex. `https://quizmosh-production.up.railway.app` |
| SECURE_COOKIES | `true` em qualquer domínio HTTPS público; false somente em localhost HTTP |
| OAUTH_GOOGLE_CLIENT_ID / OAUTH_GOOGLE_CLIENT_SECRET | Credenciais de cliente Web do Google |
| OAUTH_DISCORD_CLIENT_ID / OAUTH_DISCORD_CLIENT_SECRET | Credenciais do aplicativo Discord |
| DATABASE_URL | URL JDBC: `jdbc:postgresql://HOST:5432/DATABASE` |
| DATABASE_USER / DATABASE_PASSWORD | Usuário e senha do banco |
| PORT | Porta HTTP Java, padrão 8080 |
| SERVER_FORWARD_HEADERS_STRATEGY | `framework` atrás do proxy Railway confiável |
| MAX_ROOMS | Limite global de salas, padrão 250 |
| VITE_PUBLIC_BASE_URL | Origem usada no pre-render ao compilar fora do Docker; não contém secrets |

**DATABASE_URL deve ser JDBC.** Não copiar diretamente uma URL `postgresql://user:password@host/...` para essa variável. No Railway, usar referências privadas ao serviço PostgreSQL, por exemplo `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`, mais `DATABASE_USER=${{Postgres.PGUSER}}` e `DATABASE_PASSWORD=${{Postgres.PGPASSWORD}}`. O nome do serviço deve corresponder ao serviço real.

## Google e Discord

1. Criar/selecionar um projeto Google Cloud, configurar a tela de consentimento e criar um OAuth client do tipo Web. Enquanto em teste, cadastrar os test users exigidos pelo Google. Registrar exatamente:
   `https://quizmosh-production.up.railway.app/login/oauth2/code/google`.
2. Criar/selecionar uma aplicação no Discord Developer Portal e registrar:
   `https://quizmosh-production.up.railway.app/login/oauth2/code/discord`.
3. Inserir IDs e secrets diretamente nas variáveis privadas do serviço Railway; nunca no frontend, repositório ou chat. Não habilitar logs DEBUG de OAuth.
4. Conferir PUBLIC_BASE_URL e reiniciar o serviço. Cada provedor aparece na interface somente quando ID e secret estão preenchidos.
5. Testar login em navegador limpo, logout e retorno à mesma conta; testar estado/consentimento e bloqueio de criação anônima. Ambientes de preview precisam de callbacks próprios e do PUBLIC_BASE_URL correspondente.

Escopos são definidos no servidor (`openid` e `identify`). Não solicitar email, refresh token/offline access ou contatos. O fixture dos testes Java simula a troca de authorization code, PKCE e ID token assinado; não equivale à validação das aplicações reais de produção.

## PostgreSQL, migrations e rollback

Provisionar PostgreSQL com volume persistente antes do deploy e manter uma réplica Java. Flyway executa V1 existente e V2 aditiva no startup; não altera checksum de V1. Falha de migration impede readiness. Não usar baseline-on-migrate, clean ou DDL automático para encobrir divergências. Validar migração numa cópia de backup antes de promover dados reais.

No Railway, criar serviço PostgreSQL com volume e conectá-lo ao Java por rede privada. Não expor TCP publicamente. Conferir que volume/backups existem: um container postgres sem volume não resolve persistência. O plugin disponível nesta execução não expõe criação de volumes; a configuração pode exigir o painel autenticado.

H2 anterior no filesystem efêmero do serviço não garante retenção entre deploys. Exportar resultados que devam ser preservados antes da troca; não alegar migração desses dados sem tê-los extraído. Partidas ativas continuam sendo encerradas em restart.

Rollback de código: reimplantar o commit anterior sem apagar migrations/tabelas. Dados novos permanecem no PostgreSQL; não aplicar downgrade de schema destrutivo. Avisar participantes antes de reiniciar.

## Backups e operação

Habilitar backups diários do volume PostgreSQL, retenção inicial de sete dias, acesso restrito e teste de restauração mensal em destino isolado. Conferir custos e recursos do plano Railway antes de ativar agendamento. Como alternativa, executar `pg_dump` com credenciais via ambiente e armazenar criptografado fora do mesmo volume. Nunca versionar dumps.

Registros excluídos podem existir em backups até expirarem: restringir restaurações e reaplicar exclusões antes de reabrir o serviço. A política final deve refletir a retenção que o operador realmente configurar; este documento é uma estratégia, não prova de backup ativado.

Health: `/actuator/health`. Readiness com banco: `/actuator/health/readiness`. Liveness: `/actuator/health/liveness`. Logs JSON e X-Request-ID correlacionam erros; não registram cookies, OAuth tokens ou corpo de requisições. Spring/Micrometer mantém métricas HTTP/JVM internas; somente health é exposto publicamente. Não habilitar `/actuator/env` ou `/actuator/configprops` publicamente.

## Testes e smoke

Os scripts existentes continuam cobrindo gameplay; agora precisam de uma sessão de conta para criar salas. CI gera um token aleatório, grava somente seu hash num PostgreSQL **descartável**, e passa `QUIZMOSH_SMOKE_ACCOUNT_TOKEN` aos scripts. `scripts/seed-smoke-account.py` não deve ser executado em produção. Não existe endpoint de seed ou flag que permita criar salas como guest.

Em produção, usar sessão autenticada de uma conta de teste real via ambiente temporário local, sem registrar o cookie no histórico ou nos logs, e executar os três smoke tests. A presença de um build verde não substitui essa verificação com providers e banco reais.

## Pendências externas de aceite

- Credenciais e callbacks reais dos dois provedores.
- PostgreSQL com volume e estratégia de backup configurados no Railway.
- Identificação do controlador e canal privado de atendimento de privacidade para completar as políticas.
- Login real, restart com conta preservada e smoke final no domínio de produção após essas configurações.
