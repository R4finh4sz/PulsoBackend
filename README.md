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

O cadastro de escolas exige CEP e UF. O backend consulta o ViaCEP e compara UF, cidade, bairro e logradouro com os dados enviados, ignorando diferenças de caixa, acentos e espaços repetidos. Apenas campos não retornados pelo serviço aceitam preenchimento manual; cidade e UF precisam ser verificáveis. CEP inexistente retorna 400 e falha na consulta retorna 503, sem gravar. A alteração de CEP/UF também precisa corresponder ao endereço já cadastrado.

- **Perfis de acesso:** administrador, coordenador pedagógico, professor e aluno, cada um com suas permissões.
- **Escolas e turmas:** cadastro de escolas, turmas, vínculo de professores e alunos, transferência de alunos entre escolas e deliberação de turma (aprovação/reprovação, encaminhamento ou formatura).
- **Disciplinas e atividades:** disciplinas com ementa e tópicos, quizzes com prazo e correção automática, ranking de alunos e relatório de desempenho para o professor.
- **Cadastro e convites:** autocadastro de alunos com aprovação da coordenação e convites para professores e coordenadores.
- **Segurança:** senhas com política de força, sessão com expiração, renovação única de token, verificação em duas etapas por e-mail, limite de requisições e auditoria de eventos.
- **LGPD:** termos de uso versionados com novo aceite, exportação dos dados do usuário e exclusão de conta (processada automaticamente após 15 dias).
- **Apoio:** notificações, perguntas frequentes por perfil e métricas para administradores e coordenadores.

## Valores de seleção validados pela API

Campos de opções fixas enviados em JSON aceitam o nome exato da opção: perfis (`role`/`targetRole`), situações (`status`), tipo de solicitação (`type`), ação e resultado da deliberação (`action`/`result`). Números, nomes desconhecidos, diferenças de maiúsculas/minúsculas e espaços extras retornam `400`. Além disso, cada operação restringe as opções válidas naquele contexto: por exemplo, um aceite só admite `APPROVED` ou `REJECTED`.

UF no cadastro e atualização de escola aceita somente os 27 códigos oficiais em maiúsculas, como `PE` e `SP`; `ZZ`, `pe`, nomes por extenso e números são recusados. A busca de escola também valida o parâmetro `state`. A obrigatoriedade dos campos permanece a mesma de cada rota.

Seleções dinâmicas, como escolas, turmas, professores e tópicos de ementa, são verificadas no backend quanto à existência, vínculo e autorização. A lista do frontend facilita a escolha, mas não substitui essas verificações. Campos livres, como nome da escola, pergunta e ementa, continuam sendo texto livre com seus limites próprios.

## Transferência de alunos e aceite

A coordenação de origem solicita a transferência em `POST /api/students/transfer`, com `fullName`, `ra` e `schoolId` de destino. O aluno permanece na escola atual até o aceite da coordenação de destino.

No PulsoWeb, a solicitação fica no perfil do aluno, em "Transferir para outra escola", com busca da escola de destino por CEP. A coordenação de destino recebe a transferência na aba "Alunos" da fila de solicitações e escolhe a turma antes de aprovar. Publique o backend e o frontend atualizados para disponibilizar o fluxo completo.

`GET /api/registration-requests` reúne cadastros e transferências destinadas à escola do coordenador, com paginação e filtros de situação e perfil. Cada item informa `type` (`REGISTRATION` ou `TRANSFER`); transferências incluem `originSchoolId` e o `userId` existente. O filtro opcional `type=TRANSFER` ou `type=REGISTRATION` seleciona apenas um tipo. O admin continua recebendo somente cadastros de coordenadores.

O aceite usa `PATCH /api/registration-requests/{id}`. Para transferência, envie `{"type":"TRANSFER","status":"APPROVED","classroomId":123}`; a turma deve pertencer à escola de destino. Para recusar, envie `{"type":"TRANSFER","status":"REJECTED"}`. Cadastros usam `type=REGISTRATION`; `reason` se aplica a cadastros. Os IDs pertencem a sequências diferentes: o frontend deve identificar itens pelo par `type` + `id` e enviar o tipo ao analisar. Clientes antigos podem omitir o tipo para cadastros, mas recebem `409` se esse ID também identificar uma transferência destinada à mesma escola, evitando analisar o item errado. Fotos de transferências usam `GET /api/registration-requests/{id}/photo?type=TRANSFER`.

A transferência mantém a mesma conta, senha, notas e entregas; o aluno continua consultando seu histórico anterior por `/api/student/activities` e pelos resultados das próprias entregas. As turmas e atividades antigas permanecem na escola de origem. As rotas anteriores `/api/students/transfers` continuam disponíveis e usam a mesma validação de aceite.

