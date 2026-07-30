package com.delivera.fms.routing.instance.client;

import com.delivera.fms.routing.dto.CustomerDto;
import com.delivera.fms.routing.dto.DepotDto;
import com.delivera.fms.routing.dto.RoutingRequest;
import com.delivera.fms.routing.dto.RoutingResponse;
import com.delivera.fms.routing.dto.TypeSolver;
import com.delivera.fms.routing.instance.calculator.DistanceMatrixCalculator;
import com.delivera.fms.routing.instance.mapper.StandardInstanceMapper;
import com.delivera.fms.routing.instance.model.StandardInstance;
import com.delivera.fms.routing.instance.parser.StandardInstanceParser;
import com.delivera.fms.routing.service.EngineDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class StandardInstanceClient {

    private static final Logger log = LoggerFactory.getLogger(StandardInstanceClient.class);

    private final StandardInstanceParser parser;
    private final StandardInstanceMapper mapper;
    private final DistanceMatrixCalculator calculator;
    private final EngineDispatcher engineDispatcher;

    public StandardInstanceClient(
            StandardInstanceParser parser,
            StandardInstanceMapper mapper,
            DistanceMatrixCalculator calculator,
            EngineDispatcher engineDispatcher) {
        this.parser = parser;
        this.mapper = mapper;
        this.calculator = calculator;
        this.engineDispatcher = engineDispatcher;
    }

    public RoutingResponse sendInstance(Path file, TypeSolver solverType,
                                        Map<String, Object> parameters) throws IOException {
        log.info("Loading instance from file: {}", file);
        StandardInstance instance = parser.parse(file);
        StandardInstanceMapper.MappingResult mappingResult = mapper.map(instance);

        List<DepotDto> depots = mappingResult.depots();
        List<CustomerDto> customers = mappingResult.customers();

        double[][] distanceMatrix = calculator.calculate(depots, customers);

        String problemId = file.getFileName().toString().replace(".json", "");

        RoutingRequest request = new RoutingRequest(
                problemId,
                depots,
                customers,
                mappingResult.vehicles(),
                distanceMatrix,
                solverType,
                parameters
        );

        log.info("Dispatching instance '{}' to {} engine", problemId, solverType);
        RoutingResponse response = engineDispatcher.dispatch(request);
        log.info("Instance '{}' solved. Total cost: {}, Time: {}ms",
                problemId, response.totalCost(), response.computationTimeMs());
        return response;
    }
}
