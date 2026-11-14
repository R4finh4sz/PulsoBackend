# Envio de e-mails com Resend

`ResendEmailSender` implementa `EmailSender` e usa a API do Resend. O remetente e a chave são configurados por `RESEND_FROM_EMAIL` e `RESEND_API_KEY`. Falhas de envio geram `EmailDeliveryException`; os logs registram somente o tipo de erro, sem credenciais nem conteúdo das mensagens.

## Convites de professores e coordenadores

`InvitationService` cria convites com validade de 48 horas e chama `InvitationMailService.sendInvitation()`. O link abre a página do frontend `/invite/[token]`, onde a pessoa verifica o e-mail e escolhe a própria senha. Nenhuma senha é enviada por e-mail.

A propriedade `app.invitation.base-url` define a URL da página, sem o token. O padrão é `https://pulsoescolar.com.br/invite`; para desenvolvimento, configure por exemplo `http://localhost:3000/invite` no `.env`. Não use `/api/invite`: as rotas `/api/*` do frontend são encaminhadas ao backend.

Ao abrir o convite, `InvitationVerificationService` envia um código de seis dígitos por `InvitationMailService.sendCode()`. O código vale por dez minutos, com intervalo de três minutos para reenvio e limite de cinco tentativas. Após verificar o código e concluir o formulário, o cadastro aguarda aprovação. `RegistrationReviewService` publica a notificação de aprovação ou rejeição por `AccountMailMessages` e registra a criação do coordenador na auditoria quando sua aprovação cria a conta.

O envio do link e do código do convite é síncrono, dentro da transação. Falhas de envio impedem sua confirmação no banco. O provedor e o banco não compartilham uma transação: o provedor pode aceitar uma mensagem antes de uma falha posterior no commit.

## Autenticação e notificações

`TwoFactorService` publica `TwoFactorMailRequested`; após o commit, `TwoFactorMailDispatcher` agenda o envio por `TwoFactorMailService`. O código vale por dez minutos. `NotificationMailDispatcher` usa o mesmo executor para mensagens de aprovação/rejeição de cadastro e recuperação de senha, montadas por `AccountMailMessages`.

O executor tem quatro threads e fila em memória de até cem tarefas. Não há fila persistente nem retry automático; um encerramento abrupto pode perder tarefas. O sucesso da requisição não confirma entrega do e-mail. Mensagens de eventos revertidos não são agendadas.
