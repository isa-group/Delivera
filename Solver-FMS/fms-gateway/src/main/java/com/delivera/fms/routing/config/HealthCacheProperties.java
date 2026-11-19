package com.delivera.fms.routing.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Tiempos de vida de la cache de salud de los motores (fms.health-cache.*).
 *
 * Se separan en dos porque un motor sano suele seguir siendolo durante un buen
 * rato, mientras que uno caido puede recuperarse en cualquier momento: una
 * entrada negativa que durase demasiado ocultaria esa recuperacion.
 */
@ConfigurationProperties(prefix = "fms.health-cache")
@Validated
public class HealthCacheProperties {

    /** Tiempo que se conserva un estado UP antes de volver a sondear el motor. */
    @NotNull
    private Duration positiveTtl = Duration.ofSeconds(30);

    /** Tiempo que se conserva un estado DOWN antes de reintentar la sonda. */
    @NotNull
    private Duration negativeTtl = Duration.ofSeconds(5);

    public Duration getPositiveTtl() {
        return positiveTtl;
    }

    public void setPositiveTtl(Duration positiveTtl) {
        this.positiveTtl = positiveTtl;
    }

    public Duration getNegativeTtl() {
        return negativeTtl;
    }

    public void setNegativeTtl(Duration negativeTtl) {
        this.negativeTtl = negativeTtl;
    }
}
