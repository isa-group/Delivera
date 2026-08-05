package com.delivera.fms.routing.service;

import com.delivera.fms.routing.config.EngineProperties;
import com.delivera.fms.routing.config.SolverNotFoundException;
import com.delivera.fms.routing.dto.SolverInfo;
import com.delivera.fms.routing.dto.SolverParameter;
import com.delivera.fms.routing.dto.SolverStatus;
import com.delivera.fms.routing.dto.TypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registro central de solvers. Construye un cliente HTTP por cada motor
 * declarado en {@link EngineProperties} y expone tanto los metadatos (para el
 * catalogo publico) como los clientes (para el despacho de problemas).
 *
 * Al derivarse todo de la configuracion, registrar un motor nuevo no requiere
 * tocar esta clase ni ninguna otra: basta con anadir la constante al enum
 * {@link TypeSolver} y su bloque bajo fms.engines en application.yml.
 */
@Service
public class SolverRegistry {

    private static final Logger log = LoggerFactory.getLogger(SolverRegistry.class);

    /** Margen para la sonda de salud: no debe penalizar la respuesta del catalogo. */
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(2);

    private final Map<TypeSolver, RegisteredSolver> solvers;

    public SolverRegistry(EngineProperties properties, WebClient.Builder builder) {
        Map<TypeSolver, RegisteredSolver> registered = properties.getEngines().entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .sorted(Comparator
                        .<Map.Entry<TypeSolver, EngineProperties.Engine>>comparingInt(e -> e.getValue().getOrder())
                        .thenComparing(e -> e.getKey().name()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> build(entry.getKey(), entry.getValue(), builder),
                        (a, b) -> a,
                        LinkedHashMap::new));

        this.solvers = Collections.unmodifiableMap(registered);
        log.info("Registered {} solver(s): {}", solvers.size(), solvers.keySet());
    }

    private RegisteredSolver build(TypeSolver type, EngineProperties.Engine engine, WebClient.Builder builder) {
        SolverInfo info = new SolverInfo(
                type,
                engine.getDisplayName() != null ? engine.getDisplayName() : type.name(),
                engine.getDescription(),
                engine.getStrategy(),
                engine.getTechnology(),
                engine.getVersion(),
                engine.isDeterministic(),
                parameters(type, engine),
                SolverStatus.UNKNOWN);
        return new RegisteredSolver(info, builder.baseUrl(engine.getUrl()).build());
    }

    /**
     * Convierte los parametros declarados en configuracion al contrato publico.
     *
     * El valor por defecto se comprueba aqui, una sola vez: un defecto que no
     * case con su tipo es un error del descriptor y aborta el arranque, en lugar
     * de aparecer como fallo al resolver el primer problema.
     */
    private List<SolverParameter> parameters(TypeSolver type, EngineProperties.Engine engine) {
        return engine.getParameters().stream()
                .map(parameter -> new SolverParameter(
                        parameter.getName(),
                        parameter.getDescription(),
                        parameter.getType(),
                        defaultValue(type, parameter),
                        parameter.getMin(),
                        parameter.getMax(),
                        parameter.isRequired()))
                .toList();
    }

