# Testes

Os pacotes acompanham as áreas do backend:

- `integration/<area>`: testes com contexto Spring, incluindo os fluxos existentes.
- `integration/support`: preparação compartilhada dos novos testes HTTP.
- `controller`, `service`, `security` e `exception`: testes isolados das respectivas camadas.
- `migration`: validação das migrações e das restrições do banco.
- `PulsoescolarApiApplicationTests`: smoke test da aplicação, no mesmo pacote da classe principal.

Executar toda a suíte no PowerShell:

```powershell
.\mvnw.cmd clean test
```

Executar somente os testes HTTP dos controllers:

```powershell
.\mvnw.cmd test '-Dtest=*ControllerIntegrationTest'
```

Para avaliar a feature de disciplinas e temas na branch `feature-subjects`:

```powershell
.\mvnw.cmd test '-Dtest=SubjectControllerIntegrationTest,SubjectValidationTest,SubjectMigrationTests'
```

`GET/POST /api/classrooms/{classroomId}/subjects` lista e cria disciplinas com
ementa e de 1 a 15 tópicos. As rotas `/subjects/{subjectId}/themes` foram removidas.
As atividades usam os títulos dos tópicos da ementa no campo `theme`.
A migração V22 acrescenta o conteúdo e padroniza
as tabelas como `subjects`, `subject_topics` e `subject_themes`, preservando os dados.

Na branch `feature-subject-invitations`, a migração V23 adiciona compartilhamento
de disciplinas, convites internos e notificações. Executar os testes desse fluxo:

```powershell
.\mvnw.cmd test '-Dtest=SubjectInvitationIntegrationTest'
```

| Método | Rota | Resultado |
|---|---|---|
| POST | `/api/classrooms/{classroomId}/subjects/{subjectId}/invitations` | 201; recebe `{ "email": "professor@exemplo.com" }` |
| GET | `/api/subject-invitations` | 200; somente convites do destinatário autenticado |
| PATCH | `/api/subject-invitations/{id}` | 200; recebe `{ "status": "ACCEPTED" }` ou `{ "status": "REJECTED" }` |
| GET | `/api/notifications` | 200; somente notificações do usuário autenticado |
| PATCH | `/api/notifications/{id}/read` | 204; marca como lida, sem aceitar o convite |

O remetente precisa participar da disciplina e estar vinculado à turma. O destinatário
deve ser um professor cadastrado, ativo e da mesma escola. Não existe cadastro por
convite neste fluxo e nenhum e-mail é enviado. Conta inexistente ou excluída retorna
404; perfil ou escola incompatível retorna 403; participante existente ou convite
pendente retorna 409. E-mails são normalizados para minúsculas e sem espaços externos.

Somente o destinatário pode responder. A aceitação revalida escola, conta e vínculo
do remetente, vincula o professor à turma e à disciplina na mesma transação e inclui
seu ID em `teacherIds`. A recusa não concede acesso; um novo convite pode ser enviado
depois dela. Responder novamente retorna 409. Envio e resposta usam bloqueios no
banco para impedir notificações duplicadas ou duas decisões concorrentes. A leitura
da notificação é idempotente e a resposta ao convite também a marca como lida.

Os títulos dos tópicos aceitam de 1 a 40 caracteres e exigem uma letra Unicode.
Ementa e referências são strings obrigatórias, mas aceitam texto vazio.
Títulos duplicados são comparados sem diferenciar maiúsculas e minúsculas.

Os novos testes usam `@SpringBootTest(RANDOM_PORT)`, `RestClient` e o perfil `test`.
O H2 `pulso-integration` tem esquema criado e descartado pelo Hibernate; o Flyway
fica desativado apenas nesse perfil. Os testes antigos continuam validando o
esquema via migrações, com sua configuração original.

Cada cenário cria identidades e turmas únicas por UUID. As identidades de acesso
são fixtures persistidas pelo repository, pois não há cadastro público de ADMIN.
Os tokens são obtidos por HTTP em `/api/auth/login` e confirmados em
`/api/auth/2fa/verify`. Apenas `EmailSender` é mockado; o código é capturado do
e-mail e aguardado por `CompletableFuture` com timeout, sem `Thread.sleep`.
Não há transação no teste HTTP: as escritas são confirmadas pelas transações reais
da aplicação e verificadas por repository ou por uma nova requisição.

Na revisão dos testes anteriores, os cenários de desvínculo passaram a consultar
`/subjects`, com acesso permitido antes da remoção e 403 depois. O domínio usa
`Subject` nas classes e nos pacotes, com tabelas `subjects`, `subject_topics` e
`subject_themes`. A migração V22 preserva os registros das tabelas anteriores.

O campo `syllabus` da resposta reúne os `description` dos tópicos da própria
disciplina, na ordem cadastrada, separados por uma linha em branco. Os tópicos
permanecem disponíveis individualmente. Disciplinas antigas sem tópicos mantêm
a ementa anteriormente armazenada como alternativa.
Escolas são cadastradas com nome, endereço, CEP e UF obrigatórios. O backend consulta o ViaCEP no cadastro e na atualização de localização e rejeita divergências nos campos retornados (ignorando caixa, acentos e espaços repetidos). Campos ausentes no retorno, como logradouro e bairro de CEP genérico, aceitam valores manuais; cidade e UF continuam sendo comparadas. CEP inexistente retorna 400; indisponibilidade do ViaCEP retorna 503 e não libera gravação manual. A atualização de CEP/UF confere também o endereço já salvo.
Campos de endereço ignorados no cadastro pessoal ficam apenas no teste específico
de compatibilidade do frontend. Os testes de auditoria usam `CurrentUser` real.

Ao concluir um quiz, `POST /api/activities/{id}/submissions` calcula e persiste
a nota automaticamente e retorna `score`, `correctAnswers`, `gradedAt` e
`themePerformance`. A rota manual de correção permanece para entregas antigas pendentes.
