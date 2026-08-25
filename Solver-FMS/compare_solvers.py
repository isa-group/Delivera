#!/usr/bin/env python3
"""
Compara los solvers registrados sobre las instancias Cordeau y deja constancia.

Por que a traves de la API y no llamando a cada motor: es el unico camino que
ejerce el mapeo real y trata a todos los solvers exactamente igual, que es la
condicion para que la comparacion signifique algo.

Cada ejecucion produce TRES ficheros que comparten nombre
(...-2026-08-13-1316-mi-experimento):

  instancias-<fecha>.csv  Una fila por INSTANCIA: las propiedades del problema
                          -clientes, depositos, capacidad, demanda, densidad, BKS- que
                          no dependen de quien lo resuelva ni de cuantas veces.
  datos-<fecha>.csv       Una fila por EJECUCION: instancia, solver, repeticion,
                          parametros, semilla y resultado. Se cruza con la anterior
                          por `filename` y no repite ninguna de sus columnas.
  informe-<fecha>.md      El informe legible, en español: fecha, configuracion,
                          resumen, una tabla por solver y el comparativo por instancia.

Dos ficheros de datos y no uno porque son dos niveles de observacion distintos. Las
propiedades de una instancia valen para la instancia, no para cada una de sus once
ejecuciones: meterlas en cada fila las repite sin añadir nada y estropea cualquier
agregado, porque al promediarlas sobre las ejecuciones las instancias con mas
ejecuciones pesan mas. La division con el informe es la otra: los CSV son para la
maquina y no deben cambiar de forma entre experimentos; el informe es para leerlo y
citarlo.

Que hace distinto a mirar el coste que devuelve cada motor:

1. RECALCULA el coste desde las paradas, en vez de fiarse del que informa el
   motor. Ambos van al CSV, asi que una discrepancia se ve.
2. VALIDA la solucion contra las restricciones de la instancia. Un coste bajo
   obtenido saltandose la duracion maxima no es un buen resultado, y sin esta
   comprobacion lo pareceria.
3. REPITE. Ningun motor salvo el voraz es reproducible, asi que una ejecucion
   suelta no dice nada: hacen falta mejor, media y dispersion.

Este fichero es solo la linea de comandos y el cableado. Lo que hace el trabajo esta
en el paquete `experimentation`, separado para que un analisis posterior pueda usar el
modelo de instancia sin arrastrar el cliente HTTP ni el generador de informes.

Uso:
    python compare_solvers.py                          # las 33 instancias, 3 repeticiones
    python compare_solvers.py --instances p01,p22 --runs 5
    python compare_solvers.py --solvers GENETIC --seed 1234 --label semilla-fija
"""

import argparse
import http.client
import sys
from collections import namedtuple
from pathlib import Path

from experimentation import BASE_DIR
from experimentation.comparison import gateway, instance as instances
from experimentation.comparison.dataset import Output
from experimentation.comparison.report import write_report
from experimentation.comparison.runner import Config, run_instance

DEFAULT_OUT_DIR = BASE_DIR / "results"

# Las tres salidas de un experimento, que comparten nombre y se citan juntas.
Run = namedtuple("Run", "instances runs report")


