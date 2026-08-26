#!/usr/bin/env python3
"""
Arbol de decision: que solver conviene para una instancia dada.

Entrena, a partir de los datos de compare_solvers, un arbol que elige solver mirando las
caracteristicas de la instancia, y lo valida contra la regla de elegir siempre el mismo.

Los solvers no estan escritos aqui: salen de los datos. El arbol clasifica sobre los que
encuentre en el CSV, sean dos o seis. Con un unico ganador el arbol es un solo nodo, que
es la respuesta correcta: la decision no depende de la instancia.

Este fichero es solo la linea de comandos y el cableado. Lo que hace el trabajo esta en
`experimentation/tree/`, separado para que se pueda reutilizar el modelo sin arrastrar
el generador de informes:

    dataset.py    El cruce de las dos tablas y que significa "conviene": coste, y a
                  igualdad de coste, tiempo
    model.py      Entrenar, validar contra la regla mayoritaria y verificar el algoritmo
    plot.py       El arbol como imagen
    report.py     El informe en Markdown

Requiere pandas, scikit-learn y matplotlib, a diferencia del paquete `comparison`:
    pip install pandas scikit-learn matplotlib

Uso:
    python decision_tree.py                                    # el experimento mas reciente
    python decision_tree.py results/datos-*.csv                # varios experimentos
    python decision_tree.py --exclude RANDOM,GREEDY            # solo candidatos reales
    python decision_tree.py --tolerance 2.0 --max-depth 4
"""

import argparse
import sys
import time
from collections import Counter
from pathlib import Path

from experimentation import BASE_DIR
from experimentation.tree.dataset import build_dataset, load
from experimentation.tree.model import selftest, train
from experimentation.tree.plot import render_tree
from experimentation.tree.report import write_report

DEFAULT_OUT_DIR = BASE_DIR / "results"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Entrena un arbol de decision que elige solver segun la instancia")
    parser.add_argument("csv", nargs="*", help="CSV(s) a analizar. Por defecto, el mas reciente de results/")
    parser.add_argument("--exclude", default="",
                        help="Solvers a dejar fuera, separados por coma. Util para descartar lineas "
                             "base que nunca serian candidatas en produccion")
    parser.add_argument("--tolerance", type=float, default=1.0,
                        help="Margen en %% dentro del cual dos solvers se consideran de igual calidad. "
                             "Entre los empatados gana el mas rapido (por defecto 1.0)")
    parser.add_argument("--max-depth", type=int, default=10, help="Profundidad maxima (por defecto 10)")
    parser.add_argument("--min-leaf", type=int, default=5,
                        help="Instancias minimas por hoja (por defecto 5)")
    parser.add_argument("--folds", type=int, default=10, help="Particiones de la validacion cruzada")
    parser.add_argument("--permutations", type=int, default=2000,
                        help="Permutaciones del contraste de significancia")
    parser.add_argument("--seed", type=int, default=20260819,
                        help="Semilla, para que el arbol sea reproducible")
    parser.add_argument("--out", default=str(DEFAULT_OUT_DIR), help="Directorio de salida")
    parser.add_argument("--skip-selftest", action="store_true",
                        help="No verificar el algoritmo sobre el problema sintetico")
    return parser.parse_args()


def resolve_inputs(paths):
    if paths:
        found = [Path(p) for p in paths if Path(p).exists()]
        if not found:
            sys.exit(f"No existe ninguno de: {', '.join(paths)}")
        return found
    candidates = sorted(DEFAULT_OUT_DIR.glob("datos-*.csv"))
    if not candidates:
        sys.exit(f"No hay ningun datos-*.csv en {DEFAULT_OUT_DIR}. Ejecuta antes compare_solvers.py")
    return [candidates[-1]]


def relative_names(paths):
    """Los CSV de entrada tal como se citan en el informe: relativos a Solver-FMS si
    cuelgan de ahi, y absolutos si vienen de fuera."""
    names = []
    for path in paths:
        resolved = path.resolve()
        try:
            names.append(resolved.relative_to(BASE_DIR).as_posix())
        except ValueError:
            names.append(resolved.as_posix())
    return names


def main():
    config = parse_args()
    excluded = {s.strip().upper() for s in config.exclude.split(",") if s.strip()}
    paths = resolve_inputs(config.csv)
    table = load(paths, excluded)

    dataset = build_dataset(table, config.tolerance)
    if not dataset:
        sys.exit("No hay instancias con resultados utilizables")

    result = train(dataset, config)
    check = None if config.skip_selftest else selftest(config)

    out_dir = Path(config.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y-%m-%d-%H%M")

    image = f"tree-{stamp}.png"
    render_tree(result["model"], out_dir / image, config, dataset)

    report = out_dir / f"tree-{stamp}.md"
    write_report(report, result, dataset, config, relative_names(paths), image, check)

    wins = Counter(row["winner"] for row in dataset)
    solvers = sorted({s for row in dataset for s in row["cost"]})
    print(f"Instancias: {len(dataset)}  |  solvers: {', '.join(solvers)}")
    print(f"Conviene: {', '.join(f'{s} {c}' for s, c in wins.most_common())}")
    if check:
        print(f"Autoverificacion del algoritmo: {'PASA' if check['passed'] else 'FALLA'} "
              f"(umbral {check['threshold']:.1f} sobre 150 real, acierto {check['accuracy'] * 100:.1f} %)")
    if result["validated"]:
        print(f"\nAcierto en validacion cruzada: {result['accuracy'] * 100:.1f} % "
              f"(regla mayoritaria: {result['baseline'] * 100:.1f} %, p = {result['p_value']:.4f})")
    else:
        print("\nUna sola clase: el arbol es un nodo y no se valida.")
    print(f"Imagen: {out_dir / image}")
    print(f"Informe: {report}")


if __name__ == "__main__":
    main()