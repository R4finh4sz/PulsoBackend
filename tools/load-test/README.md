# Teste de carga – Pulso Escolar API

Script: `loadtest.mjs` (Node 18+, sem dependências). Não altera código nem regras da API.

## Cenários
| Cenário | Requisição | Perfil |
|---|---|---|
| student_activities | `GET /api/student/activities` | aluno |
| quiz_open | `GET /api/activities/{id}/quiz` | aluno |
| submit_first | `POST /api/activities/{id}/submissions` (atividade pendente) | aluno |
| submit_duplicate | mesmo POST em atividade já entregue (regra de entrega única) | aluno |
| teacher_report / teacher_activities | `GET /api/teacher/reports`, `GET /api/teacher/activities` | professor |

Cada usuário virtual repete ações com pausa aleatória (1–3 s). Resultados são classificados em **2xx**, **429**, **rejeição esperada** (4xx nos POSTs de entrega, por causa da regra de entrega única) e **falha** (5xx, timeout, rede, outros 4xx).

## Preparação (você, uma vez)
1. Crie dados **fictícios** em produção: escola, turma, disciplina, 1+ professor(es) e N alunos (aprovados, **sem 2FA**), e **uma ou mais atividades** (quiz de 10 questões) na turma dos alunos. Cada aluno só entrega cada atividade uma vez; para mais envios válidos, crie mais atividades.
2. Copie `accounts.example.csv` para `accounts.csv` e preencha (`role,email,password`). O arquivo é ignorado pelo git.

## Limites do servidor que afetam o teste
- Login: 10 req/min **por IP de origem** (`/api/auth/*`). O script autentica a 8/min, então 400 contas levam ~50 min; o JWT dura 50 min (`JWT_SESSION_TTL`). Logo, no máximo ~360 contas ficam com token válido durante todo o teste. Se a API estiver atrás de proxy, o IP visto pode ser o do proxy, e **logins de usuários reais podem receber 429 durante a fase de login**.
- Demais rotas: 120 req/min **por usuário**. Com 1000 usuários virtuais compartilhando poucas contas, muitos 429 são esperados (e reportados à parte).
- Com mais usuários virtuais do que contas, vários VUs usam a mesma conta (conexões concorrentes, não 1000 identidades distintas).

## Execução
```bash
cd tools/load-test
node loadtest.mjs --dry-run                # valida configuração, sem tráfego
node loadtest.mjs --confirm-production     # executa (padrão: https://api.pulsoescolar.com.br)
```
Variáveis opcionais: `BASE_URL`, `ACCOUNTS_FILE`, `STAGES` (padrão `50:120,100:120,250:180,500:180,1000:300` = usuários:segundos), `THINK_MIN_MS`, `THINK_MAX_MS`, `TEACHER_VU_RATIO` (0.05), `LOGIN_PER_MIN` (8), `TOKEN_TTL_MIN` (50), `REQUEST_TIMEOUT_MS` (30000).

**Parada de segurança:** se, numa janela de 10 s, 5xx+falhas de rede passarem de 20% ou o P95 dos 2xx passar de 10 s, a carga é interrompida e isso consta no relatório (`--no-abort` desativa).

## Saída (`results/<data>/`)
`report.md` (tabelas por estágio e cenário), `results.json`, `requests.csv` (uma linha por requisição).

## Métricas do servidor
O script **não** acessa CPU, memória nem conexões do banco (`/actuator/**` é bloqueado e não há acesso à hospedagem). Anote-as manualmente no painel da Square Cloud durante a execução e acrescente ao relatório.

## Seção “Resultados do teste de carga”
Só deve ser escrita depois da execução, a partir de `report.md`. Até lá não há resultados medidos neste repositório.
