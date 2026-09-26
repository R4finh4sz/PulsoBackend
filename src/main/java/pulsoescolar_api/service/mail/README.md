# Envio de e-mails com MailerSend

O Pulso Escolar utiliza o MailerSend para enviar códigos de autenticação (2FA) e mensagens de cadastro. A integração implementada usa **SMTP**, por meio do `JavaMailSender` do Spring. O backend não chama a API REST do MailerSend: as rotas HTTP descritas abaixo pertencem ao próprio Pulso Escolar.

O endereço `app.mailersend.com` é o painel de administração do provedor. O envio utiliza `smtp.mailersend.net`, porta `587`, com autenticação SMTP e STARTTLS obrigatório. A configuração fica em `application.properties`, que carrega as variáveis locais da `.env`. Este documento não contém credenciais, tokens ou endereços reais de remetentes e destinatários.

## Componentes e responsabilidades

| Componente | Responsabilidade |
|---|---|
| `LoginController` | Expõe as rotas de login, verificação, reenvio e logout. |
| `LoginService` | Valida as credenciais, cria a sessão pendente de 2FA e retorna o JWT. |
| `TwoFactorService` | Gera e valida o código, controla expiração, tentativas e intervalo de reenvio. |
| `TwoFactorMailRequested` | Transporta destinatário e código em memória; seu `toString()` oculta esses dados. |
| `TwoFactorMailDispatcher` | Após o commit da transação, agenda o envio em segundo plano. |
| `MailExecutorConfig` | Define o executor dedicado aos e-mails de 2FA. |
| `TwoFactorMailService` | Monta a mensagem de autenticação e solicita o envio SMTP. |
| `WelcomeMailService` | Monta e envia a mensagem com o acesso inicial do usuário cadastrado. |

## Rotas utilizadas no fluxo

| Método e rota | Comportamento | Envia e-mail? |
|---|---|---|
| `POST /api/auth/login` | Recebe `email` e `password`. Retorna `200` com `accessToken`, `tokenType`, `expiresAt`, `user`, `twoFactorRequired`, `codeExpiresAt` e `resendAvailableAt`. | Sim, código de 2FA em segundo plano. |
| `POST /api/auth/2fa/verify` | Recebe `code` no corpo e o JWT pendente no cabeçalho `Authorization: Bearer <token>`. Retorna `204` ao confirmar. | Não. A validação ocorre no backend. |
| `POST /api/auth/2fa/resend` | Recebe o JWT pendente, sem corpo. Retorna `200` com os novos prazos e `twoFactorRequired`. | Sim, novo código em segundo plano. |
| `POST /api/auth/logout` | Recebe o JWT e revoga a sessão. Retorna `204`. | Não. |
| `POST /api/students` | Cadastra um estudante conforme as permissões e vínculos de escola. Retorna `201`. | Sim, mensagem de cadastro. |
| `POST /api/teachers` | Cadastra um professor conforme as permissões e vínculos de escola. Retorna `201`. | Sim, mensagem de cadastro. |
| `POST /api/coordinators` | Cadastra um coordenador conforme as permissões e vínculos de escola. Retorna `201`. | Sim, mensagem de cadastro. |

O frontend chama essas rotas do backend; ele não se conecta diretamente ao SMTP. O projeto não expõe uma rota genérica para enviar e-mails arbitrários.

## Como funciona o login com e-mail

1. O frontend envia e-mail e senha para `POST /api/auth/login`.
2. O backend valida as credenciais e prepara uma sessão com 2FA pendente.
3. `TwoFactorService.issue()` gera um código aleatório de seis dígitos. Apenas seu hash é persistido na sessão; o código original segue em memória para o envio.
4. O serviço publica `TwoFactorMailRequested`. Após a confirmação da transação no banco, o dispatcher coloca a tarefa no executor. Se a transação sofrer rollback, esse envio não é agendado.
5. O login retorna sem aguardar o SMTP. O frontend deve abrir a tela do código quando receber `twoFactorRequired: true`.
6. Em segundo plano, `TwoFactorMailService` monta um `SimpleMailMessage`, com remetente configurado, destinatário, assunto e texto, e chama `sender.send(message)`.
7. O `JavaMailSender` usa a configuração SMTP para entregar a mensagem ao MailerSend, que processa a entrega ao destinatário.
8. O usuário informa o código em `POST /api/auth/2fa/verify`. Após a confirmação, o mesmo token permite o acesso conforme o perfil do usuário.