## Métricas da escola

`GET /api/schools/me/metrics` informa os totais atuais, excluindo contas de alunos e professores já excluídas. `GET /api/schools/me/metrics/growth?year=2026` representa o histórico de cadastros do ano: meses, acumulados, médias e contagens sem data de cadastro incluem contas excluídas. A exclusão da conta reduz o total atual, preservando sua contribuição histórica no gráfico. As métricas retornam valores agregados, sem identificar contas excluídas.

## Limite de requisições

Requisições sem autenticação são limitadas por IP; depois da autenticação, pelo ID do usuário, independentemente do token, dispositivo ou IP. Pessoas autenticadas no mesmo Wi-Fi têm contadores separados. Pessoas sem autenticação que compartilham um IP compartilham o limite. Cookies não alteram essa contagem.

O limite geral usa `RATE_LIMIT_MAX_REQUESTS` e `RATE_LIMIT_WINDOW` (padrão: 120 por minuto). Rotas `/api/auth/**`, `/api/invitations/**` e `POST /api/students` também consomem o limite mais restrito de `AUTH_RATE_LIMIT_MAX_REQUESTS` e `AUTH_RATE_LIMIT_WINDOW` (padrão: 10 por minuto). Ao exceder, a API retorna `429` com `Retry-After`; requisições `OPTIONS` não são contadas.

O filtro usa `request.getRemoteAddr()`, sem interpretar cabeçalhos enviados diretamente pelo cliente. Atrás de proxy, configure o servidor para resolver o IP original somente a partir de proxies confiáveis. No Tomcat, isso pode ser configurado com `SERVER_FORWARD_HEADERS_STRATEGY=NATIVE` e `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`, usando os endereços dos proxies da hospedagem. Sem essa configuração, o endereço visto pode ser o do proxy e os usuários anônimos compartilharão o mesmo contador. Não habilite confiança em qualquer origem.

Os contadores ficam na memória de cada instância e são reiniciados ao reiniciar a API. Para compartilhar limites entre várias instâncias, será necessário um armazenamento compartilhado, como Redis.

## Cadastro de alunos e ano das atividades

O autocadastro de aluno (`POST /api/auth/register`, em JSON ou multipart, e `POST /api/students`) retorna `409` se o e-mail ou RA já pertencer a uma conta ou solicitação de cadastro, inclusive em outra escola. A comparação de e-mail ignora maiúsculas e minúsculas. Contas existentes devem usar o login ou a recuperação de senha, sem criar outro cadastro.

O ano da atividade (`year`) é definido pelo backend a partir de `createdAt`, no fuso `America/Sao_Paulo`, e não é um campo editável da criação. O encerramento deve ser futuro e pertencer ao ano atual nesse mesmo fuso: atividades criadas em 2026 não podem encerrar em 2027. Na virada do ano a regra acompanha automaticamente o novo ano. Dados e prazos das atividades antigas são preservados.

## Ordem das questões do quiz

Novos quizzes embaralham as questões por aluno e atividade. A ordem se mantém ao reabrir o quiz, consultar o resultado ou reiniciar a API. Professores veem a ordem original; alternativas não são embaralhadas. Quizzes existentes antes da migração V47 mantêm a ordem original, preservando respostas e tentativas em andamento.

O contrato HTTP continua igual: envie `answers` na ordem das questões recebidas em `GET /api/activities/{id}/quiz`. O backend reorganiza as respostas para corrigir e armazenar na ordem original. Respostas em entregas, resultados e listagem de atividades do aluno seguem a ordem exibida àquele aluno; consultas do professor seguem a ordem original. Notas, desempenho por tema e relatórios usam a mesma correção para todos.

## Notificações de quiz

Os alunos da turma recebem uma notificação `QUIZ_OPENED` quando o professor publica um quiz. No dia do encerramento, recebem `QUIZ_CLOSING_TODAY`, com a disciplina e o horário limite, caso ainda não tenham respondido. O lembrete considera o fuso `America/Sao_Paulo` e é processado a cada minuto, uma única vez por atividade, inclusive após reiniciar a API. Contas excluídas, alunos que saíram da turma/escola e turmas excluídas não recebem avisos.

As mensagens aparecem em `GET /api/notifications`, com o mesmo formato e fluxo de leitura das demais notificações. São avisos dentro da aplicação, sem envio de e-mail ou push.

## Administração de escolas

A identificação da turma é única dentro de cada escola (escola, nome e letra). Escolas diferentes podem ter, por exemplo, uma turma `1º ano · Matutino`, letra `B`, cada uma com seus próprios vínculos.

