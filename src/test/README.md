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

Executar somente os 16 novos cenários HTTP:

```powershell
.\mvnw.cmd test '-Dtest=*ControllerIntegrationTest'
```

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
`/school-courses`, com acesso permitido antes da remoção e 403 depois. A rota
`/subjects` continua apenas no teste da rota removida e nas migrações históricas.
CNPJ continua obrigatório e único para escolas; a consulta pública não o expõe.
Campos de endereço ignorados no cadastro pessoal ficam apenas no teste específico
de compatibilidade do frontend. Os testes de auditoria usam `CurrentUser` real.