    private Object defaultValue(TypeSolver type, EngineProperties.Parameter parameter) {
        try {
            return parameter.getType().validate(parameter.getDefaultValue());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid default value for parameter '" + parameter.getName()
                    + "' of solver " + type + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Devuelve el catalogo completo de solvers registrados.
     *
     * @param includeStatus si es true, consulta en paralelo la salud de cada
     *                      motor y rellena el campo status; en caso contrario
     *                      todos los solvers se devuelven como UNKNOWN.
     */
    public List<SolverInfo> findAll(boolean includeStatus) {
        List<SolverInfo> catalog = solvers.values().stream()
                .map(RegisteredSolver::info)
                .toList();
        return includeStatus ? withStatuses(catalog) : catalog;
    }

    /**
     * Devuelve un solver concreto.
     *
     * @throws SolverNotFoundException si el tipo no esta registrado
     */
    public SolverInfo findByType(TypeSolver type, boolean includeStatus) {
        RegisteredSolver solver = solvers.get(type);
        if (solver == null) {
            throw new SolverNotFoundException("Solver not registered: " + type + ". Available: " + solvers.keySet());
        }
        return includeStatus ? solver.info().withStatus(probe(solver)) : solver.info();
    }

    /**
     * Cliente HTTP del motor que implementa el solver indicado.
     *
     * @throws SolverNotFoundException si el tipo no esta registrado
     */
    public WebClient clientFor(TypeSolver type) {
        RegisteredSolver solver = solvers.get(type);
        if (solver == null) {
            throw new SolverNotFoundException("No engine configured for solver type: " + type
                    + ". Available: " + solvers.keySet());
        }
        return solver.client();
    }

    /**
     * Completa los parametros recibidos con los valores por defecto declarados en
     * los metadatos y comprueba tipo y rango.
     *
     * Es lo que hace que el valor por defecto sea parte del contrato y no un
     * comentario: el motor recibe siempre la configuracion completa, y el mapa
     * devuelto describe exactamente la ejecucion que se lanza.
     *
     * @throws IllegalArgumentException si falta un parametro obligatorio o si uno
     *                                  recibido no es convertible o queda fuera de rango
     * @throws SolverNotFoundException  si el tipo no esta registrado
     */
    public Map<String, Object> resolveParameters(TypeSolver type, Map<String, Object> requested) {
        SolverInfo info = findByType(type, false);
        Map<String, Object> supplied = (requested != null) ? requested : Map.of();

        Set<String> declared = info.parameters().stream()
                .map(SolverParameter::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String name : supplied.keySet()) {
            if (!declared.contains(name)) {
                log.warn("Solver {} does not declare parameter '{}'; ignored. Declared: {}",
                        type, name, declared);
            }
        }

        Map<String, Object> effective = new LinkedHashMap<>();
        for (SolverParameter parameter : info.parameters()) {
            Object raw = supplied.get(parameter.name());

            if (raw == null) {
                if (parameter.hasDefault()) {
                    effective.put(parameter.name(), parameter.defaultValue());
                } else if (parameter.required()) {
                    throw new IllegalArgumentException("Parameter '" + parameter.name()
                            + "' is required by solver " + type + " and has no default value");
                }
                continue;
            }

            effective.put(parameter.name(), checked(type, parameter, raw));
        }

        return effective;
    }

    /** Comprueba que el valor encaja con el tipo declarado y respeta el rango. */
    private Object checked(TypeSolver type, SolverParameter parameter, Object raw) {
        Object value;
        try {
            value = parameter.type().validate(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Parameter '" + parameter.name() + "' of solver " + type
                    + ": " + ex.getMessage(), ex);
        }

        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (parameter.min() != null && numeric < parameter.min()) {
                throw outOfRange(type, parameter, value, "minimum is " + parameter.min());
            }
            if (parameter.max() != null && numeric > parameter.max()) {
                throw outOfRange(type, parameter, value, "maximum is " + parameter.max());
            }
        }

        return value;
    }

    private IllegalArgumentException outOfRange(TypeSolver type, SolverParameter parameter,
                                                Object value, String detail) {
        return new IllegalArgumentException("Parameter '" + parameter.name() + "' of solver " + type
                + " is " + value + " but " + detail);
    }

    /** Comprueba la salud de todos los motores en paralelo. */
    private List<SolverInfo> withStatuses(List<SolverInfo> catalog) {
        Map<TypeSolver, SolverStatus> statuses = Flux.fromIterable(solvers.values())
                .flatMap(solver -> probeAsync(solver)
                        .map(status -> Map.entry(solver.info().type(), status)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .block();

        Map<TypeSolver, SolverStatus> resolved = statuses != null ? statuses : Map.of();
        return catalog.stream()
                .map(info -> info.withStatus(resolved.getOrDefault(info.type(), SolverStatus.DOWN)))
                .toList();
    }

    private SolverStatus probe(RegisteredSolver solver) {
        SolverStatus status = probeAsync(solver).block();
        return status != null ? status : SolverStatus.DOWN;
    }

    private Mono<SolverStatus> probeAsync(RegisteredSolver solver) {
        return solver.client().get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(HEALTH_TIMEOUT)
                .map(body -> SolverStatus.UP)
                .onErrorResume(ex -> {
                    log.debug("Health probe failed for {}: {}", solver.info().type(), ex.getMessage());
                    return Mono.just(SolverStatus.DOWN);
                });
    }

    /** Metadatos publicos de un solver junto al cliente HTTP de su motor. */
    private record RegisteredSolver(SolverInfo info, WebClient client) {
    }
}
