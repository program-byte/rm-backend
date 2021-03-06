package io.remedymatch.angebot.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.remedymatch.angebot.domain.AngebotAnfrageStatus;

public interface AngebotAnfrageJpaRepository extends JpaRepository<AngebotAnfrageEntity, UUID> {

	List<AngebotAnfrageEntity> findAllByAngebot_Institution_Id(UUID id);
	
	List<AngebotAnfrageEntity> findAllByInstitutionVon_Id(UUID id);
	
	@Modifying
    @Query("UPDATE AngebotAnfrage a SET a.status=:statusNeu WHERE a.angebot.id = :angebotId AND a.status=:statusAlt")
    void updateStatus(//
    		@Param("angebotId") UUID angebotId, //
    		@Param("statusAlt") AngebotAnfrageStatus statusAlt, //
    		@Param("statusNeu") AngebotAnfrageStatus statusNeu);
}
