# Pulso Escolar API

API REST do **Pulso Escolar**, plataforma que conecta coordenação pedagógica, professores e alunos de uma escola em um só lugar: turmas, disciplinas, atividades em formato de quiz com correção automática, acompanhamento de desempenho e gestão de contas.

## Arquitetura

Monólito em camadas (Controller → Service → Repository).

| Parte | Tecnologia |
|---|---|
| Linguagem / framework | Java 21, Spring Boot 4 |
| Banco de dados | PostgreSQL, com migrations Flyway |
| Autenticação | JWT (OAuth2 Resource Server) com refresh token e 2FA por e-mail |
| E-mail | Resend |
| Testes | JUnit 5, Spring Test, H2 em memória |
| CI | GitHub Actions (`.github/workflows/ci.yml`) |

## Funcionalidades

- **Perfis de acesso:** administrador, coordenador pedagógico, professor e aluno, cada um com suas permissões.
- **Escolas e turmas:** cadastro de escolas, turmas, vínculo de professores e alunos, transferência de alunos entre escolas e deliberação de turma (aprovação/reprovação, encaminhamento ou formatura).
- **Disciplinas e atividades:** disciplinas com ementa e tópicos, quizzes com prazo e correção automática, ranking de alunos e relatório de desempenho para o professor.
- **Cadastro e convites:** autocadastro de alunos com aprovação da coordenação e convites para professores e coordenadores.
- **Segurança:** senhas com política de força, sessão com expiração, renovação única de token, verificação em duas etapas por e-mail, limite de requisições e auditoria de eventos.
- **LGPD:** termos de uso versionados com novo aceite, exportação dos dados do usuário e exclusão de conta (processada automaticamente após 15 dias).
- **Apoio:** notificações, perguntas frequentes por perfil e métricas para administradores e coordenadores.

## Rodando localmente

Pré-requisitos: Java 21 e um banco PostgreSQL. O Maven não precisa estar instalado, pois o projeto traz o wrapper (`mvnw`).

```bash
# 1. copie o modelo de configuração e preencha com os seus dados
cp .env.example .env

# 2. suba a API (http://localhost:8080)
./mvnw spring-boot:run        # no Windows: mvnw.cmd spring-boot:run
```

As configurações ficam no arquivo `.env`, que não é versionado. As principais variáveis:

| Variável | Para que serve |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexão com o PostgreSQL |
| `JWT_SECRET` | Chave de assinatura dos tokens: Base64 de pelo menos 32 bytes aleatórios (`openssl rand -base64 32`) |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | **Obrigatórias.** Criam o administrador inicial se ele ainda não existir |
| `RESEND_API_KEY`, `RESEND_FROM_EMAIL` | Envio de e-mails (opcionais para desenvolvimento, mas sem elas não há envio) |
| `CORS_ALLOWED_ORIGINS` | Origens do front-end permitidas (padrão: `localhost:3001` e `localhost:5173`) |

As migrations rodam sozinhas na inicialização. O restante das variáveis (validade do token, limites de requisição) tem valor padrão e está comentado em `.env.example`.

## Testes

```bash
./mvnw verify
```

Os testes usam H2 em memória, então **não precisam** de banco nem de `.env`. O mesmo comando roda no CI a cada push e Pull Request.
