"""
El informe en Markdown: la salida que se lee y se cita.

Solo transforma las filas ya medidas en texto. No mide nada, no habla con nadie: eso
permite cambiar como se presenta un experimento sin tocar como se ejecuta.
"""

import math
import statistics
import time

from .dataset import digest, mean_time
from .gateway import declared_defaults


def write_report(path, config, records, solvers, properties, sources):
    """
    Genera un informe en Markdown a partir de los datos de un experimento. Se puede
    abrir en un navegador o convertir a PDF con pandoc.
    """
    grouped = {}
    for row in records:
        grouped.setdefault((row["filename"], row["solver"]), []).append(row)

    names = list(dict.fromkeys(r["filename"] for r in records))
    summaries = {key: digest(rows) for key, rows in grouped.items()}
    wins = count_wins(names, solvers, summaries)

    lines = []
    lines += report_header(config, records, names, solvers, sources)
    lines += report_solver_catalog(solvers)
    lines += report_global(names, solvers, summaries, wins)
    lines += report_by_instance(names, solvers, summaries, properties)
    lines += report_per_solver(names, solvers, summaries, properties)
    lines += report_incidents(records)
    lines += report_notes()

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def count_wins(names, solvers, summaries):
    """
    Gana la instancia el solver con el mejor coste FACTIBLE. Si ninguno entrega una
    solucion valida, la instancia no la gana nadie: no hay nada que comparar.
    """
    wins = {solver: 0 for solver in solvers}
    for name in names:
        candidates = [(s, summaries[(name, s)]) for s in solvers
                      if summaries.get((name, s)) and summaries[(name, s)]["any_feasible"]]
        if candidates:
            wins[min(candidates, key=lambda item: item[1]["best_cost"])[0]] += 1
    return wins


def report_header(config, records, names, solvers, sources):
    executions = len(records)
    failed = sum(1 for r in records if r["status"] == "error")
    elapsed = time.time() - config.started_at

    return [
        "# Comparativa de solvers MD-CVRP",
        "",
        f"**Fecha de ejecución:** {config.started.strftime('%d/%m/%Y %H:%M:%S')}  ",
        f"**Identificador de la ejecución:** `{config.run_id}`"
        + (f" · **etiqueta:** `{config.label}`" if config.label else "") + "  ",
        f"**Duración total:** {human_duration(elapsed)}  ",
        f"**Gateway:** `{config.url}`  ",
        f"**Commit del repositorio:** `{config.commit}`  ",
        f"**Instancias:** [`{sources['instances']}`](./{sources['instances']}) - "
        f"{len(names)} filas, una por instancia  ",
        f"**Ejecuciones:** [`{sources['runs']}`](./{sources['runs']}) - "
        f"{executions} filas, una por ejecucion"
        + (f", {failed} con error" if failed else "") + "  ",
        "",
        "| Configuración | Valor |",
        "|---|---|",
        f"| Instancias | {len(names)} |",
        f"| Solvers | {', '.join(solvers)} |",
        f"| Repeticiones por solver no determinista | {config.runs} |",
        f"| Semilla base | {config.seed if config.seed is not None else 'sorteada por el motor'} |",
        f"| Timeout por petición | {config.timeout} s |",
        "",
        "Reproducir esta ejecución:",
        "",
        "```bash",
        config.command,
        "```",
        "",
    ]


def report_solver_catalog(solvers):
    """
    Los descriptores, en el propio informe. Sin ellos el resultado no es citable: un
    coste no significa nada si no consta con que version y con que parametros salio.
    """
    lines = ["## Solvers evaluados", "",
             "| Solver | Nombre | Estrategia | Tecnología | Versión | Determinista |",
             "|---|---|---|---|---|---|"]
    for solver, info in solvers.items():
        lines.append(
            f"| `{solver}` | {info.get('name', '')} | {info.get('strategy', '')} | "
            f"{info.get('technology', '')} | {info.get('version', '')} | "
            f"{'sí' if info.get('deterministic') else 'no'} |")

    lines += ["", "Parámetros con los que se ha ejecutado cada uno (los valores por defecto "
                  "del descriptor, que son los que aplica la pasarela):", ""]
    for solver, info in solvers.items():
        defaults = declared_defaults(info)
        rendered = ", ".join(f"`{k}={v}`" for k, v in sorted(defaults.items())) or "ninguno"
        lines.append(f"- **{solver}**: {rendered}")
    lines.append("")
    return lines


