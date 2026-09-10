package pulsoescolar_api.exception;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
@RestControllerAdvice
public class ApiExceptionHandler {
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
