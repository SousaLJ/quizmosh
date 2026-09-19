# Auditoria de partida — 0.5.0

Em 2026-09-17, main e Railway apontavam para `d926a820c6236feaf90b31cd87d498da5cf6c1d3` (0.4.0). Deploy `e597aecb-9fa4-4b03-87f0-26a01622ebff` estava SUCCESS. GitHub Actions run `35245178671` passou. Não havia diferença de código pendente de publicação.

O ambiente Railway continha apenas quizmosh, sem serviço PostgreSQL, volume ou credenciais OAuth; variáveis existentes: PORT e SERVER_FORWARD_HEADERS_STRATEGY. Portanto o histórico H2 estava sujeito à perda em redeploy.

Arquitetura: seis módulos; domínio/aplicação independentes de Spring. API e WebSocket utilizam bearer aleatório por participante, em memória, com validade de 12h. Cliente guarda a sessão por aba. JDBC ResultArchive e Flyway V1 já persistem partidas concluídas. Docker compila Vue e incorpora dist ao JAR Java 25. Uma réplica é necessária.

Baseline frontend: 18 testes e build de produção passaram. A tentativa inicial de Java encontrou JDK previamente extraído incompleto e dificuldades de proxy; JDK foi novamente baixado e seu checksum verificado. Smoke remoto inicial encontrou timeout/restrição de rede; isso não representa falha comprovada do jogo. Ver resultados finais no documento de validação.

Decisão: evoluir JDBC/Flyway existentes; separar autenticação de conta da participação na sala. Login social exige aplicações OAuth reais e seus secrets, não presentes no ambiente auditado. Nenhum login de desenvolvimento será habilitado em produção para substituir isso.