def reserve_run(out_dir, config):
    """
    Nombres de las tres salidas de este experimento, y el identificador que las une.

    Al minuto y no al segundo porque el nombre se lee y se cita. Dos experimentos dentro
    del mismo minuto -dos pruebas rapidas sobre una instancia- desempatan con un sufijo en
    vez de pisarse: son el registro de un experimento, no un fichero temporal, y perder uno
    en silencio seria peor que un nombre feo.
    """
    for attempt in range(1, 100):
        run_id = config.run_id if attempt == 1 else f"{config.run_id}-{attempt}"
        stem = f"{run_id}-{config.label}" if config.label else run_id
        paths = Run(instances=out_dir / f"instancias-{stem}.csv",
                    runs=out_dir / f"datos-{stem}.csv",
                    report=out_dir / f"informe-{stem}.md")

        if not any(path.exists() for path in paths):
            # El identificador que se guarda en cada fila es el que desempata, no el de la
            # hora: si no, dos experimentos del mismo minuto serian el mismo en el CSV.
            config.run_id = run_id
            return paths

    sys.exit(f"Demasiados experimentos con el mismo nombre en {out_dir}")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Compara solvers sobre las instancias Cordeau y genera informe (.md) y datos (.csv)")
    parser.add_argument("--url", default="http://localhost:8090", help="URL del gateway")
    parser.add_argument("--instances", default="all",
                        help="Instancias separadas por coma (p01,p22), o 'all' para las 33 (por defecto)")
    parser.add_argument("--solvers", default="", help="Por defecto, todos los del catalogo")
    parser.add_argument("--runs", type=int, default=3,
                        help="Repeticiones por solver no determinista (por defecto 3)")
    parser.add_argument("--timeout", type=int, default=600, help="Timeout por peticion, en segundos")
    parser.add_argument("--seed", type=int,
                        help="Semilla base. La repeticion k usa seed+k-1, de modo que el "
                             "experimento entero se puede repetir tal cual")
    parser.add_argument("--out", default=str(DEFAULT_OUT_DIR),
                        help="Directorio donde dejar el informe y los datos (por defecto results/)")
    parser.add_argument("--label", default="",
                        help="Etiqueta del experimento. Va al nombre de los ficheros y a una "
                             "columna del CSV, para distinguir configuraciones al juntarlos")
    return parser.parse_args()


def resolve_solvers(url, wanted):
    """Los solvers salen del catalogo, no de una lista aqui: uno nuevo entra sin tocar nada."""
    try:
        available = gateway.catalog(url)
    except (OSError, http.client.HTTPException) as error:
        sys.exit(f"No se puede leer el catalogo en {url}: {error}")

    if not wanted:
        return available

    names = [s.strip().upper() for s in wanted.split(",")]
    unknown = [s for s in names if s not in available]
    if unknown:
        sys.exit(f"Solver no registrado: {', '.join(unknown)}. Hay: {', '.join(available)}")
    return {s: available[s] for s in names}


def main():
    args = parse_args()
    config = Config(args)

    solvers = resolve_solvers(config.url, args.solvers)
    names = (instances.all_names() if args.instances.strip().lower() == "all"
             else [n.strip() for n in args.instances.split(",")])

    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = BASE_DIR / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    paths = reserve_run(out_dir, config)

    best_known = instances.load_best_known()
    records = []
    # Las propiedades de cada instancia, para el informe. Son las mismas filas que van al
    # CSV de instancias: una por instancia, no una por ejecucion.
    properties = {}

    print(f"Ejecucion {config.run_id}  |  {len(names)} instancias  |  "
          f"solvers {', '.join(solvers)}  |  {args.runs} repeticiones")
    print(f"Instancias en  {paths.instances}")
    print(f"Ejecuciones en {paths.runs}")

    output = Output(paths.instances, paths.runs)

    try:
        for position, name in enumerate(names, start=1):
            if not instances.exists(name):
                print(f"\n{name}: no existe en el banco de instancias, se salta")
                continue
            rows, instance_properties = run_instance(
                config, name, solvers, best_known.get(name), output,
                f"[{position}/{len(names)}] ")
            records += rows
            properties[name] = instance_properties
    except KeyboardInterrupt:
        # Interrumpir un barrido largo es normal. Lo medido hasta aqui se conserva y el
        # informe se genera igual, diciendo cuantas instancias entraron.
        print("\n\nInterrumpido. Se genera el informe con lo medido hasta ahora.")
    finally:
        output.close()
        if records:
            write_report(paths.report, config, records, solvers, properties,
                         {"instances": paths.instances.name, "runs": paths.runs.name})
            print(f"\nInstancias:  {paths.instances}")
            print(f"Ejecuciones: {paths.runs}")
            print(f"Informe:     {paths.report}")
        else:
            paths.instances.unlink(missing_ok=True)
            paths.runs.unlink(missing_ok=True)
            print("\nNo hay ninguna ejecucion que registrar.")


if __name__ == "__main__":
    main()