O ponto que dispara o serviço, em `TwoFactorMailDispatcher`, é:

```java
mail.send(event.email(), event.code());
```

O ponto que efetivamente solicita o envio SMTP, em `TwoFactorMailService`, é:

```java
sender.send(message);
```

O assunto e o conteúdo são definidos por `message.setSubject(...)` e `message.setText(...)`. Atualmente o e-mail é texto simples, sem template HTML.

## Prazos, proteção e respostas

- O código vale por 10 minutos a partir da geração, não da chegada do e-mail.
- Há um limite de cinco tentativas de verificação por código.
- O intervalo para solicitar outro código é de 3 minutos e também é considerado em novas tentativas de login enquanto o bloqueio de reenvio estiver ativo.
- Um reenvio substitui o hash anterior, renova os prazos e zera as tentativas.
- Credenciais inválidas no login resultam em `401`. Solicitação antes do prazo de reenvio resulta em `429`.
- Código inválido, expirado ou com limite de tentativas atingido resulta em `400`.
- Enquanto o 2FA estiver pendente, o token permite confirmar, reenviar ou encerrar a sessão; as rotas de negócio permanecem bloqueadas.
- O código não é retornado pela API. Os logs de falha de envio registram tipos de exceção, sem incluir o corpo da mensagem ou credenciais SMTP.

## Envio em segundo plano e limitações

O executor possui 4 threads e uma fila em memória para até 100 tarefas aguardando execução. Isso evita que a espera pelo SMTP prenda as requisições de login e reenvio. No encerramento normal, o executor está configurado para aguardar até 30 segundos pela conclusão das tarefas.

Se houver falha no SMTP ou no agendamento, o dispatcher registra um aviso. A sessão já confirmada no banco continua pendente de 2FA; o usuário pode solicitar reenvio após `resendAvailableAt`. A resposta `200` do login não confirma que o e-mail foi enviado ou entregue.

Não há retry automático nem fila persistente. Um encerramento abrupto pode perder tarefas em memória, e uma fila cheia pode rejeitar novos envios. A aceitação de uma mensagem pelo SMTP também não garante sua chegada à caixa de entrada. O código atual não recebe webhooks de entrega do MailerSend.

Os timeouts SMTP configurados são 5 segundos para conexão e 10 segundos para leitura e escrita. Eles não constituem um limite único para a duração total do envio.

## E-mail de cadastro

`UserRegistrationService` gera a senha inicial e chama `WelcomeMailService`. A mensagem contém nome, e-mail e senha inicial; o banco armazena o hash da senha.

Esse envio é síncrono e ocorre dentro da transação de cadastro. Se houver falha de envio, o serviço lança `EmailDeliveryException`, a API retorna `503` e a transação é revertida. SMTP e banco não compartilham uma transação: uma falha no commit após a aceitação do e-mail pode deixar uma mensagem enviada sem o cadastro persistido.

A troca de senha é opcional e está separada do aceite dos termos. `PATCH /api/auth/password` e `POST /api/terms/accept` não enviam e-mail.

## Explicação para apresentação

“Nosso backend integra o MailerSend por SMTP usando o JavaMailSender do Spring. O frontend chama nossas rotas de login e confirmação. Depois de validar a senha e salvar a sessão, o backend envia o código de autenticação em segundo plano, sem fazer a requisição esperar pelo provedor. O acesso só é liberado quando o código é confirmado. Também usamos o MailerSend para enviar o acesso inicial dos usuários cadastrados.”
