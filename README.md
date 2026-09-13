# Banking Microservices

Plataforma bancaria baseada em microsservicos, construida como projeto de portfolio para demonstrar arquitetura de sistemas distribuidos no estilo usado por bancos digitais: autenticacao via JWT, comunicacao sincrona (REST) e assincrona (Kafka), cache distribuido, idempotencia em transacoes financeiras, migrations versionadas de banco de dados, testes automatizados e um pipeline de CI.

## Funcionalidades

- Cadastro e autenticacao de clientes (JWT, senhas com BCrypt, papeis `ROLE_CUSTOMER` / `ROLE_ADMIN`)
- Abertura de conta, consulta de saldo (com cache), deposito e encerramento de conta
- Transferencia entre contas com idempotencia (protege contra duplicidade em retentativas)
- Extrato/historico de transacoes por conta
- Notificacao (simulada) de cliente a cada transferencia concluida ou falha, via evento Kafka
- Documentacao interativa (Swagger/OpenAPI) em cada servico
- Testes unitarios e de integracao (com bancos e broker reais via Testcontainers)

## Arquitetura

O sistema segue Domain-Driven Design (DDD) por servico (camadas `domain`, `application`, `api`, `infrastructure`) e o padrao de microsservicos com **um banco de dados por servico** — nenhum servico acessa a tabela de outro diretamente, toda comunicacao entre eles e via HTTP ou eventos.

```
                        ┌──────────────────┐
                 ┌─────▶│  customer-service │  (8081) — identidade, login, JWT
                 │      └──────────────────┘
                 │
┌─────────────┐  │      ┌──────────────────┐
│ api-gateway │──┼─────▶│  account-service  │  (8082) — contas e saldo (cache Redis)
│   (8080)    │  │      └──────────────────┘
└─────────────┘  │               ▲
                 │               │ REST (debita/credita)
                 │      ┌──────────────────────┐
                 └─────▶│ transaction-service   │  (8083) — transferencias, idempotencia,
                        └──────────────────────┘   publica eventos no Kafka
                                  │
                                  │ evento "transaction-events"
                                  ▼
                        ┌───────────────────────┐
                        │ notification-service   │  (8084) — consome eventos, simula envio
                        └───────────────────────┘
```

- **api-gateway**: ponto de entrada unico (porta 8080). Roteia cada path para o servico correspondente e valida o token JWT antes de repassar a chamada (as demais aplicacoes tambem validam o token de forma independente).
- **customer-service**: dono da identidade do cliente. E o unico servico que emite tokens JWT (`/api/v1/auth/login`).
- **account-service**: dono do saldo das contas. Expoe endpoints internos de credito/debito usados pelo transaction-service, e cacheia consultas de saldo no Redis.
- **transaction-service**: orquestra uma transferencia (debita a origem, credita o destino, compensa a origem se o credito falhar), garante idempotencia via chave `Idempotency-Key` (Redis + constraint unica no banco) e publica um evento Kafka ao concluir.
- **notification-service**: consome os eventos de transacao e grava/"envia" (log) uma notificacao para cada conta envolvida.

Um modulo `common-security` (biblioteca compartilhada, nao e um servico) concentra o codigo de JWT e o tratamento global de erros usado pelos quatro servicos Spring MVC. O api-gateway, por ser reativo (WebFlux), tem sua propria validacao de JWT — os dois times de codigo compartilham a mesma logica de assinatura/segredo, mas nao a mesma dependencia (misturar Spring MVC e WebFlux no mesmo classpath quebra a inicializacao do servidor reativo).

## Tecnologias

| Categoria | Escolha |
|---|---|
| Linguagem / plataforma | Java 25, Spring Boot 4.0.8 |
| Web | Spring MVC (servicos de negocio) + Spring WebFlux (gateway) |
| Seguranca | Spring Security, JWT (JJWT), BCrypt |
| Persistencia | Spring Data JPA + Hibernate, PostgreSQL, Flyway (migrations) |
| Mensageria | Apache Kafka (Spring Kafka) |
| Cache | Redis (Spring Cache) |
| Documentacao de API | springdoc-openapi / Swagger UI |
| Testes | JUnit 5, Mockito, Testcontainers, Spring Security Test, Awaitility |
| Build | Maven (multi-modulo) |
| Containers | Podman / Podman Compose (compativel com `docker-compose.yml`) |
| CI | GitHub Actions |

