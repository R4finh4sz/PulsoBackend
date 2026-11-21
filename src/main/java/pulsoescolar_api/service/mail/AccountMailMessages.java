package pulsoescolar_api.service.mail;

import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.RegistrationStatus;

public final class AccountMailMessages {
    private static final String SIGNATURE = "\n\nEquipe PulsoEscolar";
    private AccountMailMessages() {}

    public static NotificationMailRequested reviewed(String email, Role role, RegistrationStatus status, String reason) {
        String profile = switch (role) {
            case PEDAGOGICAL_COORDINATOR -> "coordenador";
            case TEACHER -> "professor";
            case STUDENT -> "aluno";
            default -> throw new IllegalArgumentException("Perfil inválido para análise de cadastro.");
        };
        if (status == RegistrationStatus.APPROVED) {
            String guidance = switch (role) {
                case PEDAGOGICAL_COORDINATOR -> "Acesse o sistema para acompanhar as atividades da sua equipe e apoiar o trabalho de professores e alunos.";
                case TEACHER -> "Acesse o sistema para continuar seu trabalho, acompanhar suas turmas e contribuir para o aprendizado dos alunos.";
                default -> "Acesse o sistema para acompanhar suas atividades e continuar seus estudos. Estamos felizes em fazer parte da sua jornada!";
            };
            return new NotificationMailRequested(email, "Seu acesso ao PulsoEscolar foi aprovado!",
                    "Parabéns!\n\nSeu cadastro como " + profile
                    + " foi aprovado e seu acesso ao PulsoEscolar está liberado.\n\n" + guidance + SIGNATURE);
        }
        if (status != RegistrationStatus.REJECTED) throw new IllegalArgumentException("Análise ainda não concluída.");
        String detail = reason == null || reason.isBlank() ? "" : "\n\nMotivo: " + reason.strip();
        return new NotificationMailRequested(email, "Atualização sobre seu cadastro no PulsoEscolar",
                "Olá!\n\nApós a análise da equipe responsável, seu cadastro como " + profile
                + " não foi aprovado neste momento." + detail
                + "\n\nPara esclarecer dúvidas ou receber orientações sobre seu cadastro, entre em contato com a equipe responsável na sua instituição." + SIGNATURE);
    }

    public static NotificationMailRequested guardianInvitation(String email, String guardianName, String studentName,
                                                               String schoolName, String link, boolean renewal) {
        if (renewal) {
            return new NotificationMailRequested(email, "Atualização dos documentos do responsável — PulsoEscolar",
                    "Olá, " + guardianName + "!\n\nPara liberar o acesso de " + studentName + " no PulsoEscolar, "
                    + "leia os documentos atualizados e registre sua decisão no link abaixo. "
                    + "O link é válido por 48 horas e pode ser usado uma vez.\n\n" + link + SIGNATURE);
        }
        return new NotificationMailRequested(email, "Confirmação de responsável legal — PulsoEscolar",
                "Olá, " + guardianName + "!\n\nO(a) estudante " + studentName + " está se cadastrando em " + schoolName
                + " no PulsoEscolar e indicou você como responsável legal. A escola conferiu este contato."
                + "\n\nAcesse o link abaixo para ler os termos e registrar sua decisão. Abrir o e-mail ou o link não confirma nada: "
                + "a confirmação só acontece quando você a registrar na página."
                + "\n\n" + link
                + "\n\nO link é válido por 48 horas e só pode ser usado uma vez. Se você não reconhece este estudante, "
                + "acesse o link e escolha a opção correspondente, ou ignore este e-mail." + SIGNATURE);
    }

    public static NotificationMailRequested guardianCorrection(String email, String reason, String link) {
        return new NotificationMailRequested(email, "Corrija os dados do seu responsável — PulsoEscolar",
                "Olá!\n\nA escola precisa que você informe ou atualize os dados do responsável legal do seu cadastro."
                + "\n\nMotivo: " + reason
                + "\n\nAcesse o link abaixo para informar novamente o nome e o e-mail do seu responsável legal:\n\n" + link
                + "\n\nO link é válido por 7 dias e só pode ser usado uma vez. Seu cadastro continua pendente até a conferência."
                + SIGNATURE);
    }

    public static NotificationMailRequested recoveryCode(String email, String code) {
        return new NotificationMailRequested(email, "Código para redefinir sua senha — PulsoEscolar",
                "Olá!\n\nRecebemos uma solicitação para redefinir a senha da sua conta no PulsoEscolar."
                + "\n\nSeu código de verificação é: " + code
                + "\n\nDigite esse código na tela de recuperação de senha para continuar. Ele é válido por 10 minutos e pode ser utilizado apenas uma vez."
                + "\n\nNão compartilhe este código. Se você não solicitou a alteração, ignore este e-mail." + SIGNATURE);
    }

    public static NotificationMailRequested passwordReset(String email) {
        return new NotificationMailRequested(email, "Sua senha do PulsoEscolar foi alterada",
                "Olá!\n\nSua senha foi redefinida com sucesso. Você já pode acessar o PulsoEscolar usando sua nova senha."
                + "\n\nSe você não realizou essa alteração, entre em contato com o suporte da sua instituição imediatamente." + SIGNATURE);
    }
}
