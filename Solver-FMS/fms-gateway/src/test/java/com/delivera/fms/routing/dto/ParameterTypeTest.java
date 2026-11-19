package com.delivera.fms.routing.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParameterTypeTest {

    @Test
    void booleanAcceptsTrueAndFalse() {
        assertThat(ParameterType.BOOLEAN.validate(true)).isEqualTo(Boolean.TRUE);
        assertThat(ParameterType.BOOLEAN.validate(false)).isEqualTo(Boolean.FALSE);
    }

    @Test
    void booleanRejectsTextAndNumbers() {
        // "true" o 1 obligarian a adivinar la intencion del cliente.
        assertThatThrownBy(() -> ParameterType.BOOLEAN.validate("true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected true or false");
        assertThatThrownBy(() -> ParameterType.BOOLEAN.validate(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void booleansAreNotAcceptedAsNumbers() {
        assertThatThrownBy(() -> ParameterType.INTEGER.validate(true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected a number");
    }

    @Test
    void nullMeansNotSentForEveryType() {
        for (ParameterType type : ParameterType.values()) {
            assertThat(type.validate(null)).isNull();
        }
    }

    @Test
    void integerAndDecimalKeepTheirNormalisation() {
        assertThat(ParameterType.INTEGER.validate(24)).isEqualTo(24L);
        assertThat(ParameterType.DECIMAL.validate(30)).isEqualTo(30.0);
        assertThatThrownBy(() -> ParameterType.INTEGER.validate(12.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected an integer");
    }
}
