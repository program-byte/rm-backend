package io.remedymatch.angebot.domain;

import io.remedymatch.artikel.domain.Artikel;
import io.remedymatch.artikel.domain.ArtikelRepository;
import io.remedymatch.domain.ObjectNotFoundException;
import io.remedymatch.engine.client.EngineClient;
import io.remedymatch.geodaten.geocoding.domain.GeoCalcService;
import io.remedymatch.institution.domain.InstitutionStandort;
import io.remedymatch.institution.domain.InstitutionStandortId;
import io.remedymatch.institution.domain.InstitutionStandortRepository;
import io.remedymatch.user.domain.NotUserInstitutionObjectException;
import io.remedymatch.user.domain.UserService;
import lombok.AllArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static io.remedymatch.angebot.process.AngebotAnfrageProzessConstants.PROZESS_KEY;

@AllArgsConstructor
@Validated
@Service
public class AngebotService {
    private static final String EXCEPTION_MSG_ANGEBOT_NICHT_GEFUNDEN = "Angebot fuer diese Id nicht gefunden: %s";
    private static final String EXCEPTION_MSG_ANGEBOT_NICHT_VON_USER_INSTITUTION = "Angebot gehoert nicht der Institution des angemeldetes Benutzers.";

    private static final String EXCEPTION_MSG_ANGEBOT_ANFRAGE_NICHT_GEFUNDEN = "AngebotAnfrage fuer diese Id nicht gefunden: %s";
    private static final String EXCEPTION_MSG_ANGEBOT_ANFRAGE_NICHT_VON_USER_INSTITUTION = "AngebotAnfrage gehoert nicht der Institution des angemeldetes Benutzers.";


    private final UserService userService;
    private final ArtikelRepository artikelRepository;
    private final InstitutionStandortRepository institutionStandortRepository;

    private final AngebotRepository angebotRepository;
    private final AngebotAnfrageRepository angebotAnfrageRepository;
    private final GeoCalcService geoCalcService;
    private final EngineClient engineClient;

    public List<Angebot> getAlleNichtBedienteAngebote() {
        return mitEntfernung(//
                angebotRepository.getAlleNichtBedienteAngebote(), //
                userService.getContextInstitution().getHauptstandort());
    }

    public List<Angebot> getAngeboteDerUserInstitution() {
        val userInstitution = userService.getContextInstitution();
        return mitEntfernung(//
                angebotRepository.getAngeboteVonInstitution(userInstitution.getId()), //
                userInstitution.getHauptstandort());
    }

    @Transactional
    public Angebot neueAngebotEinstellen(final @NotNull @Valid NeuesAngebot neuesAngebot) {
        Artikel artikel = artikelRepository.get(neuesAngebot.getArtikelId()).get();
        Optional<InstitutionStandort> institutionStandort = institutionStandortRepository
                .get(neuesAngebot.getStandortId());
        if (institutionStandort.isEmpty()) {
            // FIXME: Pruefung darauf, dass es der Stanrort meiner Institution ist
            throw new IllegalArgumentException("InstitutionStandort nicht gefunden");
        }

        return mitEntfernung(angebotRepository.add(Angebot.builder() //
                .anzahl(neuesAngebot.getAnzahl()) //
                .rest(neuesAngebot.getAnzahl()) //
                .artikel(artikel) //
                .institution(userService.getContextInstitution()) //
                .standort(institutionStandort.get()) //
                .haltbarkeit(neuesAngebot.getHaltbarkeit()) //
                .steril(neuesAngebot.isSteril()) //
                .originalverpackt(neuesAngebot.isOriginalverpackt()) //
                .medizinisch(neuesAngebot.isMedizinisch()) //
                .kommentar(neuesAngebot.getKommentar()) //
                .bedient(false) //
                .build()));
    }

    @Transactional
    public void angebotDerUserInstitutionLoeschen(final @NotNull @Valid AngebotId angebotId)
            throws ObjectNotFoundException, NotUserInstitutionObjectException {
        Optional<Angebot> angebot = angebotRepository.get(angebotId);
        if (angebot.isEmpty()) {
            throw new ObjectNotFoundException(String.format(EXCEPTION_MSG_ANGEBOT_NICHT_GEFUNDEN, angebotId));
        }

        if (!userService.isUserContextInstitution(angebot.get().getInstitution().getId())) {
            throw new NotUserInstitutionObjectException(EXCEPTION_MSG_ANGEBOT_NICHT_VON_USER_INSTITUTION);
        }

        // Alle laufende Anfragen stornieren
        angebotAnfrageRepository.storniereAlleOffeneAnfragen(angebotId);

        // TODO Auch Prozesse beenden

        angebotRepository.delete(angebotId);
    }

    @Transactional
    public void angebotAnfrageDerUserInstitutionLoeschen(final @NotNull @Valid AngebotAnfrageId anfrageId)
            throws ObjectNotFoundException, NotUserInstitutionObjectException {
        Optional<AngebotAnfrage> angebotAnfrage = angebotAnfrageRepository.get(anfrageId);
        if (!angebotAnfrage.isPresent()) {
            throw new ObjectNotFoundException(String.format(EXCEPTION_MSG_ANGEBOT_ANFRAGE_NICHT_GEFUNDEN, anfrageId));
        }

        if (!userService.isUserContextInstitution(angebotAnfrage.get().getInstitutionVon().getId())) {
            throw new NotUserInstitutionObjectException(EXCEPTION_MSG_ANGEBOT_ANFRAGE_NICHT_VON_USER_INSTITUTION);
        }

        anfrageStornieren(anfrageId);
    }

