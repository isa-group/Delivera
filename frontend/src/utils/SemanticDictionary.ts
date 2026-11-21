export enum SemanticRole {

    QUANTIFIER,

    MEASURE,

    RESOURCE,

    QUALIFIER,

    ACTION,

    STATE,

    TECHNOLOGY,

    USAGE,

    PARAMETER,

    UNKNOWN

}

export interface SemanticToken {

    word: string

    role: SemanticRole

}

function buildRoleMap(): Map<string, SemanticRole> {
    
    const QUANTIFIERS = [

        "max",
        "maximum",
    
        "min",
        "minimum",
    
        "limit",
    
        "quota",
    
        "threshold",
    
        "capacity",
    
        "total"
    
    ]

    const USAGE = [

        "usage",

        "used",

        "consumed",

        "remaining",


        "free",

        "allocated",

        "reserved",

        "spent"

    ]

    const ACTIONS =  [

        "execution",
        "executions",

        "request",
        "requests",

        "call",
        "calls",

        "run",
        "runs",

        "job",
        "jobs",

        "task",
        "tasks",

        "operation",
        "operations",

        "deployment",
        "deployments",

        "solve",
        "solves"

    ]

    const RESOURCES =  [

        "vehicle",
        "vehicles",

        "worker",
        "workers",

        "company",
        "companies",

        "customer",
        "customers",

        "user",
        "users",

        "route",
        "routes",

        "order",
        "orders",

        "depot",
        "depots",

        "driver",
        "drivers",

        "unit",
        "units",

        "account",
        "accounts",

        "organization",
        "organizations",

        "project",
        "projects",

        "license",
        "licenses",

        "subscription",
        "subscriptions",

        "token",
        "tokens",

        "solver",
        "solvers"

    ]

    const STATES =  [

        "active",

        "inactive",

        "enabled",

        "disabled",

        "available",

        "unavailable",

        "online",

        "offline",

        "healthy",

        "unhealthy",

        "valid",

        "invalid",

        "expired",

        "pending",

        "running",

        "completed",

        "failed",

        "error"

    ]


    const QUALIFIERS =  [
        "ruido",
        
        "per",

        "loyal",

        "premium",

        "basic",

        "standard",

        "custom",

        "default",

        "global",

        "local",

        "public",

        "private",

        "temporary",

        "operational",

        "priority",

        "initial",
    
        "final",
    
        "inter",
    
        "intra",
    
        "stagnant",
    
        "classical"
        
    ]

    const TECHNOLOGIES = [

        "greedy",

        "genetic",

        "annealing",

        "hgs",

        "dwave",

        "qiskit",

        "ortools",

        "pyvrp",

        "neural",

        "quantum"

    ]

    const PARAMETERS = [

        "population",
    
        "generation",
        "generations",
    
        "evaluation",
        "evaluations",
    
        "mutation",
    
        "crossover",
    
        "cooling",
    
        "temperature",
    
        "acceptance",
    
        "tournament",
    
        "elitism",
    
        "restart",
        "restarts",
    
        "search",
    
        "candidate",
    
        "calibration",
    
        "quantile",
    
        "seed",
    
        "fitness",
    
        "neighborhood",
    
        "cluster",
    
        "window",
    
        "depot",

        "level",

        "levels",
    
        "customer",
    
        "move",
        "moves",
    
        "rebalance",
    
        "strategy",
    
        "config",
    
        "preset",
    
        "profile",

        "evaluation",
        "evaluations",

        "acceptance",

        "restart",
        "restarts",

        "move",
        "moves",

        "search",

        "candidate",

        "calibration",

        "quantile",

        "frequency",

        "probability",

        "factor",

        "improvement",

        "selection",

        "offspring",

        "parent",

        "child",

        "route",

        "repair",

        "optimization",

        "optimizations",

        "learning",

        "training",

        "heuristic",

        "metaheuristic",

        "constraint",

        "constraints",

        "penalty",

        "repair",

        "objective",

        "qubit",

        "qubits",
    
    ]


    const MEASURES = [

        "size",
    
        "count",
    
        "rate",
    
        "ratio",
    
        "frequency",
    
        "probability",
    
        "factor",
    
        "percentage",
    
        "percent",
    
        "cost",
    
        "distance",
    
        "duration",
    
        "score",
    
        "weight",
    
        "time",
    
        "timeout",

        "limit",

        "budget",

        "interval",

        "step",

        "steps",

        "time",

        "latency",

        "iteration",

        "iterations",

        "radius",

        "km",

        "ms"
    
    ]




    const map = new Map<string, SemanticRole>()

    addWords(
        map,
        QUANTIFIERS,
        SemanticRole.QUANTIFIER
    )

    addWords(
        map,
        RESOURCES,
        SemanticRole.RESOURCE
    )

    addWords(
        map,
        QUALIFIERS,
        SemanticRole.QUALIFIER
    )

    addWords(
        map,
        ACTIONS,
        SemanticRole.ACTION
    )

    addWords(
        map,
        STATES,
        SemanticRole.STATE
    )

    addWords(
        map,
        TECHNOLOGIES,
        SemanticRole.TECHNOLOGY
    )

    addWords(
        map,
        USAGE,
        SemanticRole.USAGE

    )

    addWords(
        map,
        PARAMETERS,
        SemanticRole.PARAMETER
    )

    addWords(
        map,
        MEASURES,
        SemanticRole.MEASURE
    )

    return map

}

function addWords(
    map: Map<string, SemanticRole>,
    words: string[],
    role: SemanticRole
): void {

    for (const word of words) {

        map.set(
            word.toLowerCase(),
            role
        )

    }

}
const ROLE_MAP = buildRoleMap()

export { ROLE_MAP }