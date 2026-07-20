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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
public class StandardInstanceClient {

    private final StandardInstanceParser parser;
    private final StandardInstanceMapper mapper;
    private final DistanceMatrixCalculator calculator;
    private final RestClient restClient;
    private final String solveEndpoint;

    public StandardInstanceClient(
            StandardInstanceParser parser,
            StandardInstanceMapper mapper,
            DistanceMatrixCalculator calculator,
            RestClient restClient,
            @Value("${fms.service.solve-endpoint:/api/v1/fms/routing/solve}") String solveEndpoint) {
        this.parser = parser;
        this.mapper = mapper;
        this.calculator = calculator;
        this.restClient = restClient;
        this.solveEndpoint = solveEndpoint;
    }

    public RoutingResponse sendInstance(Path file, TypeSolver solverType) throws IOException {
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

        return restClient.post()
                .uri(solveEndpoint)
                .body(request)
                .retrieve()
                .body(RoutingResponse.class);
    }
}
