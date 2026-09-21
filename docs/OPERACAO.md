# Operação

| Variável | Padrão / função |
| --- | --- |
| POSTGRES_PASSWORD | Obrigatória no Compose; gerada pelos scripts |
| HTTP_PORT / HTTPS_PORT | 8080 / 8443; portas publicadas |
| SITE_ADDRESS | :80 para rede local; domínio para HTTPS |
| MAX_ROOMS | 250 salas ativas |
| DATABASE_URL | PostgreSQL no Compose; H2 em arquivo no modo direto |
| DATABASE_USER / DATABASE_PASSWORD | Credenciais JDBC |
| PORT | 8080 no Java; mantenha no Compose |

Não publique `.env`. Alterar a senha no arquivo depois da inicialização de um volume PostgreSQL não altera a senha dentro do banco; faça a rotação também no PostgreSQL.

## Servidor público

1. Aponte o domínio para o servidor.
2. Em `.env`: `SITE_ADDRESS=quiz.seudominio.com`, `HTTP_PORT=80`, `HTTPS_PORT=443` e senha aleatória.
3. Libere TCP 80 e 443; execute `docker compose up -d --build --wait`.
4. Abra `https://quiz.seudominio.com`. Caddy obtém e renova certificados se o domínio e os desafios da autoridade certificadora alcançarem o servidor.

Use uma réplica game. HTTPS protege os tokens. Não exponha PostgreSQL nem o Vite publicamente.

## Estado e logs

`GET /actuator/health` verifica aplicação e banco. `docker compose logs -f game` mostra início/fim de partidas sem registrar tokens ou palpites. `match_results` contém ID, código da sala, data e JSON de configuração, ranking e resultados das rodadas. Nicknames e placares são persistidos; não há cadastro obrigatório.

Partidas ativas e tokens ficam em memória. Um restart encerra as salas. Avise os jogadores antes de atualizar. Falha no arquivo de resultados não deve impedir mostrar o placar; confira os logs da aplicação.

## Atualizar o catálogo e a aplicação

```bash
git pull --ff-only
docker compose up -d --build --wait
docker compose logs --tail=80 game
```

O serviço aguarda o PostgreSQL ficar saudável e o Flyway terminar as migrações antes de carregar o catálogo. A versão 0.6 adiciona as tabelas de categorias/perguntas e a carga de 600 perguntas com traduções. A tabela `flyway_schema_history` registra o que já foi aplicado: reinícios comuns não repetem os inserts. O volume existente e `match_results` são preservados. Não use `down -v` para atualizar.

`/api/meta` permite conferir `questions: 600`, seis `categories` e as contagens por idioma, categoria e filtro cultural. O catálogo fica em memória após a leitura do banco; correções SQL passam a valer após reiniciar o serviço. Instruções editoriais e exemplos de novas migrações: [CATALOGO_BANCO.md](CATALOGO_BANCO.md).

## Backup

```bash
docker compose exec -T database pg_dump -U quizmosh -d quizmosh > quizmosh-backup.sql
```

Para restaurar em um banco de destino apropriado, sem sobrepor dados importantes:

```bash
docker compose exec -T database psql -U quizmosh -d quizmosh < quizmosh-backup.sql
```

No modo direto, encerre o processo e copie `data/`. Não copie H2 enquanto o servidor escreve nele.

## Problemas frequentes

- Porta ocupada: altere HTTP_PORT ou use `--server.port=9090` no JAR.
- Java incompatível: use Java 25; Docker já fornece o runtime.
- Celular não entra: confira IPv4 do PC, Wi-Fi, firewall e isolamento de clientes. localhost no celular aponta para o próprio celular.
- Sala sumiu: reinício, fechamento pelo último humano ou expiração.
- Sem som: ative o ícone e interaja com a página; navegadores exigem um gesto antes de reproduzir.
- Reconexão: recarregue a mesma aba. Fechá-la pode eliminar o token em sessionStorage.

Se o banco ficar indisponível ao finalizar uma partida, o relatório concluído fica em uma fila em memória, com nova tentativa a cada 30 segundos. A fila preserva o relatório mesmo se uma revanche começar. Reiniciar o processo antes de a gravação ter sucesso pode perder esses resultados pendentes; confira os avisos nos logs.
