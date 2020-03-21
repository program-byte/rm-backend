package io.remedymatch.bedarf.api;

import io.remedymatch.bedarf.domain.BedarfService;
import io.remedymatch.person.domain.PersonRepository;
import io.remedymatch.web.UserNameProvider;
import lombok.AllArgsConstructor;
import lombok.val;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.remedymatch.bedarf.api.BedarfMapper.mapToEntity;

@RestController
@AllArgsConstructor
@RequestMapping("/bedarf")
public class BedarfController {

    private final BedarfService bedarfService;
    private final UserNameProvider userNameProvider;
    private final PersonRepository personRepository;

    @GetMapping()
    @Secured("ROLE_admin")
    public ResponseEntity<List<BedarfDTO>> bedarfLaden() {
        val institutions = StreamSupport.stream(bedarfService.alleBedarfeLaden().spliterator(), false)
                .map(BedarfMapper::mapToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(institutions);
    }

    @PostMapping("/melden")
    public ResponseEntity<Void> bedarfMelden(@RequestBody BedarfDTO bedarf) {
        val user = personRepository.findByUserName(userNameProvider.getUserName());
        bedarfService.bedarfMelden(mapToEntity(bedarf), user.getInstitution());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> bedarfLoeschen(@PathVariable("id") String bedarfId) {
        bedarfService.bedarfLoeschen(UUID.fromString(bedarfId));
        return ResponseEntity.ok().build();
    }

}
