"""
De las dos tablas del experimento a una tabla por instancia, con la etiqueta de que
solver conviene.

El nivel de observacion del arbol es la INSTANCIA: una fila, una decision. Los datos
llegan en dos ficheros -las propiedades del problema en uno, las ejecuciones en otro- y
aqui se cruzan por `filename`: las ejecuciones se agregan hasta el nivel de la instancia
y se le pegan sus propiedades.

"""

import sys

import pandas as pd

# Las propiedades con las que el arbol decide. Son las de la tabla de instancias menos
# dos, y las dos ausencias son deliberadas:
#
#   `bks`           es la referencia contra la que se mide el acierto, no un dato
#                   disponible al elegir solver. Entrenar con el seria mirar la solucion.
#   `max_duration`  esta vacio en las 11 instancias sin limite, y eso no es un numero
#                   que el arbol pueda cortar. Lo que si es una propiedad del problema
#                   siempre presente es SI hay limite, y eso es `has_duration_limit`.
FEATURES = [
    "num_customers", "num_depots", "vehicles_per_depot", "vehicle_capacity",
    "has_duration_limit", "total_demand", "load_ratio", "avg_service_duration",
    "customers_per_depot", "area", "customer_density", "mean_nn_distance",
    "mean_nearest_depot_distance",
]

# Los CSV antiguos, de cuando las propiedades iban repetidas en cada fila de ejecucion,
# traian otro nombre para esta. Se traduce al leerlos para no tirar experimentos hechos.
LEGACY_NAMES = {"mean_nearest_depot_distance": "mean_depot_distance"}


def load(paths, excluded):
    """
    Carga los CSV de ejecuciones y propiedades de un experimento, y los cruza por
    `filename` para que cada fila de ejecucion tenga sus propiedades. Devuelve solo las
    ejecuciones factibles y de solvers no excluidos, y comprueba que todas las
    ejecuciones tengan propiedades: si no, sale con error.
    """
    joined = []
    for path in paths:
        runs = pd.read_csv(path)
        properties = read_properties(path, runs)
        # Las propiedades solo pueden venir de su tabla. Los CSV antiguos ademas las
        # traian copiadas en cada fila de ejecucion, y cruzar sin quitarlas dejaria un
        # `num_customers_x` y un `num_customers_y` en vez de la columna.
        copies = {*properties.columns, *LEGACY_NAMES.values(), "max_duration", "bks"}
        runs = runs.drop(columns=copies - {"filename"}, errors="ignore")
        joined.append(runs.merge(properties, on="filename", how="left"))
    table = pd.concat(joined, ignore_index=True)

    usable = table[(table["status"] == "ok") & flag(table["feasible"])
                   & ~table["solver"].isin(excluded)]
    if usable.empty:
        sys.exit("No queda ninguna ejecucion factible despues de filtrar")

    missing = sorted(usable.loc[usable[FEATURES].isna().any(axis=1), "filename"].unique())
    if missing:
        shown = ", ".join(missing[:5]) + (f" y {len(missing) - 5} mas" if len(missing) > 5 else "")
        sys.exit(f"Sin propiedades para {shown}. Falta el CSV de instancias del "
                 "experimento: tiene que estar junto al de ejecuciones, con el mismo "
                 "nombre y el prefijo `instancias-` en vez de `datos-`.")
    return usable


def read_properties(path, runs):
    """
    Lee las propiedades de la instancia, que pueden venir de un CSV hermano o del
    formato antiguo de ejecuciones. Devuelve un DataFrame con una fila por instancia y
    las columnas de `FEATURES` mas `filename`.
    """
    sibling = path.with_name(path.name.replace("datos-", "instancias-", 1))
    if sibling != path and sibling.exists():
        return pd.read_csv(sibling).drop_duplicates("filename")
    return from_legacy(runs)


def from_legacy(runs):
    """Un CSV del formato antiguo, reducido y traducido al de la tabla de instancias."""
    if LEGACY_NAMES["mean_nearest_depot_distance"] not in runs.columns:
        # Ni hermano ni formato antiguo: no hay propiedades. Lo dice `load`, que sabe
        # que instancias se quedan sin ellas.
        return pd.DataFrame(columns=["filename", *FEATURES])

    unique = runs.drop_duplicates("filename")
    names = {LEGACY_NAMES.get(f, f): f for f in FEATURES if f != "has_duration_limit"}
    properties = unique[["filename", *names]].rename(columns=names)
    # El formato antiguo no tenia la bandera: se deduce del 0 que usaba como "sin limite".
    properties["has_duration_limit"] = unique["max_duration"].fillna(0) != 0
    return properties


def flag(values):
    """
    Convierte una columna de strings a booleans. Los CSV antiguos traian "true"/"false"
    en vez de 1/0, y eso no es un booleano. Esta funcion normaliza ambos formatos a un booleano, para poder filtrar por factibilidad.
    """
    if values.dtype == bool:
        return values
    return values.astype(str).str.lower() == "true"


def build_dataset(table, tolerance):
    """
    De las dos tablas del experimento a una tabla por instancia, con la etiqueta de que
    solver conviene. El nivel de observacion del arbol es la INSTANCIA: una fila, una
    decision. Los datos llegan en dos ficheros -las propiedades del problema en uno, las
    ejecuciones en otro- y aqui se cruzan por `filename`: las ejecuciones se agregan hasta
    el nivel de la instancia y se le pegan sus propiedades.
    """
    dataset = []
    for name, runs in table.groupby("filename", sort=True):
        # Cada solver se queda con su mejor coste y su tiempo medio: ahi es donde las
        # repeticiones se agregan hasta el nivel de la instancia.
        solvers = runs.groupby("solver").agg(cost=("cost", "min"),
                                             elapsed=("elapsed_ms", "mean"))
        ranked = solvers["cost"].sort_values()
        best_cost = ranked.iloc[0]
        margin = ((ranked.iloc[1] - best_cost) / best_cost * 100
                  if len(ranked) > 1 else float("inf"))

        equivalent = solvers[(solvers["cost"] - best_cost) / best_cost * 100 <= tolerance]
        properties = runs.iloc[0]

        dataset.append({
            **{f: float(properties[f]) for f in FEATURES},
            "filename": name,
            "cost": solvers["cost"].to_dict(),
            "elapsed": solvers["elapsed"].to_dict(),
            "winner": equivalent["elapsed"].idxmin(),
            "cheapest": ranked.index[0],
            "runner_up": ranked.index[1] if len(ranked) > 1 else None,
            "margin": margin,
            # Mas de un solver dentro del margen: la instancia no la ha decidido el
            # coste, la ha decidido el tiempo.
            "decided_by_time": len(equivalent) > 1,
        })
    return dataset
