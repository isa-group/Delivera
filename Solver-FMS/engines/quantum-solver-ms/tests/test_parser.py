import unittest
from unittest.mock import patch, mock_open

from domain.models import RoutingRequest
from utils.parser import CordeauParser

class TestCordeauParser(unittest.TestCase):
    def setUp(self):
        # 1. Definimos una instancia Cordeau controlada en memoria.
        # type=2 (MD-CVRP), m=2 (vehículos/depósito), n=2 (clientes), t=2 (depósitos)
        # La línea 1 dicta las dimensiones globales.
        # Las líneas 2 y 3 dictan duración (D) y capacidad (Q) de los depósitos.
        self.mock_file_content = """2 2 2 2
0 100
0 150
1 10.0 15.0 0 20 1 4 1 2 4 8
2 20.0 25.0 0 30 1 4 1 2 4 8
3 50.0 50.0 0 0 0 0 0 0 0 0
4 60.0 60.0 0 0 0 0 0 0 0 0
"""

    @patch('builtins.open', new_callable=mock_open)
    def test_parse_instance_dimensions(self, mock_file):
        """Verifica que las dimensiones del grafo se extraen correctamente."""
        # Engañamos a Python para que lea nuestro string en lugar de un archivo real
        mock_file.return_value.readlines.return_value = self.mock_file_content.splitlines(True)

        # Ejecutamos nuestro parser
        request = CordeauParser.parse_instance("dummy_path.txt", "TestInstance")

        # Verificamos Nombre
        self.assertEqual(request.instance_name, "TestInstance")
        
        # Verificamos Nodos (n=2 clientes, t=2 depósitos)
        self.assertEqual(len(request.customers), 2)
        self.assertEqual(len(request.depots), 2)

        # Verificamos Flota: m=2 vehículos por t=2 depósitos = 4 vehículos totales
        self.assertEqual(len(request.vehicles), 4)

    @patch('builtins.open', new_callable=mock_open)
    def test_parse_instance_data_accuracy(self, mock_file):
        """Verifica que las capacidades y demandas se asignan con precisión."""
        mock_file.return_value.readlines.return_value = self.mock_file_content.splitlines(True)
        request = CordeauParser.parse_instance("dummy_path.txt", "TestInstance")

        # Comprobamos la asignación de capacidades de los depósitos
        # Vehículos 1 y 2 (del depósito 1) deben tener capacidad 100
        self.assertEqual(request.vehicles[0].capacity, 100)
        self.assertEqual(request.vehicles[1].capacity, 100)
        
        # Vehículos 3 y 4 (del depósito 2) deben tener capacidad 150
        self.assertEqual(request.vehicles[2].capacity, 150)
        self.assertEqual(request.vehicles[3].capacity, 150)

        # Comprobamos la precisión de los datos geométricos del primer cliente
        self.assertEqual(request.customers[0].id, "1")
        self.assertEqual(request.customers[0].x, 10.0)
        self.assertEqual(request.customers[0].y, 15.0)
        self.assertEqual(request.customers[0].demand, 20)

        # Comprobamos la propiedad computada de la demanda total (20 + 30 = 50)
        self.assertEqual(request.total_demand, 50)

if __name__ == '__main__':
    unittest.main()