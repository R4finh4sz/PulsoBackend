package pulsoescolar_api.exception.activity;

import pulsoescolar_api.exception.common.BusinessConflictException;

public class QuizClosedException extends BusinessConflictException {
    public QuizClosedException() { super("O prazo de entrega deste quiz foi encerrado."); }
}
