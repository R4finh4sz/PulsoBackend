package pulsoescolar_api.security.classroom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.school.School;
import pulsoescolar_api.entity.user.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassroomAccessPolicyTest {
    private final ClassroomAccessPolicy policy = new ClassroomAccessPolicy();
    private final Classroom classroom = sala(1L);

    private static Classroom sala(Long id) {
        var sala = new Classroom();
        sala.setId(id);
        sala.setSchool(escola(1L));
        return sala;
    }

    private static School escola(Long id) {
        var escola = new School();
        escola.setId(id);
        return escola;
    }

    private SchoolUser usuario(Role role) {
        var user = new SchoolUser();
        user.setId(10L);
        user.setRole(role);
        user.setSchool(escola(2L));
        return user;
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "TEACHER", "STUDENT"})
    void devePermitirAdministradorProfessorVinculadoEAlunoDaSala(Role role) {
        // Arrange
        var user = usuario(role);
        user.setClassroom(sala(1L));
        var professor = usuario(Role.TEACHER);
        classroom.getTeachers().add(professor);
        // Act
        // Assert
        assertDoesNotThrow(() -> policy.requireAccess(classroom, user));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "TEACHER", "PEDAGOGICAL_COORDINATOR"})
    void deveNegarUsuarioSemVinculoComSalaOuEscola(Role role) {
        // Arrange
        var user = usuario(role);
        user.setClassroom(sala(2L));
        // Act
        var erro = assertThrows(AccessDeniedException.class, () -> policy.requireAccess(classroom, user));
        // Assert
        assertEquals("Você não tem permissão para acessar esta sala.", erro.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void devePermitirCoordenadorQuandoUmaEscolaForNula(boolean escolaDoUsuarioNula) {
        // Arrange
        var user = usuario(Role.PEDAGOGICAL_COORDINATOR);
        if (escolaDoUsuarioNula) user.setSchool(null);
        else classroom.setSchool(null);
        // Act
        // Assert
        assertDoesNotThrow(() -> policy.requireAccess(classroom, user));
    }

    @Test
    void deveNegarAlunoSemSala() {
        // Arrange
        var user = usuario(Role.STUDENT);
        // Act
        var erro = assertThrows(AccessDeniedException.class, () -> policy.requireAccess(classroom, user));
        // Assert
        assertEquals("Você não tem permissão para acessar esta sala.", erro.getMessage());
    }

    @Test
    void deveNegarConsultaDeAlunosMesmoParaAlunoDaSala() {
        // Arrange
        var user = usuario(Role.STUDENT);
        user.setClassroom(classroom);
        // Act
        var erro = assertThrows(AccessDeniedException.class, () -> policy.requireRosterAccess(classroom, user));
        // Assert
        assertEquals("A consulta de alunos exige acesso de administrador, coordenador pedagógico ou professor vinculado à sala.", erro.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "STUDENT", "PEDAGOGICAL_COORDINATOR"})
    void deveNegarOperacaoDeProfessorParaOutroPapel(Role role) {
        // Arrange
        var user = usuario(role);
        classroom.getTeachers().add(user);
        // Act
        var erro = assertThrows(AccessDeniedException.class, () -> policy.requireAssignedTeacher(classroom, user));
        // Assert
        assertEquals("Esta operação exige um professor vinculado à sala.", erro.getMessage());
    }
}