    public void angebotAnfrageErstellen(//
                                        final @NotNull @Valid AngebotId angebotId, //
                                        final @NotNull @Valid InstitutionStandortId standortId, //
                                        final @NotBlank String kommentar, //
                                        final @NotNull BigDecimal anzahl) {
        val angebot = angebotRepository.get(angebotId);

        if (angebot.isEmpty()) {
            throw new IllegalArgumentException("Angebot ist nicht vorhanden");
        }

        InstitutionStandort standort = null;

        val userInstitution = userService.getContextInstitution();
        if (userInstitution.getHauptstandort().getId().equals(standortId)) {
            standort = userInstitution.getHauptstandort();
        } else {
            var foundStandort = userInstitution.getStandorte().stream().filter(s -> s.getId().equals(standortId))
                    .findFirst();

            if (foundStandort.isPresent()) {
                standort = foundStandort.get();
            }
        }

        if (standort == null) {
            throw new IllegalArgumentException("Der ausgewählte Standort konnte nicht geunden werden");
        }

        var anfrage = AngebotAnfrage.builder() //
                .angebot(angebot.get()) //
                .institutionVon(userInstitution) //
                .standortVon(standort) //
                .anzahl(anzahl) //
                .kommentar(kommentar) //
                .status(AngebotAnfrageStatus.Offen) //
                .build();

        anfrage = angebotAnfrageRepository.add(anfrage);

        var variables = new HashMap<String, Object>();
        variables.put("institution", angebot.get().getInstitution().getId().getValue().toString());
        variables.put("objektId", anfrage.getId().getValue().toString());

        val prozessInstanzId = engineClient.prozessStarten(PROZESS_KEY, anfrage.getId().getValue().toString(), variables);
        anfrage.setProzessInstanzId(prozessInstanzId);
        angebotAnfrageRepository.update(anfrage);
    }

    @Transactional
    @Deprecated
    public void angebotMelden(Angebot angebot) {
        // sollte geloescht werden

        angebot.setInstitution(userService.getContextInstitution());
        angebot.setRest(angebot.getAnzahl());
        angebotRepository.add(angebot);
    }

    @Transactional
    public void anfrageStornieren(final @NotNull @Valid AngebotAnfrageId anfrageId) {
        val anfrage = angebotAnfrageRepository.get(anfrageId);
        if (anfrage.isEmpty()) {
            throw new IllegalArgumentException("Anfrage nicht vorhanden");
        }
        anfrage.get().setStatus(AngebotAnfrageStatus.Storniert);
        angebotAnfrageRepository.update(anfrage.get());
    }

    @Transactional
    public void anfrageAnnehmen(final @NotNull @Valid AngebotAnfrageId anfrageId) {
        val anfrage = angebotAnfrageRepository.get(anfrageId);
        if (anfrage.isEmpty()) {
            throw new IllegalArgumentException("Anfrage nicht vorhanden");
        }
        anfrage.get().setStatus(AngebotAnfrageStatus.Angenommen);

        // Angebot als bedient markieren
        val angebot = anfrage.get().getAngebot();

        // Restbestand des Angebots herabsetzen oder Exception werfen,
        // wenn die Anfrage größer als das Angebot ist
        if (anfrage.get().getAnzahl().doubleValue() > angebot.getRest().doubleValue()) {
            anfrage.get().setStatus(AngebotAnfrageStatus.Storniert);
            angebotAnfrageRepository.update(anfrage.get());
            throw new IllegalArgumentException("Nicht genügend Ware auf Lager");
        } else {
            if (anfrage.get().getAnzahl().doubleValue() == angebot.getRest().doubleValue()) {
                angebot.setBedient(true);
                angebot.setRest(BigDecimal.ZERO);
            } else {
                angebot.setRest(
                        BigDecimal.valueOf(angebot.getRest().doubleValue() - anfrage.get().getAnzahl().doubleValue()));
            }
        }

        angebotRepository.update(angebot);
        angebotAnfrageRepository.update(anfrage.get());
    }

    /* help methods */

    private List<Angebot> mitEntfernung(final List<Angebot> angebote, final InstitutionStandort userHauptstandort) {
        angebote.forEach(angebot -> angebot.setEntfernung(berechneEntfernung(//
                userHauptstandort, //
                angebot.getStandort())));

        return angebote;
    }

    private Angebot mitEntfernung(final Angebot angebot) {
        angebot.setEntfernung(berechneEntfernung(angebot.getStandort()));
        return angebot;
    }

    private BigDecimal berechneEntfernung(final InstitutionStandort angebotStandort) {
        return berechneEntfernung(//
                userService.getContextInstitution().getHauptstandort(), //
                angebotStandort);
    }

    private BigDecimal berechneEntfernung(//
                                          final InstitutionStandort userHauptstandort, //
                                          final InstitutionStandort angebotStandort) {
        return geoCalcService.berechneDistanzInKilometer(userHauptstandort, angebotStandort);
    }

//	private void anfrageStornierenX(final AngebotAnfrageId anfrageId) {
//		val anfrage = angebotAnfrageRepository.get(anfrageId);
//
//		if (anfrage.isEmpty()) {
//			throw new IllegalArgumentException("Anfrage ist nicht vorhanden und kann deshalb nicht storniert werden");
//		}
//
//		AngebotAnfrage angebotAnfrage = anfrage.get();
//		if (!AngebotAnfrageStatus.Offen.equals(angebotAnfrage.getStatus())) {
//			throw new IllegalArgumentException(
//					"Eine Anfrage, die nicht im Status offen ist, kann nicht storniert werden");
//		}
//
//		angebotAnfrage.setStatus(AngebotAnfrageStatus.Storniert);
//		angebotAnfrageRepository.update(angebotAnfrage);
//
//		engineClient.messageKorrelieren(angebotAnfrage.getProzessInstanzId(), ANFRAGE_STORNIEREN_MESSAGE,
//				new HashMap<>());
//	}
}
