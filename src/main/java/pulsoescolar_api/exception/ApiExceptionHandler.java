package pulsoescolar_api.exception;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
 public ProblemDetail concurrentUpdate(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "O cadastro foi alterado por outra operação. Atualize e tente novamente.");
 }
 @ExceptionHandler(EmailDeliveryException.class)
 public ProblemDetail mailDelivery(EmailDeliveryException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
 }
 @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
 public ProblemDetail authentication(org.springframework.security.core.AuthenticationException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
 }
 @ExceptionHandler(ResourceNotFoundException.class)
 public ProblemDetail notFound(ResourceNotFoundException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
 }
 @ExceptionHandler(InvalidUserRoleException.class)
 public ProblemDetail invalidRole(InvalidUserRoleException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
 }
 @ExceptionHandler(DataIntegrityViolationException.class)
 public ProblemDetail conflict(DataIntegrityViolationException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Já existe um cadastro com esse RA, e-mail ou nome, ou o vínculo informado é inválido.");
 }
 @ExceptionHandler(MethodArgumentNotValidException.class)
 public ProblemDetail validation(MethodArgumentNotValidException ex) {
  var detail=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Os dados informados são inválidos.");
  detail.setProperty("errors",ex.getBindingResult().getFieldErrors().stream()
   .map(e->e.getField()+": "+e.getDefaultMessage()).toList());
  return detail;
 }
}
