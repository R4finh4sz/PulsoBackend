package pulsoescolar_api;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pulsoescolar_api.service.*;
import pulsoescolar_api.repository.UserRepository;
import pulsoescolar_api.entity.*;
import pulsoescolar_api.dto.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class SchoolFlowTests {
 @Autowired UserRegistrationService registration;
 @Autowired ClassroomService classrooms;
 @Autowired StudentEnrollmentService enrollment;
 @Autowired TeacherAssignmentService assignment;
 @Autowired SubjectService subjects;
 @Autowired UserRepository users;
 @Autowired PasswordEncoder encoder;
 @Autowired org.springframework.web.context.WebApplicationContext context;

 @Test void httpRoutesPreserveValidationAuthorizationAndErrors() throws Exception {
  var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
   .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
   .build();
  var coordinator = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("coord@example.com");
  var csrf = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/classrooms")
    .with(coordinator).with(csrf).contentType("application/json").content("{\"name\":\"3 year\",\"identifier\":\"C\"}"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/classrooms").with(coordinator))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/classrooms")
    .with(coordinator).with(csrf).contentType("application/json").content("{\"name\":\"\"}"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/classrooms/999999/subjects")
    .with(coordinator))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
  login("coord@example.com");
  var teacher = registration.createUser(request("httpTeacher"), Role.TEACHER);
  var room = classrooms.createClassroom(new CreateClassroomRequest("HTTP room", "A"));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
     "/api/classrooms/" + room.id() + "/students/" + teacher.id()).with(coordinator).with(csrf))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
     "/api/classrooms/" + room.id() + "/subjects")
    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(teacher.email())))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
 }
 @BeforeEach void setup() {
  var coordinator=new SchoolUser();
  coordinator.setFullName("Coordinator"); coordinator.setRa("COORD");
  coordinator.setEmail("coord@example.com"); coordinator.setPasswordHash(encoder.encode("password12345"));
  coordinator.setRole(Role.PEDAGOGICAL_COORDINATOR); users.save(coordinator);
  login("coord@example.com");
 }
 @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
 void login(String email) {
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,"",java.util.List.of()));
 }
 CreateUser request(String name) { return new CreateUser(name,name,name+"@example.com","password12345"); }
 @Test void studentsInheritSubjectsIncludingLateEnrollmentAndTransfer() {
  var room=classrooms.createClassroom(new CreateClassroomRequest("3 year", "B"));
  var other=classrooms.createClassroom(new CreateClassroomRequest("3 year", "A"));
  var teacher=registration.createUser(request("teacher"),Role.TEACHER);
  var student=registration.createUser(request("student"),Role.STUDENT);
  assignment.assign(room.id(),teacher.id()); assignment.assign(other.id(),teacher.id());
  login(teacher.email());
  assertEquals(2,classrooms.listClassrooms().size());
  subjects.createSubject(room.id(),new NameRequest("Mathematics"));
  login("coord@example.com"); enrollment.enroll(room.id(),student.id());
  login(student.email()); assertEquals(1,subjects.subjects(room.id()).size());
  assertThrows(AccessDeniedException.class,()->subjects.subjects(other.id()));
  assertThrows(AccessDeniedException.class,()->subjects.createSubject(room.id(),new NameRequest("History")));
  login("coord@example.com"); enrollment.enroll(other.id(),student.id());
  login(student.email()); assertTrue(subjects.subjects(other.id()).isEmpty());
  assertThrows(AccessDeniedException.class,()->subjects.subjects(room.id()));
 }
 @Test void unassignedTeachersCannotAccessOrCreateSubjects() {
  var room=classrooms.createClassroom(new CreateClassroomRequest("3 year", "B"));
  var teacher=registration.createUser(request("teacher"),Role.TEACHER);
  login(teacher.email());
  assertTrue(classrooms.listClassrooms().isEmpty());
  assertThrows(AccessDeniedException.class,()->subjects.subjects(room.id()));
  assertThrows(AccessDeniedException.class,()->subjects.createSubject(room.id(),new NameRequest("Math")));
  assertThrows(AccessDeniedException.class,()->registration.createUser(request("student"),Role.STUDENT));
 }
 @Test void coordinatorCannotCreateAdminOrCoordinatorAndPasswordsAreHashed() {
  assertThrows(AccessDeniedException.class,()->registration.createUser(request("admin"),Role.ADMIN));
  assertThrows(AccessDeniedException.class,()->registration.createUser(request("coord2"),Role.PEDAGOGICAL_COORDINATOR));
  var teacher=registration.createUser(request("teacher"),Role.TEACHER);
  assertTrue(encoder.matches("password12345",users.findById(teacher.id()).orElseThrow().getPasswordHash()));
 }
}
