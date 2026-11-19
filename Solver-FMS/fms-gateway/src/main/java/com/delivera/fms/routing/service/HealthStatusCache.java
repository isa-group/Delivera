package com.delivera.fms.routing.service;

import com.delivera.fms.routing.config.HealthCacheProperties;
import com.delivera.fms.routing.dto.SolverStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache en memoria del ultimo estado de salud conocido de cada motor.
 *
 * La clave es la URL del motor y no el solver: varios solvers pueden compartir
 * motor (QISKIT y DWAVE apuntan al mismo), y su salud es la del servicio, no la
 * del algoritmo. Las entradas se crean bajo demanda, de modo que un motor nuevo
 * en la configuracion entra en la cache la primera vez que se sondea, sin tocar
 * esta clase.
 *
 * El TTL depende del resultado: las entradas positivas (UP) duran mas, porque un
 * motor que responde bien no suele cambiar de estado de un momento a otro; las
 * negativas (DOWN) son cortas, para que una recuperacion se detecte pronto y el
 * catalogo no siga anunciando como caido un motor ya disponible.
 */
@Service
public class HealthStatusCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration positiveTtl;
    private final Duration negativeTtl;
    private final Clock clock;

    // Con dos constructores Spring necesita saber cual usar; el otro es para los tests.
    @Autowired
    public HealthStatusCache(HealthCacheProperties properties) {
        this(properties, Clock.systemUTC());
    }

    HealthStatusCache(HealthCacheProperties properties, Clock clock) {
        this.positiveTtl = properties.getPositiveTtl();
        this.negativeTtl = properties.getNegativeTtl();
        this.clock = clock;
    }

    /** Devuelve el estado cacheado del motor solo si sigue vigente. */
    public Optional<SolverStatus> get(String engineUrl) {
        Entry entry = entries.get(engineUrl);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(engineUrl, entry);
            return Optional.empty();
        }
        return Optional.of(entry.status());
    }

    /** Guarda el resultado de una sonda con el TTL que le corresponde. */
    public void put(String engineUrl, SolverStatus status) {
        if (engineUrl == null || (status != SolverStatus.UP && status != SolverStatus.DOWN)) {
            return;
        }
        entries.put(engineUrl, new Entry(status, clock.instant().plus(ttlFor(status))));
    }

    /** Descarta la entrada de un motor, forzando su proxima sonda. */
    public void invalidate(String engineUrl) {
        if (engineUrl != null) {
            entries.remove(engineUrl);
        }
    }

    private Duration ttlFor(SolverStatus status) {
        return status == SolverStatus.UP ? positiveTtl : negativeTtl;
    }

    private record Entry(SolverStatus status, Instant expiresAt) {
    }
}
