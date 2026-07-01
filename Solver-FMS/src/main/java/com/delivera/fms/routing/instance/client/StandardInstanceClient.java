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
import com.delivera.fms.routing.service.RouteSolver;
import com.delivera.fms.routing.service.RouteSolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
public class StandardInstanceClient {

    private static final Logger log = LoggerFactory.getLogger(StandardInstanceClient.class);

    private final StandardInstanceParser parser;
    private final StandardInstanceMapper mapper;
    private final DistanceMatrixCalculator calculator;
    private final RouteSolverFactory solverFactory;

    public StandardInstanceClient(
            StandardInstanceParser parser,
            StandardInstanceMapper mapper,
            DistanceMatrixCalculator calculator,
            RouteSolverFactory solverFactory) {
        this.parser = parser;
        this.mapper = mapper;
        this.calculator = calculator;
        this.solverFactory = solverFactory;
    }

    public RoutingResponse sendInstance(Path file, TypeSolver solverType) throws IOException {
        log.info("Loading instance from file: {}", file);
        StandardInstance instance = parser.parse(file);
        StandardInstanceMapper.MappingResult mappingResult = mapper.map(instance);

        List<DepotDto> depots = mappingResult.depots();
        List<CustomerDto> customers = mappingResult.customers();

        double[][] distanceMatrix = calculator.calculate(depots, customers);

        String problemId = file.getFileName().toString();

        RoutingRequest request = new RoutingRequest(
                problemId,
                depots,
                customers,
                mappingResult.vehicles(),
                distanceMatrix,
                solverType
        );

        log.info("Solving instance '{}' with solver type: {}", problemId, solverType);
        RouteSolver solver = solverFactory.getSolver(solverType);
        RoutingResponse response = solver.solve(request);
        log.info("Instance '{}' solved. Total cost: {}, Time: {}ms", problemId, response.totalCost(), response.computationTimeMs());
        return response;
    }
}
