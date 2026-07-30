package com.delivera.fms.routing.config;

/**
 * Ejemplos JSON reutilizables para la documentacion OpenAPI (request/response).
 * Se centralizan aqui para poder referenciarlos desde varios controladores sin
 * duplicar el contenido.
 */
public final class OpenApiExamples {

    private OpenApiExamples() {
    }

    /**
     * Solicitud de ejemplo lista para ejecutar: 2 depositos, 5 clientes y una
     * matriz de distancias 7x7 consistente con los indices de los nodos.
     */
    public static final String SOLVE_REQUEST = """
            {
              "problemId": "PROBLEM-001",
              "depots": [
                { "id": "DEP-1", "lat": 40.4168, "lng": -3.7038, "matrixIndex": 0 },
                { "id": "DEP-2", "lat": 40.4500, "lng": -3.6800, "matrixIndex": 1 }
              ],
              "customers": [
                { "id": "C-001", "demand": 10, "lat": 40.4200, "lng": -3.7100, "matrixIndex": 2 },
                { "id": "C-002", "demand": 15, "lat": 40.4300, "lng": -3.6900, "matrixIndex": 3 },
                { "id": "C-003", "demand": 8,  "lat": 40.4100, "lng": -3.7200, "matrixIndex": 4 },
                { "id": "C-004", "demand": 20, "lat": 40.4400, "lng": -3.6700, "matrixIndex": 5 },
                { "id": "C-005", "demand": 12, "lat": 40.4600, "lng": -3.6850, "matrixIndex": 6 }
              ],
              "vehicles": [
                { "id": "V-001", "capacity": 50, "startDepotId": "DEP-1" },
                { "id": "V-002", "capacity": 50, "startDepotId": "DEP-2" }
              ],
              "distanceMatrix": [
                [0.0, 5.2, 2.1, 3.8, 3.0, 7.5, 8.1],
                [5.2, 0.0, 6.3, 4.1, 7.0, 3.2, 2.8],
                [2.1, 6.3, 0.0, 2.5, 1.8, 8.0, 9.2],
                [3.8, 4.1, 2.5, 0.0, 3.5, 5.0, 5.8],
                [3.0, 7.0, 1.8, 3.5, 0.0, 9.1, 10.0],
                [7.5, 3.2, 8.0, 5.0, 9.1, 0.0, 2.0],
                [8.1, 2.8, 9.2, 5.8, 10.0, 2.0, 0.0]
              ],
              "solverType": "GREEDY"
            }
            """;

    /**
     * Misma instancia resuelta con el motor genetico, ajustando parte de sus
     * parametros. Los que no aparecen toman el valor por defecto declarado en los
     * metadatos del solver; la semilla fija hace la ejecucion reproducible.
     */
    public static final String SOLVE_REQUEST_TUNED = """
            {
              "problemId": "PROBLEM-001",
              "depots": [
                { "id": "DEP-1", "lat": 40.4168, "lng": -3.7038, "matrixIndex": 0, "maxDuration": 200 },
                { "id": "DEP-2", "lat": 40.4500, "lng": -3.6800, "matrixIndex": 1, "maxDuration": 200 }
              ],
              "customers": [
                { "id": "C-001", "demand": 10, "lat": 40.4200, "lng": -3.7100, "matrixIndex": 2, "serviceDuration": 10 },
                { "id": "C-002", "demand": 15, "lat": 40.4300, "lng": -3.6900, "matrixIndex": 3, "serviceDuration": 10 },
                { "id": "C-003", "demand": 8,  "lat": 40.4100, "lng": -3.7200, "matrixIndex": 4, "serviceDuration": 10 },
                { "id": "C-004", "demand": 20, "lat": 40.4400, "lng": -3.6700, "matrixIndex": 5, "serviceDuration": 10 },
                { "id": "C-005", "demand": 12, "lat": 40.4600, "lng": -3.6850, "matrixIndex": 6, "serviceDuration": 10 }
              ],
              "vehicles": [
                { "id": "V-001", "capacity": 50, "startDepotId": "DEP-1" },
                { "id": "V-002", "capacity": 50, "startDepotId": "DEP-2" }
              ],
              "distanceMatrix": [
                [0.0, 5.2, 2.1, 3.8, 3.0, 7.5, 8.1],
                [5.2, 0.0, 6.3, 4.1, 7.0, 3.2, 2.8],
                [2.1, 6.3, 0.0, 2.5, 1.8, 8.0, 9.2],
                [3.8, 4.1, 2.5, 0.0, 3.5, 5.0, 5.8],
                [3.0, 7.0, 1.8, 3.5, 0.0, 9.1, 10.0],
                [7.5, 3.2, 8.0, 5.0, 9.1, 0.0, 2.0],
                [8.1, 2.8, 9.2, 5.8, 10.0, 2.0, 0.0]
              ],
              "solverType": "GENETIC",
              "parameters": {
                "populationSize": 200,
                "maxEvaluations": 120000,
                "seed": 42
              }
            }
            """;

