package io.remedymatch.angebot.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MengenTyp {

    STUECK("STUECK"),
    LITER("LITER");

    private String value;

}