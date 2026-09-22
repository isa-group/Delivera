package com.delivera.fms.routing.instance.service;

import com.delivera.fms.routing.config.InstanceNotFoundException;
import com.delivera.fms.routing.instance.dto.InstanceDetail;
import com.delivera.fms.routing.instance.dto.InstanceSummary;
import com.delivera.fms.routing.instance.mapper.StandardInstanceMapper;
import com.delivera.fms.routing.instance.model.DepotConfig;
import com.delivera.fms.routing.instance.model.NodeEntry;
import com.delivera.fms.routing.instance.model.StandardInstance;
import com.delivera.fms.routing.instance.parser.StandardInstanceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Registro de las instancias estandar que hay en disco.
 *
 * Es al banco de instancias lo que SolverRegistry es a los motores: el catalogo
 * no se declara en ningun sitio, se deriva del contenido del directorio, asi que
 * dejar un fichero nuevo en el volumen basta para que aparezca listado.
 *
 * Tambien es el unico punto que traduce un nombre de instancia a una ruta de
 * fichero, con la comprobacion de que no se sale del directorio: antes esa
 * comprobacion vivia en el controlador y ahora la comparten los tres endpoints.
 */
@Service
public class StandardInstanceRegistry {

    private static final Logger log = LoggerFactory.getLogger(StandardInstanceRegistry.class);

    private static final String EXTENSION = ".json";

    private final StandardInstanceParser parser;
    private final StandardInstanceMapper mapper;
    private final Path baseDir;

    public StandardInstanceRegistry(
            StandardInstanceParser parser,
            StandardInstanceMapper mapper,
            @Value("${fms.instances.dir:instances-MD-CVRP-JSON}") String instancesDir) {
        this.parser = parser;
        this.mapper = mapper;
        this.baseDir = Paths.get(instancesDir).toAbsolutePath().normalize();
        log.info("Instances directory: {}", baseDir);
    }

    /**
     * Todas las instancias del directorio, ordenadas por nombre.
     *
     * Un fichero ilegible o que no sea una instancia aborta el listado en lugar
     * de omitirse: en un banco de pruebas, una instancia que desaparece del
     * catalogo sin avisar es peor que un error.
     */
    public List<InstanceSummary> findAll() throws IOException {
        if (!Files.isDirectory(baseDir)) {
            log.warn("Instances directory does not exist: {}", baseDir);
            return List.of();
        }

        List<Path> files;
        try (Stream<Path> entries = Files.list(baseDir)) {
            files = entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        List<InstanceSummary> summaries = new ArrayList<>(files.size());
        for (Path file : files) {
            summaries.add(summarize(nameOf(file), parser.parse(file)));
        }
        return summaries;
    }

    // Una instancia concreta, ya traducida al modelo que recibe el motor
    public InstanceDetail findByName(String name) throws IOException {
        Path file = resolve(name);
        StandardInstance instance = parser.parse(file);
        StandardInstanceMapper.MappingResult mapped = mapper.map(instance);
        return new InstanceDetail(
                summarize(nameOf(file), instance),
                mapped.depots(),
                mapped.customers(),
                mapped.vehicles());
    }

    /**
     * Traduce el nombre de una instancia a la ruta de su fichero.
     *
     * @throws IllegalArgumentException  si el nombre se sale del directorio de instancias
     * @throws InstanceNotFoundException si no existe el fichero
     */
    public Path resolve(String name) {
        String fileName = name.endsWith(EXTENSION) ? name : name + EXTENSION;
        Path file = baseDir.resolve(fileName).normalize();

        // Un nombre con ../ resolveria fuera del volumen de instancias: se rechaza
        // antes de tocar el disco.
        if (!file.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid instance name: " + name);
        }
        if (!Files.isRegularFile(file)) {
            throw new InstanceNotFoundException("Instance not found: " + name
                    + ". Available instances: GET /api/v1/fms/instances");
        }
        return file;
    }

    private String nameOf(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith(EXTENSION)
                ? fileName.substring(0, fileName.length() - EXTENSION.length())
                : fileName;
    }

    private InstanceSummary summarize(String name, StandardInstance instance) {
        // Las instancias del banco comparten capacidad y duracion maxima entre sus
        // depositos. Se toma el maximo en lugar del primero para que, si algun dia
        // dejan de compartirla, el resumen no dependa del orden del fichero; el
        // valor exacto de cada deposito esta en el detalle.
        int capacity = instance.depotConfigs().stream()
                .mapToInt(DepotConfig::vehicleCapacity)
                .max()
                .orElse(0);
        double maxDuration = instance.depotConfigs().stream()
                .mapToDouble(DepotConfig::maxDuration)
                .max()
                .orElse(0);
        int totalDemand = instance.customers().stream()
                .mapToInt(NodeEntry::demand)
                .sum();

        long fleetCapacity = (long) instance.depotCount() * instance.vehiclesPerDepot() * capacity;

        return new InstanceSummary(
                name,
                instance.problemType(),
                instance.depotCount(),
                instance.customerCount(),
                instance.vehiclesPerDepot(),
                capacity,
                // 0 en el fichero es "sin limite", no una duracion de cero.
                maxDuration > 0 ? maxDuration : null,
                totalDemand,
                fleetCapacity > 0 ? (double) totalDemand / fleetCapacity : 0.0);
    }
}