    /**
     * Respuesta de ejemplo correspondiente a la resolucion real de la instancia
     * de benchmark p01 (4 depositos, 50 clientes) con el solver GREEDY.
     */
    public static final String ROUTING_RESPONSE = """
            {
              "problemId": "p01",
              "status": "COMPLETED",
              "solverUsed": "GREEDY",
              "totalCost": 775.2683743807665,
              "computationTimeMs": 6,
              "routes": [
                { "vehicleId": "V1-1", "depotId": "1", "stops": ["4", "17", "37"], "totalDistance": 30.880317680053132, "totalLoad": 21 },
                { "vehicleId": "V1-2", "depotId": "1", "stops": ["19", "41", "13"], "totalDistance": 40.14486436951017, "totalLoad": 59 },
                { "vehicleId": "V1-3", "depotId": "1", "stops": ["42", "44", "15"], "totalDistance": 42.92069079487675, "totalLoad": 39 },
                { "vehicleId": "V1-4", "depotId": "1", "stops": ["18", "25", "40"], "totalDistance": 77.10272756407127, "totalLoad": 76 },
                { "vehicleId": "V1-4", "depotId": "1", "stops": ["45"], "totalDistance": 42.941821107167776, "totalLoad": 10 },
                { "vehicleId": "V2-1", "depotId": "2", "stops": ["46", "12", "47", "6"], "totalDistance": 42.23306473661667, "totalLoad": 74 },
                { "vehicleId": "V2-2", "depotId": "2", "stops": ["27", "1", "32", "11"], "totalDistance": 40.58973909482191, "totalLoad": 53 },
                { "vehicleId": "V2-3", "depotId": "2", "stops": ["48", "8", "26", "31"], "totalDistance": 72.12477871053625, "totalLoad": 58 },
                { "vehicleId": "V2-4", "depotId": "2", "stops": ["14", "24", "23", "7", "43"], "totalDistance": 91.09488503401788, "totalLoad": 77 },
                { "vehicleId": "V3-1", "depotId": "3", "stops": ["49", "9"], "totalDistance": 12.837102637643028, "totalLoad": 29 },
                { "vehicleId": "V3-2", "depotId": "3", "stops": ["38", "5"], "totalDistance": 24.14213562373095, "totalLoad": 36 },
                { "vehicleId": "V3-3", "depotId": "3", "stops": ["30", "34"], "totalDistance": 26.65396192880828, "totalLoad": 45 },
                { "vehicleId": "V3-4", "depotId": "3", "stops": ["10", "39", "33", "50", "16"], "totalDistance": 78.6133276500888, "totalLoad": 67 },
                { "vehicleId": "V4-1", "depotId": "4", "stops": ["29", "21"], "totalDistance": 18.28574092690949, "totalLoad": 14 },
                { "vehicleId": "V4-2", "depotId": "4", "stops": ["20", "35"], "totalDistance": 28.76801799514891, "totalLoad": 45 },
                { "vehicleId": "V4-3", "depotId": "4", "stops": ["2", "22"], "totalDistance": 40.988714745749874, "totalLoad": 38 },
                { "vehicleId": "V4-4", "depotId": "4", "stops": ["3", "28", "36"], "totalDistance": 64.94648378101536, "totalLoad": 36 }
              ]
            }
            """;
}
