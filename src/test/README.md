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
ementa e de 1 a 15 tópicos. `GET/POST/DELETE` no caminho
`/api/classrooms/{classroomId}/subjects/{subjectId}/themes` gerencia os temas;
a remoção recebe `?name=...`. Apenas o professor responsável, vinculado à turma e
à mesma escola, pode alterar temas. A API utiliza somente `/subjects` para disciplinas.
A migração V22 acrescenta o conteúdo e padroniza
as tabelas como `subjects`, `subject_topics` e `subject_themes`, preservando os dados.

As regras de Disciplina e Tema acompanham o resumo dos schemas do frontend:
os textos são normalizados antes de verificar os limites; título de tópico e nome
de tema aceitam de 1 a 40 caracteres e exigem uma letra Unicode. Ementa e
referências são strings obrigatórias, mas aceitam texto vazio. Tipos JSON como
número ou booleano são rejeitados nesses campos. Títulos dos tópicos e temas
duplicados são comparados sem diferenciar maiúsculas e minúsculas.

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
CNPJ continua obrigatório e único para escolas; a consulta pública não o expõe.
Campos de endereço ignorados no cadastro pessoal ficam apenas no teste específico
de compatibilidade do frontend. Os testes de auditoria usam `CurrentUser` real.