As rotas abaixo exigem autenticação como `ADMIN`:

- `PATCH /api/schools/{id}/deactivate`: desativa a escola, mesmo com vínculos, e retorna `204`. Repetir a operação também retorna `204`; escola inexistente retorna `404`. A antiga rota `DELETE /api/schools/{id}` foi removida.
- `PATCH /api/schools/{id}/coordinator`, com `{"coordinatorId":123}`: define ou substitui o coordenador e retorna a escola atualizada (`200`). A conta deve existir, estar ativa e ter perfil `PEDAGOGICAL_COORDINATOR`. O coordenador anterior perde o vínculo com a escola, mas sua conta é preservada. O substituto pode vir de uma escola desativada; se pertencer a outra escola ativa, retorna `409`. Perfil inválido, conta excluída ou corpo inválido gera `400`; escola ou conta inexistente gera `404`.
- `PATCH /api/schools/{id}/users/{userId}`, sem corpo: vincula um aluno ou professor existente e retorna o usuário atualizado (`200`). Alunos sem escola ativa podem ser vinculados novamente e ficam sem turma, para posterior matrícula pela coordenação. Alunos de outra escola ativa devem usar o fluxo de transferência (`409`). Professores mantêm seus demais vínculos e passam a usar a escola informada como ativa. Coordenadores usam a rota específica acima. Contas excluídas ou perfis incompatíveis retornam `400`.

Escolas desativadas somem da listagem administrativa, busca pública e opções de escola no perfil. As contas, turmas, notas, entregas e vínculos históricos são preservados. A escola desativada não pode receber novos cadastros, convites aceitos, transferências ou vínculos; os alunos e profissionais não podem operar suas turmas. Transferências pendentes envolvendo escolas desativadas são preservadas, mas deixam de bloquear novas solicitações.

O login (`user.schoolUnavailable`) e `GET /api/me` (`schoolUnavailable`) retornam `true` quando a escola selecionada foi desativada, com `schoolId` e `classroomId` nulos. O frontend pode usar esse campo para mostrar o aviso e orientar o novo vínculo. Professores com outros vínculos ativos podem alternar pela rota existente `PATCH /api/me/active-school`. Os convites para professores existentes continuam disponíveis. Após selecionar ou receber uma escola ativa, o indicador volta a `false`. O backend não escolhe uma nova escola automaticamente.

## Rodando localmente

### Carga de demonstração

Para adicionar 10 escolas fictícias, 10 coordenadores, 100 professores e 1.000 alunos ao banco configurado, ative o perfil `demo-seed` e configure `DEMO_SEED_PASSWORD` com uma senha válida (mínimo de 8 caracteres, uma maiúscula e um número; máximo de 72 bytes). Não há senha padrão. O administrador continua sendo criado pelo `AdminBootstrap`.

Na hospedagem, configure temporariamente `SPRING_PROFILES_ACTIVE=demo-seed` e `DEMO_SEED_PASSWORD`, mantendo as variáveis normais do banco e do administrador, e inicie a aplicação. Após a carga, remova o perfil e a variável de senha. Localmente, com as mesmas variáveis configuradas, execute `./mvnw spring-boot:run`. A carga grava no banco indicado por `DB_URL`; não cria um banco separado.

Cada escola recebe 1 coordenador, 10 professores e 100 alunos. Todos os endereços usam o CEP de referência `01001000` (Praça da Sé, Sé, São Paulo/SP); as escolas são fictícias. Não são criadas turmas, disciplinas ou atividades. O aluno precisa ser matriculado em turma pela coordenação. Os termos precisam ser aceitos no login.

Os logins seguem `{escola}-{perfil}-{número}@example.invalid`, com escolas de 1 a 10 e perfis `coordenador`, `professor` e `aluno`. Exemplos: `1-coordenador-1@example.invalid`, `1-professor-10@example.invalid`, `1-aluno-100@example.invalid`. O RA é `DEMO-` seguido da parte anterior ao `@`. Todos usam a senha informada na carga. Os e-mails são deliberadamente fictícios e não recebem recuperação de senha ou códigos por e-mail.

A carga roda em uma transação, não altera contas existentes e não duplica uma carga completa de 1.110 contas `DEMO-`. Se encontrar uma quantidade parcial, interrompe a inicialização para revisão. O prefixo `DEMO-` fica reservado para essa carga; não execute a carga simultaneamente em várias instâncias. Esta carga acessa os repositórios diretamente e não consulta o ViaCEP nem gera eventos de auditoria de cadastro.

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
