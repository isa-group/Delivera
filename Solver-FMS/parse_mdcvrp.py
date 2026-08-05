#!/usr/bin/env python3
"""
Parser de instancias MD-CVRP a formato JSON legible.

Formato de entrada:
- Línea 1: type m n t
  - type: 0=VRP, 1=PVRP, 2=MDVRP, 3=SDVRP, 4=VRPTW, 5=PVRPTW, 6=MDVRPTW, 7=SDVRPTW
  - m: número de vehículos por depot
  - n: número de clientes
  - t: número de depots (para MDVRP)
- Siguientes t líneas: D Q (duración máxima, capacidad del vehículo)
- Siguientes n líneas: clientes (i x y d q f a list...)
- Últimas t líneas: depots (i x y d q f)
"""

import json
from pathlib import Path
from typing import Dict, Any

TYPE_NAMES = {
    0: "VRP",
    1: "PVRP",
    2: "MDVRP",
    3: "SDVRP",
    4: "VRPTW",
    5: "PVRPTW",
    6: "MDVRPTW",
    7: "SDVRPTW"
}


def parse_instance(filepath: Path) -> Dict[str, Any]:
    """Parsea una instancia MD-CVRP y retorna un diccionario estructurado."""
    with open(filepath, 'r') as f:
        lines = [line.strip() for line in f if line.strip()]
    
    # Línea 1: type m n t
    header = lines[0].split()
    problem_type = int(header[0])
    num_vehicles = int(header[1])
    num_customers = int(header[2])
    num_depots = int(header[3])
    
    result = {
        "filename": filepath.name,
        "problem_type": TYPE_NAMES.get(problem_type, f"Unknown({problem_type})"),
        "problem_type_code": problem_type,
        "vehicles_per_depot": num_vehicles,
        "num_customers": num_customers,
        "num_depots": num_depots,
        "depots": [],
        "customers": []
    }
    
    # Siguientes t líneas: información de cada depot (D Q)
    line_idx = 1
    for i in range(num_depots):
        parts = lines[line_idx].split()
        depot_info = {
            "depot_id": i + 1,
            "max_duration": int(parts[0]),
            "vehicle_capacity": int(parts[1])
        }
        result["depots"].append(depot_info)
        line_idx += 1
    
    # Siguientes n líneas: clientes
    for i in range(num_customers):
        parts = lines[line_idx].split()
        customer = {
            "id": int(parts[0]),
            "x": float(parts[1]),
            "y": float(parts[2]),
            "service_duration": int(parts[3]),
            "demand": int(parts[4]),
            "visit_frequency": int(parts[5]),
            "num_combinations": int(parts[6])
        }
        
        # Lista de combinaciones de visita (si existe)
        if customer["num_combinations"] > 0:
            combinations = []
            for j in range(customer["num_combinations"]):
                combinations.append(int(parts[7 + j]))
            customer["visit_combinations"] = combinations
        
        # Ventanas de tiempo (si existen)
        time_window_start_idx = 7 + customer["num_combinations"]
        if len(parts) > time_window_start_idx:
            customer["time_window_earliest"] = int(parts[time_window_start_idx])
        if len(parts) > time_window_start_idx + 1:
            customer["time_window_latest"] = int(parts[time_window_start_idx + 1])
        
        result["customers"].append(customer)
        line_idx += 1
    
    # Últimas t líneas: coordenadas de depots
    for i in range(num_depots):
        parts = lines[line_idx].split()
        depot_coords = {
            "id": int(parts[0]),
            "x": float(parts[1]),
            "y": float(parts[2]),
            "service_duration": int(parts[3]),
            "demand": int(parts[4]),
            "visit_frequency": int(parts[5])
        }
        # Fusionar con la información del depot existente
        result["depots"][i].update(depot_coords)
        line_idx += 1
    
    return result


def main():
    """Parsea todas las instancias y genera archivos JSON."""
    instances_dir = Path("Solver-FMS/instances-MD-CVRP")
    output_dir = Path("Solver-FMS/instances-MD-CVRP-JSON")
    
    # Crear directorio de salida
    output_dir.mkdir(exist_ok=True)
    
    # Parsear todas las instancias
    instances = []
    for filepath in sorted(instances_dir.iterdir()):
        if filepath.is_file():
            print(f"Parseando {filepath.name}...")
            try:
                instance = parse_instance(filepath)
                instances.append(instance)
                
                # Guardar JSON individual
                output_file = output_dir / f"{filepath.name}.json"
                with open(output_file, 'w', encoding='utf-8') as f:
                    json.dump(instance, f, indent=2, ensure_ascii=False)
                print(f"  [OK] {output_file}")
            except Exception as e:
                print(f"  [ERROR] {e}")
    
    # Estadísticas
    print(f"\nEstadísticas:")
    print(f"  Total de instancias: {len(instances)}")
    print(f"  Total de clientes: {sum(i['num_customers'] for i in instances)}")
    print(f"  Total de depots: {sum(i['num_depots'] for i in instances)}")


if __name__ == "__main__":
    main()
