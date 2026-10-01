package pulsoescolar_api.exception;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(BusinessValidationException.class)
 public ProblemDetail businessValidation(BusinessValidationException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
 }
 @ExceptionHandler(BusinessConflictException.class)
 public ProblemDetail businessConflict(BusinessConflictException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
 }
 @ExceptionHandler(OperationNotAllowedException.class)
 public ProblemDetail operationNotAllowed(OperationNotAllowedException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
 }
 @ExceptionHandler(VerificationCooldownException.class)
 public ProblemDetail verificationCooldown(VerificationCooldownException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
 }
 @ExceptionHandler(InvitationUnavailableException.class)
 public ProblemDetail invitationUnavailable(InvitationUnavailableException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
 }
 @ExceptionHandler(RegistrationPhotoTooLargeException.class)
 public ProblemDetail photoTooLarge(RegistrationPhotoTooLargeException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, ex.getMessage());
 }
 @ExceptionHandler(SessionUnavailableException.class)
 public ProblemDetail sessionUnavailable(SessionUnavailableException ex) {
  return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
 }
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
