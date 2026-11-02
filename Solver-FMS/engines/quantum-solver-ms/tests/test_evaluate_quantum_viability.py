import unittest
import sys
import os

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from utils.parser import CordeauParser
from services.graph_analyzer import GraphAnalyzer

class TestEvaluateQuantumViability(unittest.TestCase):

    def setUp(self):
        self.data_dir = os.path.join(os.path.dirname(__file__), "data")

    def load_and_analyze(self, filename: str):
        filepath = os.path.join(self.data_dir, filename)
        request = CordeauParser.parse_instance(filepath, filename)
        features = GraphAnalyzer.extract_features(request)
        is_viable = GraphAnalyzer.evaluate_quantum_viability(features)
        return features, is_viable

    def test_case_1_viable(self):
        features, is_viable = self.load_and_analyze("data_test_metric_1.txt")
        self.assertTrue(is_viable, f"Expected True but failed. Features: {features}")
        self.assertLess(features["demand_granularity"], 0.6)
        self.assertLessEqual(features["capacity_tightness"], 0.90)
        self.assertLessEqual(features["spatial_compressibility_index"], 1.2)

    def test_case_2_fails_granularity(self):
        features, is_viable = self.load_and_analyze("data_test_metric_2.txt")
        self.assertFalse(is_viable, "Expected False due to demand granularity")
        self.assertGreaterEqual(features["demand_granularity"], 0.6)

    def test_case_3_fails_granularity(self):
        features, is_viable = self.load_and_analyze("data_test_metric_3.txt")
        self.assertFalse(is_viable, "Expected False due to Demand Granularity >= 0.6")
        self.assertGreaterEqual(features["demand_granularity"], 0.6)

    def test_case_4_fails_tightness(self):
        features, is_viable = self.load_and_analyze("data_test_metric_4.txt")
        self.assertFalse(is_viable, "Expected False due to Capacity Tightness > 0.90")
        self.assertGreater(features["capacity_tightness"], 0.90)

    def test_case_5_fails_spatial_compressibility(self):
        features, is_viable = self.load_and_analyze("data_test_metric_5.txt")
        self.assertFalse(is_viable, "Expected False due to Spatial Compressibility > 1.2")
        self.assertGreater(features["spatial_compressibility_index"], 1.2)

if __name__ == '__main__':
    # unittest.main()
    # prueba de graph analyzer para el p01
    test = TestEvaluateQuantumViability()
    test.setUp()
    features, is_viable = test.load_and_analyze("p01")
    print("Features:", features)
    print("Is Viable:", is_viable)
