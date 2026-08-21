"""
El informe en Markdown: la salida que se lee y se cita.

Solo transforma resultados ya calculados en texto. No entrena nada, no mide nada: eso
permite cambiar como se presenta un experimento sin tocar como se ejecuta.

Las columnas y secciones que salen constantes se omiten. Una columna con el mismo valor
en las 33 filas no distingue nada, y cuando haya varios motores ganando reapareceran
solas.
"""

import time
from collections import Counter

def write_report(path, result, dataset, config, sources, image, check):
    solvers = sorted({s for row in dataset for s in row["cost"]})
    wins = Counter(row["winner"] for row in dataset)
    lines = []

    lines.append("# Arbol de decision: que solver conviene")
    lines.append("")
    lines.append(f"**Fecha:** {time.strftime('%d/%m/%Y %H:%M')}  ")
    lines.append(f"**Datos:** {', '.join(f'`{s}`' for s in sources)}  ")
    lines.append(f"**Instancias:** {len(dataset)}  |  **Solvers considerados:** "
                 f"{', '.join(f'`{s}`' for s in solvers)}  ")
    lines.append(f"**Tolerancia de empate:** {config.tolerance} %  |  "
                 f"**Profundidad maxima:** {config.max_depth}  |  "
                 f"**Minimo por hoja:** {config.min_leaf}  ")
    lines.append("")
    lines.append("Reproducir:")
    lines.append("")
    lines.append("```bash")
    command = f"python decision_tree.py {' '.join(sources)}"
    if config.exclude:
        command += f" --exclude {config.exclude}"
    command += f" --tolerance {config.tolerance} --max-depth {config.max_depth}"
    lines.append(command)
    lines.append("```")
    lines.append("")

    lines.append("## Reparto de victorias")
    lines.append("")
    lines.append("| Solver | Instancias en que conviene |")
    lines.append("|---|---:|")
    # Solo los que ganan algo: los que no aparecen ya estan en "Solvers considerados".
    for solver in solvers:
        if wins.get(solver):
            lines.append(f"| `{solver}` | {wins[solver]} / {len(dataset)} |")
    lines.append("")

    by_time = [row for row in dataset if row["decided_by_time"]]
    if by_time:
        lines.append(f"{len(by_time)} instancias se han decidido **por tiempo**: habia mas de un solver "
                     f"a menos de {config.tolerance} % del mejor coste, y ha ganado el mas rapido.")
        lines.append("")

    lines.append("## El arbol")
    lines.append("")
    if image:
        lines.append(f"![Arbol de decision](./{image})")
        lines.append("")
    lines.append("```")
    lines.extend(result["text"].splitlines())
    lines.append("```")
    lines.append("")

    if not result["validated"]:
        needed = min((row["margin"] for row in dataset if row["margin"] != float("inf")), default=None)
        only = next(iter(result["classes"]))
        lines.append(f"Un solo nodo, porque `{only}` conviene en todas las instancias. La decision no "
                     "depende de la instancia, y eso es lo que el arbol esta diciendo.")
        lines.append("")
        if needed is not None:
            lines.append(f"Le saldran ramas cuando otro motor gane instancias, o cuando `--tolerance` "
                         f"suba por encima del **{needed:.1f} %**, que es lo que separa al ganador del "
                         "segundo en la instancia mas reñida.")
            lines.append("")

        lines.append("## Vale algo?")
        lines.append("")
        lines.append("**No se puede saber, y por eso no hay numeros aqui.** Con una sola clase el "
                     "acierto es del 100 % por construccion: acertar siempre es trivial cuando solo hay "
                     "una respuesta posible. Publicar ese 100 % al lado del de un arbol de verdad "
                     "invitaria a compararlos, y no son comparables.")
        lines.append("")
        lines.append("La validacion cruzada y el contraste de permutacion aparecen solos en cuanto haya "
                     "dos ganadores.")
        lines.append("")
    else:
        lines.append("## Vale algo?")
        lines.append("")
        lines.append("| Medida | Valor |")
        lines.append("|---|---:|")
        lines.append(f"| Acierto en validacion cruzada ({result['folds']} particiones) "
                     f"| {result['accuracy'] * 100:.1f} % ± {result['accuracy_sd'] * 100:.1f} |")
        lines.append(f"| Acierto eligiendo siempre el solver mayoritario | {result['baseline'] * 100:.1f} % |")
        lines.append(f"| p del contraste de permutacion ({config.permutations} barajados) "
                     f"| {result['p_value']:.4f} |")
        lines.append("")
        gain = (result["accuracy"] - result["baseline"]) * 100
        if result["p_value"] < 0.05 and gain > 0:
            lines.append(f"El arbol supera en **{gain:+.1f} puntos** a no tener arbol, y el azar iguala "
                         f"ese acierto en el {result['p_value'] * 100:.2f} % de los barajados. "
                         "**Hay patron y es aprovechable.**")
        else:
            lines.append(f"El arbol queda en **{gain:+.1f} puntos** frente a elegir siempre el solver "
                         f"mayoritario, con p = {result['p_value']:.4f}. **No supera al azar**: con estos "
                         "datos el arbol no aporta sobre la regla fija, y llevarlo a produccion seria "
                         "peor que no tenerlo.")
        lines.append("")

        if result["importances"]:
            lines.append("### Que caracteristicas usa")
            lines.append("")
            lines.append("| Caracteristica | Importancia |")
            lines.append("|---|---:|")
            for name, value in result["importances"]:
                lines.append(f"| `{name}` | {value:.3f} |")
            lines.append("")
            lines.append("Solo aparecen las que el arbol llega a usar en algun corte. Importancia cero no "
                         "significa que la caracteristica no importe: significa que otra correlacionada "
                         "con ella entro antes.")
            lines.append("")

    if check:
        lines.append("## Verificacion del algoritmo")
        lines.append("")
        lines.append("Un arbol trivial no demuestra que el codigo funcione, asi que el algoritmo se "
                     "comprueba aparte sobre un problema de respuesta conocida: 120 muestras donde la "
                     "regla verdadera es `senal > 150`, con dos caracteristicas de puro ruido que debe "
                     "ignorar.")
        lines.append("")
        lines.append("| Comprobacion | Resultado |")
        lines.append("|---|---|")
        lines.append(f"| Caracteristica por la que corta | indice {check['feature_index']} "
                     f"({'correcta' if check['feature_ok'] else 'INCORRECTA'}) |")
        lines.append(f"| Umbral encontrado | {check['threshold']:.2f} (verdadero: 150) |")
        lines.append(f"| Acierto dejando una fuera | {check['accuracy'] * 100:.1f} % |")
        lines.append(f"| Veredicto | **{'PASA' if check['passed'] else 'FALLA'}** |")
        lines.append("")

    lines.append("## Datos por instancia")
    lines.append("")
    predictions = result.get("predictions")

    # Las columnas se arman como (titulo, alineacion, valor) y se descartan las que salen
    # constantes: una columna con el mismo valor en todas las filas no distingue nada, y
    # cuando haya varios motores ganando reapareceran solas.
    columns = [
        ("Instancia", "---", lambda i, r: f"`{r['filename']}`"),
        ("Clientes", "---:", lambda i, r: str(int(r["num_customers"]))),
        ("Depositos", "---:", lambda i, r: str(int(r["num_depots"]))),
        ("Conviene", "---", lambda i, r: f"`{r['winner']}`"),
        ("Margen sobre el 2º", "---:",
         lambda i, r: "-" if r["margin"] == float("inf") else f"{r['margin']:.1f} %"),
        ("Decidida por", "---", lambda i, r: "tiempo" if r["decided_by_time"] else "coste"),
    ]
    if predictions:
        columns.insert(4, ("El arbol dice", "---", lambda i, r: f"`{predictions[i]}`"))

    cells = [[render(i, row) for i, row in enumerate(dataset)] for _, _, render in columns]
    keep = [index for index, values in enumerate(cells) if len(set(values)) > 1]

    lines.append("| " + " | ".join(columns[i][0] for i in keep) + " |")
    lines.append("|" + "|".join(columns[i][1] for i in keep) + "|")
    for row_index in range(len(dataset)):
        lines.append("| " + " | ".join(cells[i][row_index] for i in keep) + " |")
    lines.append("")

    dropped = [columns[i][0] for i in range(len(columns)) if i not in keep]
    if dropped:
        lines.append(f"Se omiten las columnas constantes: {', '.join(dropped)}.")
        lines.append("")

    lines.append("## Como leerlo cuando haya mas motores")
    lines.append("")
    lines.append("- **Un arbol que no bate a la regla mayoritaria no sirve**, aunque el dibujo tenga "
                 "ramas. Esa fila de la tabla es la primera que hay que mirar.")
    lines.append("- **Subir `--max-depth` casi siempre sube el acierto en entrenamiento y lo baja en "
                 "validacion.** Si al subirlo mejora la validacion cruzada, el arbol se quedaba corto; "
                 "si empeora, esta memorizando.")
    lines.append("- **La tolerancia importa mas cuanto mejores sean los motores.** Dos metaheuristicas "
                 "buenas se separaran por decimas en muchas instancias, y esas decimas son semilla. Con "
                 "`--tolerance 0` el arbol aprende ruido.")
    lines.append("- **Conviene excluir las lineas base** (`--exclude RANDOM,GREEDY`) cuando la pregunta "
                 "sea cual de los motores de produccion usar: incluirlas infla el acierto con instancias "
                 "que nadie dudaba.")
    lines.append("")

    lines.append("## Limitaciones")
    lines.append("")
    lines.append(f"- **{len(dataset)} instancias.** Un arbol sin frenos las memoriza. Por eso los valores "
                 "por defecto son conservadores y el acierto que se reporta es siempre en validacion "
                 "cruzada, nunca sobre los datos de entrenamiento.")
    lines.append("- **Los arboles son inestables con pocos datos:** cambiar una instancia puede cambiar "
                 "el primer corte. Antes de llevar una regla al codigo conviene ver si sobrevive a "
                 "reejecutar el experimento con otra semilla.")
    lines.append("- **Las instancias Cordeau no son datos reales de reparto.** Una regla aprendida aqui "
                 "vale para elegir motor en el banco de pruebas; extrapolarla a produccion es una "
                 "hipotesis, no un resultado.")
    lines.append("")

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


