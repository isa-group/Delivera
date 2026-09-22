package com.delivera.fms.engine.annealing.algorithm;

import com.delivera.fms.engine.annealing.service.AnnealingParameters;
import com.delivera.fms.engine.annealing.solution.AnnealingSolution;
import com.delivera.fms.engine.annealing.solution.Neighborhood;
import com.delivera.fms.engine.core.model.Depot;
import com.delivera.fms.engine.core.search.DepotRebalancer;
import com.delivera.fms.engine.core.search.RouteOptimizer;
import com.delivera.fms.engine.core.split.RouteSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uma.jmetal.algorithm.impl.AbstractLocalSearch;
import org.uma.jmetal.util.pseudorandom.PseudoRandomGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recocido simulado sobre la plantilla {@link AbstractLocalSearch} de jMetal.
 *
 * Un paso de la plantilla ({@link #updateCurrentSolution}) es un nivel de temperatura: una
 * tanda de movimientos aceptados con el criterio de Metropolis -siempre si mejoran, con
 * probabilidad {@code exp(-delta / T)} si empeoran-. Entre niveles ({@link #updateProgress}) se
 * enfria, se anota el mejor factible, se aplican las fases periodicas y se decide si recalentar.
 *
 * Temperatura calibrada, no fijada
 * Antes de arrancar se sondean {@value #WARMUP_SAMPLES} movimientos y se toma un cuantil bajo
 * ({@code calibrationQuantile}) de los que empeoran: un empeoramiento pequeno, de los que la
 * busqueda necesita aceptar para salir de un optimo local. La temperatura inicial es la que acepta
 * un empeoramiento asi con la probabilidad pedida: {@code -delta / ln(p0)}.
 *
 * Un cuantil bajo y no la mediana, y esto cambia el resultado mas que ningun otro parametro. Desde
 * un optimo local casi todos los movimientos aleatorios empeoran mucho, asi que la mediana es una
 * escala enorme: a esa temperatura la cadena se asienta en un coste de equilibrio muy por encima
 * del punto de partida y, como la temperatura final se deriva de la inicial, nunca baja lo bastante
 * para intensificar. Medido en p22: 12 % de gap con la mediana, 2,5 % con el cuantil del 5 %.
 *
 * Solo cuentan los empeoramientos de distancia: los que arrastran una penalizacion de flota son mil
 * veces mayores que un movimiento normal y, en una instancia cuya solucion inicial no cabe en la
 * flota, son ademas mayoria.
 *
 * Enfriamiento y recalentamiento
 * Geometrico por nivel. Cuando la temperatura baja de la que acepta un empeoramiento tipico con
 * {@code finalAcceptanceRate}, el sistema esta frio: se recalienta a la mitad de la inicial y se
 * reanuda desde el mejor conocido. No hay recalentamiento por estancamiento: dispararlo antes de
 * que un ciclo llegue a enfriarse le quitaba justo la fase en la que intensifica.
 *
 * Lo que el recocido no puede hacer solo
 * Hay instancias en las que la asignacion inicial no cabe en la flota y salir exige una cadena de
 * movimientos de la que solo el ultimo elimina una ruta. Metropolis los valora de uno en uno, asi
 * que se queda atrapado salvo por casualidad. Por eso se llama periodicamente al
 * {@link DepotRebalancer} del nucleo, el mismo que usa el motor genetico, y a la busqueda local a
 * nivel de ruta. Con {@code localSearchFrequency = 0} el motor es recocido puro; esa es la
 * ablacion que separa lo que aporta Metropolis de lo que aporta la busqueda local.
 *
 * Que se devuelve
 * La penalizacion orienta pero no decide: una solucion infactible barata puede quedar por debajo
 * de una factible cara. El resultado es la mejor solucion factible vista, comprobada una vez
 * por nivel; solo si nunca aparecio ninguna se devuelve la mejor penalizada.
 */
public class SimulatedAnnealing extends AbstractLocalSearch<AnnealingSolution> {

    private static final Logger log = LoggerFactory.getLogger(SimulatedAnnealing.class);

    /** Movimientos de sondeo para calibrar la temperatura inicial. */
    static final int WARMUP_SAMPLES = 1000;
    /** Fraccion de la temperatura inicial a la que se recalienta. */
    static final double REHEAT_FACTOR = 0.5;
    /** Cada cuantos movimientos se mira el reloj dentro de un nivel. */
    static final int CLOCK_CHECK_INTERVAL = 512;
    /** Pasadas de reparacion inicial, y fraccion del presupuesto (1/n) que pueden consumir. */
    static final int INITIAL_REPAIR_PASSES = 5;
    static final int INITIAL_REPAIR_SHARE = 4;
    /** Temperatura de respaldo si el sondeo no encuentra ningun empeoramiento. */
    private static final double FALLBACK_TEMPERATURE = 1.0;
    /** Umbral por encima del cual un empeoramiento se considera penalizacion y no distancia. */
    private static final double PENALTY_THRESHOLD = RouteSplitter.FLEET_PENALTY / 2;
    private static final double EPSILON = 1e-9;

    private final AnnealingSolution initial;
    private final Neighborhood neighborhood;
    private final RouteOptimizer optimizer;
    private final DepotRebalancer rebalancer;
    private final AnnealingParameters params;
    private final PseudoRandomGenerator random;
    private final long startTime;
    private final List<TracePoint> trace = new ArrayList<>();

    private double initialTemperature;
    private double minTemperature;
    private double temperature;
    private int movesPerLevel;
    private int level;
    private long moves;
    private long accepted;

    private AnnealingSolution best;
    private AnnealingSolution bestFeasible;

    public SimulatedAnnealing(AnnealingSolution initial,
                              Neighborhood neighborhood,
                              RouteOptimizer optimizer,
                              DepotRebalancer rebalancer,
                              AnnealingParameters params,
                              PseudoRandomGenerator random,
                              long startTime) {
        this.initial = initial;
        this.neighborhood = neighborhood;
        this.optimizer = optimizer;
        this.rebalancer = rebalancer;
        this.params = params;
        this.random = random;
        this.startTime = startTime;
    }

    @Override
    protected AnnealingSolution setCurrentSolution() {
        return initial;
    }

    @Override
    protected void initProgress() {
        AnnealingSolution current = getCurrentSolution();

        // Imprescindible: hay instancias infactibles de salida que el recocido no repara solo.
        // Varias pasadas porque cada una esta acotada en movimientos, pero nunca mas alla de una
        // fraccion del presupuesto: la reparacion es una fase auxiliar, no la busqueda.
        long repairDeadline = startTime + params.timeLimitMs() / INITIAL_REPAIR_SHARE;
        for (int pass = 0; pass < INITIAL_REPAIR_PASSES; pass++) {
            boolean changed = rebalance(current, repairDeadline);
            changed |= optimize(current);
            if (!changed || System.currentTimeMillis() >= repairDeadline) {
                break;
            }
        }

        best = current.copy();
        bestFeasible = current.isFeasible() ? best : null;
        recordTrace();

        movesPerLevel = Math.max(1, params.movesPerTemperatureFactor() * current.problem().customerCount());
        initialTemperature = calibrate(current);
        minTemperature = initialTemperature * Math.log(params.initialAcceptanceRate())
                / Math.log(params.finalAcceptanceRate());
        temperature = initialTemperature;
        level = 0;

        log.debug("Calibrated temperature: initial {}, minimum {}, {} moves per level",
                initialTemperature, minTemperature, movesPerLevel);
    }

    @Override
    protected boolean isStoppingConditionReached() {
        return outOfTime() || (params.maxLevels() > 0 && level >= params.maxLevels());
    }

    /** Un nivel: {@code movesPerLevel} movimientos a temperatura constante. */
    @Override
    protected AnnealingSolution updateCurrentSolution(AnnealingSolution current) {
        for (int move = 0; move < movesPerLevel; move++) {
            if (move % CLOCK_CHECK_INTERVAL == CLOCK_CHECK_INTERVAL - 1 && outOfTime()) {
                break;
            }

            Neighborhood.Move proposal = neighborhood.propose(current);
            if (proposal == null) {
                continue;
            }
            moves++;

            if (accepts(proposal.delta())) {
                accepted++;
                if (current.cost() < best.cost() - EPSILON) {
                    best = current.copy();
                }
            } else {
                neighborhood.undo(current, proposal);
            }
        }

        return current;
    }

    @Override
    protected void updateProgress() {
        level++;
        if (log.isTraceEnabled()) {
            log.trace("level {} T={} current={} best={} accepted={}/{}", level, temperature,
                    getCurrentSolution().cost(), best.cost(), accepted, moves);
        }
        temperature *= params.coolingRate();

        // Comprobar la factibilidad aqui y no en cada mejora: el mejor solo mejora, asi que
        // mirarlo una vez por nivel recoge el mismo estado sin trocearlo todo miles de veces.
        promoteBestIfFeasible();

        AnnealingSolution current = getCurrentSolution();
        if (isDue(params.rebalanceFrequency()) && !outOfTime()
                && rebalance(current, startTime + params.timeLimitMs())) {
            track(current);
        }
        if (isDue(params.localSearchFrequency()) && !outOfTime() && optimize(current)) {
            track(current);
        }

        if (temperature < minTemperature) {
            reheat();
        }
    }

    /** Copia de la mejor solucion factible vista, o de la mejor penalizada si nunca hubo una. */
    @Override
    public AnnealingSolution result() {
        return (bestFeasible != null ? bestFeasible : best).copy();
    }

    public boolean foundFeasible() {
        return bestFeasible != null;
    }

    public List<TracePoint> trace() {
        return Collections.unmodifiableList(trace);
    }

    public int levels() {
        return level;
    }

    public long moves() {
        return moves;
    }

    public long accepted() {
        return accepted;
    }

    @Override
    public String name() {
        return "SA";
    }

    @Override
    public String description() {
        return "Simulated annealing for the MD-CVRP with exact split evaluation";
    }

    private boolean accepts(double delta) {
        return delta <= 0.0 || random.nextDouble() < Math.exp(-delta / temperature);
    }

    /**
     * Temperatura inicial a partir de un cuantil bajo de los empeoramientos de distancia de una
     * muestra de movimientos, todos deshechos: el sondeo no cambia la solucion de partida.
     */
    private double calibrate(AnnealingSolution solution) {
        List<Double> worsenings = new ArrayList<>();
        for (int sample = 0; sample < WARMUP_SAMPLES; sample++) {
            Neighborhood.Move move = neighborhood.propose(solution);
            if (move == null) {
                continue;
            }
            if (move.delta() > EPSILON && move.delta() < PENALTY_THRESHOLD) {
                worsenings.add(move.delta());
            }
            neighborhood.undo(solution, move);
        }
        if (worsenings.isEmpty()) {
            return FALLBACK_TEMPERATURE;
        }
        Collections.sort(worsenings);
        int position = (int) Math.min(worsenings.size() - 1, worsenings.size() * params.calibrationQuantile());
        return -worsenings.get(position) / Math.log(params.initialAcceptanceRate());
    }

    private void reheat() {
        temperature = initialTemperature * REHEAT_FACTOR;
        setCurrentSolution(best.copy());
    }

    private boolean isDue(int frequency) {
        return frequency > 0 && level % frequency == 0;
    }

    private boolean rebalance(AnnealingSolution solution, long deadline) {
        if (!rebalancer.rebalance(solution.orders(), deadline)) {
            return false;
        }
        solution.evaluateAll();
        return true;
    }

    private boolean optimize(AnnealingSolution solution) {
        boolean improved = false;
        for (Depot depot : solution.problem().depots()) {
            improved |= optimizer.improve(depot, solution.order(depot.index()));
        }
        if (improved) {
            solution.evaluateAll();
        }
        return improved;
    }

    // Tras una fase que puede haber tocado cualquier deposito: actualiza el mejor si procede.
    private void track(AnnealingSolution current) {
        if (current.cost() < best.cost() - EPSILON) {
            best = current.copy();
            promoteBestIfFeasible();
        }
    }

    private void promoteBestIfFeasible() {
        if (bestFeasible == best) {
            return;
        }
        if ((bestFeasible == null || best.cost() < bestFeasible.cost() - EPSILON) && best.isFeasible()) {
            bestFeasible = best;
            recordTrace();
        }
    }

    private void recordTrace() {
        if (bestFeasible != null) {
            trace.add(new TracePoint(System.currentTimeMillis() - startTime, bestFeasible.cost()));
        }
    }

    private boolean outOfTime() {
        return System.currentTimeMillis() - startTime >= params.timeLimitMs();
    }

    /** Instante en que la mejor solucion factible paso a costar {@code cost}. */
    public record TracePoint(long elapsedMs, double cost) {
    }
}