def report_global(names, solvers, summaries, wins):
    lines = ["## Resumen global", "",
             "| Solver | Instancias | Gap medio | Gap mediano | Mejor gap | Peor gap "
             "| Victorias | Factibles | Viajes/vehículo | Tiempo medio |",
             "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]

    for solver in solvers:
        entries = [summaries[(n, solver)] for n in names if summaries.get((n, solver))]
        if not entries:
            lines.append(f"| `{solver}` | 0 | - | - | - | - | 0 | - | - | - |")
            continue

        gaps = [e["gap"] for e in entries if e["gap"] is not None]
        feasible = sum(e["feasible"] for e in entries)
        runs = sum(e["runs"] for e in entries)
        vehicles = sum(e["vehicles"] for e in entries)
        trips = sum(e["trips"] for e in entries)

        lines.append(
            f"| `{solver}` | {len(entries)} | {pct(statistics.fmean(gaps)) if gaps else '-'} "
            f"| {pct(statistics.median(gaps)) if gaps else '-'} "
            f"| {pct(min(gaps)) if gaps else '-'} | {pct(max(gaps)) if gaps else '-'} "
            f"| {wins[solver]} | {feasible}/{runs} "
            f"| {f'{trips / vehicles:.2f}' if vehicles else '-'} "
            f"| {mean_time(statistics.fmean(e['mean_time'] for e in entries))} |")

    lines += ["", "El **gap** compara el mejor coste factible contra la mejor solución "
                  "publicada (BKS). Las **victorias** cuentan las instancias en las que el "
                  "solver da el mejor coste factible de todos.", "",
              "**Viajes/vehículo** son rutas entre vehículos distintos. `1,00` es un vehículo "
              "por ruta; por encima, hay vehículos que salen más de una vez. No es una "
              "violación -la flota se respeta igual- pero sí una diferencia de fondo entre "
              "motores que el coste no refleja: servir una instancia con la flota o servirla "
              "dándole varias vueltas no es lo mismo. El detalle por ejecución está en la "
              "columna `extra_trips` del CSV.", ""]
    return lines


def report_by_instance(names, solvers, summaries, properties):
    lines = ["## Comparativa por instancia", "",
             "Mejor coste factible de cada solver sobre cada instancia.", "",
             "| Instancia | Clientes | Depósitos | Dur. máx | BKS | "
             + " | ".join(f"`{s}`" for s in solvers) + " | Ganador |",
             "|---|---:|---:|---:|---:|" + "---:|" * len(solvers) + "---|"]

    for name in names:
        row = properties[name]
        limit = int(row["max_duration"]) if row["max_duration"] else "-"
        cells = []
        best_solver, best_cost = None, math.inf

        for solver in solvers:
            summary = summaries.get((name, solver))
            if summary is None:
                cells.append("-")
                continue
            # El asterisco marca al solver que no ha entregado ninguna solucion valida:
            # su coste esta ahi para verlo, pero no compite.
            mark = "" if summary["any_feasible"] else " \\*"
            cells.append(f"{summary['best_cost']:.2f}{mark}")
            if summary["any_feasible"] and summary["best_cost"] < best_cost:
                best_solver, best_cost = solver, summary["best_cost"]

        bks = row.get("bks")
        lines.append(
            f"| `{name}` | {row['num_customers']} | {row['num_depots']} | {limit} | "
            f"{f'{bks:.2f}' if bks else '-'} | " + " | ".join(cells) + " | "
            + (f"`{best_solver}`" if best_solver else "-") + " |")

    lines += ["", "\\* ninguna ejecución factible: el coste no es comparable con el resto.", ""]
    return lines


def report_per_solver(names, solvers, summaries, properties):
    lines = ["## Detalle por solver", ""]

    for solver, info in solvers.items():
        lines += [f"### `{solver}` - {info.get('name', '')} v{info.get('version', '')}", "",
                  f"{info.get('description', '')}", "",
                  "| Instancia | Clientes | Depósitos | n | Mejor | Media | Desv | BKS "
                  "| Gap | Rutas | Tiempo | Factible | Semilla |",
                  "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"]

        for name in names:
            summary = summaries.get((name, solver))
            row = properties[name]
            if summary is None:
                lines.append(f"| `{name}` | {row['num_customers']} | {row['num_depots']} "
                             f"| 0 | - | - | - | - | - | - | - | - | - |")
                continue

            bks = row.get("bks")
            stdev = f"{summary['stdev']:.2f}" if summary["stdev"] is not None else "-"
            seed = summary["best"]["seed"] or "-"

            lines.append(
                f"| `{name}` | {row['num_customers']} | {row['num_depots']} | {summary['runs']} "
                f"| {summary['best_cost']:.2f} | {summary['mean_cost']:.2f} | {stdev} "
                f"| {f'{bks:.2f}' if bks else '-'} "
                f"| {pct(summary['gap']) if summary['gap'] is not None else '-'} "
                f"| {summary['routes']} | {mean_time(summary['mean_time'])} "
                f"| {summary['feasible']}/{summary['runs']} | {seed} |")

        lines.append("")
    return lines


def report_incidents(records):
    failures = [r for r in records if r["status"] == "error"]
    infeasible = [r for r in records if r["status"] == "ok" and r["feasible"] == "false"]
    if not failures and not infeasible:
        return ["## Incidencias", "", "Ninguna: todas las ejecuciones terminaron y todas "
                                      "las soluciones cumplen las restricciones.", ""]

    lines = ["## Incidencias", ""]
    if failures:
        lines += ["### Ejecuciones fallidas", "",
                  "| Instancia | Solver | Repetición | Error |", "|---|---|---:|---|"]
        for row in failures:
            lines.append(f"| `{row['filename']}` | `{row['solver']}` | {row['repetition']} "
                         f"| {row['error']} |")
        lines.append("")

    if infeasible:
        # Agrupadas por instancia y solver: un motor que ignora una restriccion la ignora
        # en las tres repeticiones, y listarlas una a una llenaria el informe de ruido.
        grouped = {}
        for row in infeasible:
            grouped.setdefault((row["filename"], row["solver"]), []).append(row)

        lines += ["### Soluciones que incumplen restricciones", "",
                  "Un coste bajo obtenido saltándose una restricción no es un buen "
                  "resultado. Una fila por instancia y solver; el detalle de cada "
                  "ejecución está en el CSV.", "",
                  "| Instancia | Solver | Ejecuciones | Violaciones | Ejemplo |",
                  "|---|---|---:|---:|---|"]
        for (name, solver), rows in grouped.items():
            worst = max(rows, key=lambda r: r["violations"])
            counts = {r["violations"] for r in rows}
            rendered = str(worst["violations"]) if len(counts) == 1 \
                else f"{min(counts)}–{max(counts)}"
            lines.append(f"| `{name}` | `{solver}` | {len(rows)} | {rendered} "
                         f"| {worst['violations_detail']} |")
        lines.append("")
    return lines


def report_notes():
    return [
        "## Notas metodológicas",
        "",
        "- **Los datos van en dos ficheros porque hay dos niveles de observación.** Las "
        "propiedades del problema -clientes, depósitos, capacidad, densidad, BKS- son de "
        "la instancia y no cambian porque se resuelva once veces: van una sola vez, en "
        "el fichero de instancias. El coste, el tiempo y la semilla son de cada "
        "ejecución: van en el de ejecuciones. Se cruzan por `filename`. Repetir las "
        "primeras en cada fila de las segundas no añadía información y sí sesgaba "
        "cualquier media: al agregar sobre las ejecuciones, las instancias con más "
        "ejecuciones pesarían más.",
        "- **El coste es el recalculado desde las paradas**, no el que informa el motor. "
        "El fichero de ejecuciones trae los dos (`cost` y `reported_cost`): si alguna vez "
        "dejaran de coincidir, se vería.",
        "- **La factibilidad se valida en cada ejecución**: cliente servido exactamente una "
        "vez, carga informada igual a la suma de demandas, capacidad, duración máxima "
        "(distancia + tiempos de servicio) y vehículos por depósito. Un coste por debajo "
        "del BKS no es un récord, es la señal de que se está violando una restricción.",
        "- **La desviación mide variabilidad real del algoritmo**, no error de medida. Si "
        "dos configuraciones se separan menos que ella, la diferencia es ruido. Con menos "
        "de cinco repeticiones no es fiable: el genético converge a la misma solución a "
        "menudo y dos vueltas pueden dar cero dispersión aparente.",
        "- **La semilla es la de la mejor repetición.** Reenviarla en `parameters` "
        "reproduce exactamente esa solución. Aparece vacía en los solvers deterministas.",
        "- **El número de repeticiones lo decide el descriptor**: un solver que declara "
        "`deterministic: true` se ejecuta una sola vez porque repetirlo solo gasta tiempo.",
        "- **Comparar solvers que respetan restricciones distintas es engañoso.** El voraz "
        "ignora la duración máxima y el número de vehículos, así que puede ganar en "
        "distancia precisamente por eso. Por ese motivo el mejor coste y las victorias se "
        "calculan solo sobre ejecuciones factibles.",
        "",
    ]


def pct(value):
    return f"{value:+.2f} %"


def human_duration(seconds):
    if seconds < 60:
        return f"{seconds:.0f} s"
    minutes, rest = divmod(int(seconds), 60)
    if minutes < 60:
        return f"{minutes} min {rest} s"
    hours, minutes = divmod(minutes, 60)
    return f"{hours} h {minutes} min"
