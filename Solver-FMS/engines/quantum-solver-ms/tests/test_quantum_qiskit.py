import unittest
from unittest.mock import patch
import json
import subprocess
import sys
import importlib.util
from pathlib import Path

from domain.models import CustomerDto, DepotDto, RoutingRequest, VehicleDto
from solvers.quantum_qiskit import (
    QuantumExecutionTimeoutError, _model_timeout, _simulation_pass_manager, _simulation_sampler,
    count_qiskit_qubits, solve_with_qiskit, solve_with_qiskit_with_timeout,
)


class TestQiskitResultDecoding(unittest.TestCase):
    def setUp(self):
        self.request = RoutingRequest(
            instance_name="qiskit-result-contract",
            depots=[DepotDto(id="D", x=0, y=0)],
            customers=[CustomerDto(id="C1", x=1, y=0, demand=1),
                       CustomerDto(id="C2", x=2, y=0, demand=1)],
            vehicles=[VehicleDto(id="V", capacity=5)],
        )

    def result_factory(self, active_arcs):
        def build_result(problem):
            from qiskit_optimization.algorithms import (
                OptimizationResult, OptimizationResultStatus,
            )
            active = {*active_arcs, "y_C1_V", "y_C2_V"}
            # Es el tipo real de resultado de la librería. Solo se sustituye
            # la búsqueda, para comprobar el contrato sin ejecutar QAOA.
            return OptimizationResult(
                x=[int(variable.name in active) for variable in problem.variables],
                fval=4, variables=problem.variables, status=OptimizationResultStatus.SUCCESS,
            )
        return build_result

    def test_decodes_optimization_result_by_variable_name(self):
        model_sizes = []
        with patch("qiskit_optimization.algorithms.MinimumEigenOptimizer.solve",
                   side_effect=self.result_factory({"x_D_C1_V", "x_C1_C2_V", "x_C2_D_V"})):
            response = solve_with_qiskit([self.request], on_model_ready=model_sizes.append)
        self.assertTrue(response.is_feasible)
        self.assertEqual(len(response.routes), 1)
        self.assertEqual(response.routes[0].stops, ["D", "C1", "C2", "D"])
        self.assertEqual(response.routes[0].load, 2)
        self.assertEqual(response.total_distance, 4)
        self.assertEqual(model_sizes, [count_qiskit_qubits(self.request)])
        self.assertEqual(response.qubits, model_sizes[0])

    def test_qubit_counter_and_viability_script_include_auxiliary_bits_without_qaoa(self):
        script = Path(__file__).resolve().parents[3] / "quantum_viability.py"
        spec = importlib.util.spec_from_file_location("quantum_viability", script)
        viability = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(viability)
        request = self.request.model_copy(deep=True)
        request.vehicles[0].capacity = 3
        pair = request.model_copy(deep=True)
        pair.customers = [CustomerDto(id="SC", x=1, y=0, demand=2)]
        three = request.model_copy(deep=True)
        three.customers.append(CustomerDto(id="C3", x=3, y=0, demand=1))
        three.vehicles[0].capacity = 4
        empty = request.model_copy(deep=True)
        empty.customers = []
        cases = [(empty, False, 0), (pair, False, 6), (request, False, 10),
                 (request, True, 26), (three, False, 18)]
        with patch("qiskit_optimization.algorithms.MinimumEigenOptimizer.solve") as optimizer, patch(
            "solvers.quantum_qiskit._simulation_sampler",
        ) as sampler:
            for instance, mtz, expected in cases:
                with self.subTest(customers=len(instance.customers), mtz=mtz):
                    self.assertEqual(count_qiskit_qubits(instance, mtz), expected)
                    self.assertEqual(viability.qubits_needed(instance, mtz), expected)
        optimizer.assert_not_called()
        sampler.assert_not_called()

    def test_qubit_limit_uses_shared_count_before_simulation(self):
        request = self.request.model_copy(deep=True)
        request.vehicles[0].capacity = 3
        with patch("solvers.quantum_qiskit._simulation_sampler") as sampler, patch(
            "qiskit_optimization.algorithms.MinimumEigenOptimizer.solve",
        ) as optimizer:
            with self.assertRaisesRegex(ValueError, "necesita 10 qubits"):
                solve_with_qiskit([request], max_qubits=9)
        sampler.assert_not_called()
        optimizer.assert_not_called()

    def test_inconsistent_assignments_are_still_rejected(self):
        with patch("qiskit_optimization.algorithms.MinimumEigenOptimizer.solve",
                   side_effect=self.result_factory({"x_D_C1_V", "x_C1_D_V"})):
            with self.assertRaisesRegex(ValueError, "subtour desconectado"):
                solve_with_qiskit([self.request])

    def test_transpilation_preserves_the_state_and_removes_matrix_evolutions(self):
        from qiskit.circuit.library import QAOAAnsatz
        from qiskit.quantum_info import SparsePauliOp, Statevector
        original = QAOAAnsatz(SparsePauliOp.from_list([("ZZI", 1), ("IZI", 2)]), reps=1)
        simplified = _simulation_pass_manager(42).run(original)
        self.assertNotIn("PauliEvolution", simplified.count_ops())
        self.assertTrue(Statevector(original.assign_parameters([0.2, 0.4])).equiv(
            Statevector(simplified.assign_parameters([0.2, 0.4])),
        ))

    def test_sampler_uses_reference_for_small_qubo_and_cpu_aer_from_eighteen(self):
        from qiskit.primitives import StatevectorSampler
        from qiskit_aer.primitives import SamplerV2
        with patch("qiskit_aer.AerSimulator.available_devices", return_value=("CPU",)) as devices:
            self.assertIsInstance(_simulation_sampler(10, 1024, 42), StatevectorSampler)
            self.assertIsInstance(_simulation_sampler(17, 1024, 42), StatevectorSampler)
            devices.assert_not_called()
            sampler = _simulation_sampler(18, 1024, 42)
            devices.assert_called_once_with()
        self.assertIsInstance(sampler, SamplerV2)
        self.assertEqual(sampler.default_shots, 1024)
        self.assertEqual(sampler.seed, 42)
        self.assertEqual(sampler.options.backend_options,
                         {"method": "statevector", "device": "CPU", "max_parallel_threads": 4})

    def test_sampler_selects_gpu_when_aer_reports_it_available(self):
        from qiskit_aer.primitives import SamplerV2
        # Comprueba la selección sin requerir una GPU en el equipo de pruebas.
        with patch("qiskit_aer.AerSimulator.available_devices", return_value=("CPU", "GPU")):
            sampler = _simulation_sampler(18, 1024, 42)
        self.assertIsInstance(sampler, SamplerV2)
        self.assertEqual(sampler.options.backend_options["device"], "GPU")
        self.assertEqual(sampler.options.backend_options["method"], "statevector")
        self.assertEqual(sampler.default_shots, 1024)
        self.assertEqual(sampler.seed, 42)

    def test_aer_and_reference_preserve_the_same_state_for_a_qaoa_circuit(self):
        from qiskit.circuit.library import QAOAAnsatz
        from qiskit.quantum_info import SparsePauliOp, Statevector
        from qiskit_aer import AerSimulator
        circuit = QAOAAnsatz(SparsePauliOp.from_list([("ZZI", 1), ("IZI", 2)]), reps=1)
        circuit = _simulation_pass_manager(42).run(circuit).assign_parameters([0.2, 0.4])
        expected = Statevector(circuit)
        circuit.save_statevector()
        actual = AerSimulator(method="statevector", device="CPU", max_parallel_threads=4).run(circuit).result()
        self.assertTrue(expected.equiv(actual.get_statevector()))

    def test_infeasible_qaoa_sample_is_rejected_instead_of_returning_empty_success(self):
        from qiskit_optimization.algorithms import OptimizationResult, OptimizationResultStatus
        def infeasible(problem):
            return OptimizationResult(x=[0] * problem.get_num_vars(), fval=0,
                                      variables=problem.variables, status=OptimizationResultStatus.INFEASIBLE)
        with patch("qiskit_optimization.algorithms.MinimumEigenOptimizer.solve", side_effect=infeasible):
            with self.assertRaisesRegex(ValueError, "muestra factible"):
                solve_with_qiskit([self.request])

    def test_child_reports_model_size_for_automatic_budget(self):
        # Se sustituye únicamente el programa hijo para comprobar el protocolo IPC.
        real_popen = subprocess.Popen
        result = {"status": "ok", "result": {"instance_name": "ipc", "solver_used": "test",
                  "execution_time_ms": 0, "total_distance": 0, "is_feasible": True, "routes": []}}
        code = ("import sys; sys.stdin.read(); "
                "print('{\"status\":\"model_ready\",\"qubits\":10}',flush=True); "
                f"print({json.dumps(result)!r},flush=True)")
        def launch(command, **kwargs):
            return real_popen([sys.executable, "-B", "-c", code], **kwargs)
        for explicit in [None, 2.0]:
            with self.subTest(explicit=explicit), patch("solvers.quantum_qiskit.subprocess.Popen", side_effect=launch), patch(
                "solvers.quantum_qiskit._model_timeout", return_value=60,
            ) as budget:
                response = solve_with_qiskit_with_timeout([self.request], timeout_sec=explicit)
                self.assertEqual(response.instance_name, "ipc")
                if explicit is None:
                    budget.assert_called_once_with(10, 20, 1)
                else:
                    budget.assert_not_called()

    def test_explicit_timeout_terminates_and_reaps_child(self):
        real_popen = subprocess.Popen
        children = []
        def launch(command, **kwargs):
            child = real_popen([sys.executable, "-B", "-c", "import time; time.sleep(5)"], **kwargs)
            children.append(child)
            return child
        with patch("solvers.quantum_qiskit.subprocess.Popen", side_effect=launch):
            with self.assertRaises(QuantumExecutionTimeoutError):
                solve_with_qiskit_with_timeout([self.request], timeout_sec=0.1)
        self.assertIsNotNone(children[0].poll())

    def test_automatic_budget_grows_with_model_size_and_effort_but_is_bounded(self):
        budgets = [_model_timeout(n, 20, 1) for n in [6, 10, 14, 18, 21, 24]]
        self.assertEqual(budgets, sorted(budgets))
        self.assertGreater(_model_timeout(14, 60, 1), _model_timeout(14, 20, 1))
        self.assertGreater(_model_timeout(14, 20, 2), _model_timeout(14, 20, 1))
        self.assertLessEqual(_model_timeout(24, 1000, 5), 270)

    def test_invalid_qaoa_options_fail_before_starting_a_child(self):
        with patch("solvers.quantum_qiskit.subprocess.Popen") as launch:
            for options in [{"maxiter": 0}, {"reps": 0}, {"shots": 0}, {"timeout_sec": float("nan")}]:
                with self.subTest(options=options), self.assertRaises(ValueError):
                    solve_with_qiskit_with_timeout([self.request], **options)
        launch.assert_not_called()


if __name__ == "__main__":
    unittest.main()
