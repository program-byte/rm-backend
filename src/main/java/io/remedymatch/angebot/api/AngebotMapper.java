package io.remedymatch.angebot.api;

import io.remedymatch.angebot.domain.AngebotEntity;
import io.remedymatch.institution.api.InstitutionStandortMapper;

import static io.remedymatch.artikel.api.ArtikelMapper.getArticleDTO;
import static io.remedymatch.artikel.api.ArtikelMapper.getArticleEntity;

public class AngebotMapper {

    public static AngebotDTO mapToDTO(AngebotEntity entity) {
        var builder = AngebotDTO.builder()
                .id(entity.getId())
                .anzahl(entity.getAnzahl())
                .artikel(getArticleDTO(entity.getArtikel()))
                .haltbarkeit(entity.getHaltbarkeit())
                .medizinisch(entity.isMedizinisch())
                .originalverpackt(entity.isOriginalverpackt())
                .standort(InstitutionStandortMapper.mapToDTO(entity.getStandort()))
                .steril(entity.isSteril())
                .bedient(entity.isBedient())
                .rest(entity.getRest())
                .kommentar(entity.getKommentar());

        if (entity.getInstitution() != null) {
            builder = builder.institutionId(entity.getInstitution().getId());
        }
        return builder.build();
    }

    public static AngebotEntity mapToEntity(AngebotDTO dto) {
        var builder = AngebotEntity.builder()
                .id(dto.getId())
                .anzahl(dto.getAnzahl())
                .artikel(getArticleEntity(dto.getArtikel()))
                .haltbarkeit(dto.getHaltbarkeit())
                .medizinisch(dto.isMedizinisch())
                .originalverpackt(dto.isOriginalverpackt())
                .standort(InstitutionStandortMapper.mapToEntity(dto.getStandort()))
                .steril(dto.isSteril())
                .bedient(dto.isBedient())
                .kommentar(dto.getKommentar());
        return builder.build();
    }
}