> Nota de engenharia: a versao mais recente do Spring Cloud (2025.0.0), usada inicialmente para o api-gateway, ainda nao e compativel com o Spring Boot 4.0.8 (uma classe interna do Spring Boot foi movida de pacote e o Spring Cloud Gateway falha ao subir). Em vez de travar o projeto numa combinacao quebrada do ecossistema, o gateway foi implementado como um proxy reverso reativo enxuto sobre WebFlux/WebClient — mais simples, sem dependencias extras e sem esse problema de compatibilidade.

## Como rodar (Podman)

Pre-requisitos: [Podman](https://podman.io/) instalado e a maquina Podman iniciada (`podman machine start`, no Windows/macOS).

```bash
podman compose up --build
```

Isso sobe Postgres, Redis, Kafka (modo KRaft, sem Zookeeper) e os cinco servicos Spring Boot, cada um publicado tambem na porta do host para facilitar testes diretos:

| Servico | URL | Swagger UI |
|---|---|---|
| API Gateway | http://localhost:8080 | — (use o Swagger de cada servico abaixo) |
| Customer Service | http://localhost:8081 | http://localhost:8081/swagger-ui.html |
| Account Service | http://localhost:8082 | http://localhost:8082/swagger-ui.html |
| Transaction Service | http://localhost:8083 | http://localhost:8083/swagger-ui.html |
| Notification Service | http://localhost:8084 | http://localhost:8084/swagger-ui.html |

Todas as chamadas de negocio devem passar pelo **api-gateway** (porta 8080); as portas individuais dos servicos ficam expostas apenas para facilitar debug e o uso do Swagger.

### Rodando localmente sem containers (apenas a infraestrutura em Podman)

```bash
podman compose up postgres redis kafka
```

E em terminais separados, a partir da raiz do projeto:

```bash
mvn -pl common-security install
mvn -pl customer-service spring-boot:run
mvn -pl account-service spring-boot:run
mvn -pl transaction-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

## Fluxo de teste manual (via api-gateway)

```bash
# 1. Criar um cliente
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Maria Silva","email":"maria@example.com","documentNumber":"12345678901","password":"Senha@123"}'

# 2. Login (retorna o token JWT)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"maria@example.com","password":"Senha@123"}' | jq -r .accessToken)

# 3. Abrir duas contas
curl -X POST http://localhost:8080/api/v1/accounts -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"customerId":"<id-do-cliente>","type":"CHECKING"}'

# 4. Transferir entre contas (idempotente)
curl -X POST http://localhost:8080/api/v1/transactions/transfer \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: 3f6a9e2e-1234-4c11-9a11-000000000001" \
  -d '{"sourceAccountId":"<origem>","targetAccountId":"<destino>","amount":150.00}'
```

Usuario admin de demonstracao (seed via Flyway no customer-service): `admin@bank.com` / `Admin@123`.

## Testes

```bash
# a partir da raiz do projeto
mvn clean verify
```

Os testes de integracao usam Testcontainers (sobem Postgres/Kafka/Redis reais em containers durante o build). Como o ambiente de desenvolvimento usa Podman em vez de Docker, exporte antes:

```bash
export DOCKER_HOST=npipe:////./pipe/podman-machine-default   # Windows
export TESTCONTAINERS_RYUK_DISABLED=true
```

(Em Linux, `DOCKER_HOST` normalmente ja aponta para o socket do Podman automaticamente.) No pipeline de CI (GitHub Actions, `.github/workflows/ci.yml`) os testes rodam com Docker nativo, sem necessidade dessas variaveis.

## Estrutura do repositorio

```
banking-microservices/
├── common-security/        # biblioteca compartilhada: JWT e tratamento global de erros
├── customer-service/       # identidade, autenticacao, clientes
├── account-service/        # contas, saldo, cache Redis
├── transaction-service/    # transferencias, idempotencia, eventos Kafka
├── notification-service/   # consumo de eventos, notificacoes simuladas
├── api-gateway/            # proxy reverso reativo + validacao de JWT na borda
├── docker/                 # Dockerfile generico + script de criacao dos bancos
├── docker-compose.yml      # orquestracao local (Podman Compose)
└── .github/workflows/ci.yml
```
