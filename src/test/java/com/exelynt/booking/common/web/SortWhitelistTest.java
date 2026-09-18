package com.exelynt.booking.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exelynt.booking.common.exception.type.BadRequestException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class SortWhitelistTest {

    private static final Set<String> ALLOWED = Set.of("id", "price", "startTime");
    private static final Sort FALLBACK = Sort.by(Sort.Direction.DESC, "id");

    @Test
    @DisplayName("an allowed property and direction are honoured")
    void resolvesAllowedProperty() {
        Sort sort = SortWhitelist.resolve("price,asc", ALLOWED, FALLBACK);

        assertThat(sort.getOrderFor("price")).isNotNull();
        assertThat(sort.getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("a property without a direction sorts ascending")
    void defaultsToAscending() {
        assertThat(SortWhitelist.resolve("startTime", ALLOWED, FALLBACK).getOrderFor("startTime").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("a blank parameter falls back to the default sort")
    void fallsBackWhenBlank() {
        assertThat(SortWhitelist.resolve(null, ALLOWED, FALLBACK)).isEqualTo(FALLBACK);
        assertThat(SortWhitelist.resolve("  ", ALLOWED, FALLBACK)).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("a property outside the whitelist is rejected")
    void rejectsUnknownProperty() {
        assertThatThrownBy(() -> SortWhitelist.resolve("password,asc", ALLOWED, FALLBACK))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    @DisplayName("an unknown direction is rejected")
    void rejectsUnknownDirection() {
        assertThatThrownBy(() -> SortWhitelist.resolve("price,sideways", ALLOWED, FALLBACK))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not valid");
    }
}
