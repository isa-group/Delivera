# Metadatos de solvers

Cada solver registrado en la pasarela lleva asociada una **metainformación obligatoria** que lo
describe: qué hace y con qué parámetros se invoca. Es lo que permite que el sistema funcione como
paraguas sobre solvers de tecnologías y paradigmas distintos, y lo que hace comparables sus resultados.

## Por qué

Enfrentar dos algoritmos exige saber que ambos resuelven el mismo problema con la misma configuración.
Si los parámetros viven dentro del código de cada motor, como constantes, un resultado de benchmark no
se puede reproducir ni citar: hay que recompilar para cambiar una población, y nadie sabe con qué
ajustes se obtuvo el número que aparece en la tabla.

Los metadatos resuelven eso publicando los parámetros de cada solver con sus valores por defecto, que
la pasarela **aplica de verdad** antes de invocar al motor. Un experimento queda descrito por el mapa
de parámetros que se envió más la `version` del solver.

## Estructura del descriptor

`GET /api/v1/fms/solvers/{type}` devuelve el descriptor completo. Tiene dos partes.

### 1. Identidad y descripción

| Campo | Significado |
|---|---|
| `type` | Identificador. El mismo valor que se envía en `solverType` al resolver |
| `name` | Nombre legible |
| `description` | **Obligatorio.** Qué hace el algoritmo, en una frase |
| `strategy` | Familia algorítmica: baseline, heurística constructiva, metaheurística, exacto… |
| `technology` | Con qué está implementado. Informativo: la pasarela solo depende del contrato HTTP |
| `version` | Versión del solver. Fijarla es lo que hace citable un resultado de benchmark |
| `deterministic` | Si dos ejecuciones sobre la misma entrada dan la misma solución |
| `status` | Salud del motor, solo si se pide con `includeStatus=true` |

### 2. Parámetros

`parameters` declara cada parámetro de invocación con su **valor por defecto**, que es el que aplica
la pasarela cuando el cliente no lo envía:

```json
{
  "name": "populationSize",
  "description": "Individuos por generacion. Mas poblacion explora mas soluciones distintas a costa de mas tiempo por generacion",
  "type": "INTEGER",
  "defaultValue": 150,
  "min": 10.0,
  "max": 2000.0,
  "required": false
}
```

`type` es `INTEGER` o `DECIMAL`, y dice si el parámetro admite decimales: es lo que `min`/`max` no
pueden expresar, porque `elitismCount` (0–100) y `crossoverProbability` (0–1) se ven iguales en el
descriptor y no lo son. Un parámetro sin `defaultValue` y con `required: true` obliga al cliente a
enviarlo; sin `defaultValue` y sin `required`, el solver decide internamente.

El cliente envía los que quiera cambiar en el mapa `parameters` de la petición; el resto se completan
solos antes de despachar al motor, de forma que la ejecución siempre parte de una configuración
completa y conocida.

Qué acepta cada motor hoy: `RANDOM` y `GREEDY` ninguno, y `GENETIC` trece, documentados uno a uno en
[engines/genetic-engine.md](engines/genetic-engine.md#parámetros).

## Resolución de parámetros

Ocurre en `EngineDispatcher`, antes de enviar el problema al motor:

1. Los parámetros que la petición no trae se completan con su `defaultValue`.
2. Uno no declarado por el solver se descarta con un aviso en el log.
3. Uno que no sea un número, que tenga decimales siendo `INTEGER`, o que quede fuera de `min`/`max`,
   devuelve **400**. Un `12.7` sobre `populationSize` se rechaza en vez de truncarse a 12: el motor no
   debe ejecutar una configuración que nadie ha pedido.
4. Uno `required` sin valor por defecto ni valor enviado devuelve **400**.

El motor recibe así siempre la configuración completa. Vive en el despachador y no en el controlador
porque es un invariante: cualquier vía de entrada (petición directa o carga de instancia) llega al
motor con el mismo contrato.

## Dar de alta un solver

Los metadatos viven en la configuración de la pasarela, no dentro del motor. Esa es la decisión que
hace del sistema un paraguas: un solver escrito en Python, uno comercial o uno exacto se integran
igual, sin implementar ningún contrato Java, solo exponiendo dos endpoints HTTP
(`POST /api/v1/engine/solve` y `GET /actuator/health`).

1. Añadir la constante al enum `TypeSolver`.
2. Añadir su bloque bajo `fms.engines` en `application.yml`, con su descripción y sus parámetros.

```yaml
fms:
  engines:
    ortools:
      url: http://ortools-engine:8094
      display-name: OR-Tools CP-SAT
      description: Solver exacto con limite de tiempo sobre CP-SAT
      strategy: Exacto
      technology: Python 3.12 / OR-Tools
      version: 1.0.0
      deterministic: true
      order: 40
      parameters:
        - name: timeLimitSeconds
          description: Presupuesto de tiempo del solver
          type: INTEGER
          default-value: 60
          min: 1
          max: 3600
```

Ninguna clase Java cambia: el cliente HTTP, el despacho, el catálogo y la resolución de parámetros se
derivan de ese bloque. `enabled: false` retira un motor del catálogo sin borrar su configuración.

Un motor puede ignorar por completo el mapa `parameters` que recibe (es lo que hace el voraz). Si
quiere usarlo, la única regla es que **sus valores por defecto coincidan con los declarados**: si no,
el descriptor estaría describiendo una ejecución que no es la que ocurre.

## Uso en experimentación y benchmarking

Lo que aportan los parámetros es poder lanzar la misma instancia con configuraciones distintas del
mismo algoritmo sin recompilar nada:

```bash
curl -X POST "http://localhost:8090/api/v1/fms/instances/send?fileName=p01&solverType=GENETIC" \
  -H "Content-Type: application/json" \
  -d '{"populationSize": 300, "maxEvaluations": 150000}'
```

Con una salvedad importante: **el genético y el aleatorio no son reproducibles**. Dos ejecuciones con
los mismos parámetros sobre la misma instancia dan resultados distintos, porque su generador aleatorio
arranca de una semilla que no se puede fijar ni consultar. Al comparar dos configuraciones, una
diferencia pequeña de coste puede ser azar y no mejora: conviene repetir cada una varias veces y
comparar medias, no ejecuciones sueltas.

Lo que el descriptor **no** dice es qué restricciones del problema respeta cada motor: que el voraz
ignora la duración máxima de ruta y el número de vehículos, o que el genético sí los tiene en cuenta.
Esa información existe, pero en prosa: está en la tabla comparativa del [README](README.md) y en la
ficha de cada motor. Al comparar resultados hay que tenerla presente, porque un motor que ignora una
restricción puede ganar en distancia precisamente por eso.

## Decisiones y límites conocidos

- **Los metadatos los declara quien integra, no el motor.** Un motor podría exponerlos él mismo y la
  pasarela descubrirlos al arrancar. Se ha preferido la configuración porque no impone nada al solver
  integrado, que es justo lo que permite absorber tecnologías ajenas. El coste es que el descriptor y
  el comportamiento real pueden desincronizarse: son dos artefactos que hay que mantener a la vez.
- **El descriptor solo cubre identidad y parámetros.** No declara funciones objetivo ni características
  del problema soportadas. Se probó un bloque `constraints` con ese vocabulario y se retiró: nada lo
  consumía, duplicaba lo que ya cuenta la documentación de cada motor y obligaba a mantener un catálogo
  de características hipotéticas. Si algún día hace falta decidir por programa si un solver encaja con
  una instancia, ese es el momento de reintroducirlo.
