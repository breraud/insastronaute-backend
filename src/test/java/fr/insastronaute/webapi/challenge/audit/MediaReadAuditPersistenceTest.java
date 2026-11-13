package fr.insastronaute.webapi.challenge.audit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class MediaReadAuditPersistenceTest {
 @Test void eventIdMakesPersistenceIdempotent() {
  ModerationAuditRepository repository=mock(ModerationAuditRepository.class);
  UUID eventId=UUID.randomUUID();
  when(repository.existsById(eventId)).thenReturn(false, true);
  ModerationAuditService service=new ModerationAuditService(repository,new ObjectMapper(),mock(fr.insastronaute.webapi.user.repository.UserRepository.class));
  service.recordMediaRead(eventId,UUID.randomUUID(),"a".repeat(64),0,42);
  service.recordMediaRead(eventId,UUID.randomUUID(),"a".repeat(64),0,42);
  verify(repository,times(1)).save(any());
 }
}
