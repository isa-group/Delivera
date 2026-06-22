<p align="center">
  <img src=".github/assets/Delivera banner.png" alt="Delivera" />
</p>

Plataforma SaaS multi-tenant de gestión logística que centraliza pedidos y operaciones de múltiples empresas.

---

## Stack

| Capa | Tecnologías |
|---|---|
| Backend | Java 21, Spring Boot 3.2, Tomcat 10.1 (embedded), Lombok |
| ORM / Migraciones | Hibernate/JPA, Flyway |
| Base de datos | PostgreSQL 16 |
| Autenticación | Argon2 (Bouncy Castle), JWT HS256 (jjwt 0.12) |
| API docs | SpringDoc OpenAPI (Swagger UI en `/swagger-ui/index.html`) |
| Email | Spring Mail |
| Frontend | Vue 3, Vite 7, Vue Router 4, Pinia, Vue i18n, PrimeVue 4 |
| Mapas | Leaflet 1.9, Leaflet.MarkerCluster, OSRM (cálculo de rutas) |
| Linting / Formato | ESLint, Oxlint, Prettier |
| Tests | JaCoCo (backend), Vitest + Playwright (frontend) |
| Calidad | SonarCloud (Quality Gate en CI) |
| Infraestructura | Docker, docker-compose |
| Gestores de dependencias | Maven (backend), npm (frontend) |

## Estructura

```
delivera/
├── backend/          Spring Boot — API REST
├── frontend/         Vue 3 — SPA
├── docker/           docker-compose (PostgreSQL)
└── scripts/          Scripts de seed de base de datos
```

## Requisitos

- Java 21
- Maven
- Node.js >= 20
- Docker Desktop
- `jq` (para los scripts de seed)

## Arranque local

Abre tres consolas y ejecuta en este orden:

**1. Base de datos**

```bash
cd docker
docker compose up -d
```

**2. Backend**

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

```

**3. Auth service**
```bash
cd auth-service
mvn spring-boot:run -D spring-boot.run.profiles=dev

```

**4. Frontend**

```bash
cd frontend
npm install        # solo la primera vez
npm run dev
```

## Puertos

| Servicio | Puerto |
|---|---|
| PostgreSQL (host) | 5433 |
|Core Spring Boot | 8080 |
|Auth Spring Boot | 9090 |
| Vite dev server | 3000 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |

El proxy de Vite reenvía `/api/*` al backend, por lo que no hace falta configurar CORS en desarrollo. Si cambias el puerto de Spring Boot, actualiza también el `target` del proxy en `vite.config.js`.

### Generación de claves públicas y privadas

Para poder crear las claves públicas y privadas que se usan en el auth-service se debe ejecutar el siguiente comando:
```bash
openssl genrsa -out private_key.pem 2048
openssl rsa -in private_key.pem -pubout -out public_key.pem
```
Una vez creado los archivos los movemos y renombramos el archivo con el siguiente formato según la configuración de rotación:
### MONTHLY
- **Dev**:  `auth-service/src/main/resources/keys`, 
- **Prod**: `/app/keys/public_key_2026_06.pem` y  `/app/keys/private_key_2026_06.pem`


Si es rotation MONTHLY ponemos por ejemplo: **private_key_2026_06.pem** y **public_key_2026_06.pem**


### WEEKLY 
Formato de claves semanales (ISO)

```
key-<ISO_YEAR>-W<ISO_WEEK>
```

El año y la semana se calculan usando el estándar ISO:

- La semana empieza en lunes
- La semana 1 es la que contiene el primer jueves del año
- El año (`ISO_YEAR`) no siempre coincide con el año natural de la fecha

---

### Ejemplo 1: Semana que empieza en el año anterior

Fecha: `2025-12-30` (martes)

```
Lun   Mar   Mié   Jue   Vie   Sáb   Dom
29    30    31     1     2     3     4
2025  2025  2025  2026  2026  2026  2026
```

Hay más días en 2026 → pertenece a 2026

Resultado:
```
key-2026-W01
```

---

### Ejemplo 2: Semana que pertenece al año anterior

Fecha: `2021-01-01` (viernes)

```
Lun   Mar   Mié   Jue   Vie   Sáb   Dom
28    29    30    31     1     2     3
2020  2020  2020  2020  2021  2021  2021
```

Hay más días en 2020 → pertenece a 2020

Resultado:
```
key-2020-W53
```

El `ISO_YEAR` se determina por el año que contiene **la mayoría de días de la semana**, no por el año de la fecha concreta.

Ambas se mueven a la carpeta indicada según el entorno y en .yml del entorno correspondiente se pone dentro del apartado app, jwt:
```yml
app:
  jwt:
    rotation:
      enabled: true
      period: MONTHLY
    keys:
      key-2026-06:
        private-key: keys/private_key_2026_06.pem
        public-key: keys/public_key_2026_06.pem
      key-2026-07:
        private-key: keys/private_key_2026_07.pem
        public-key: keys/public_key_2026_07.pem
    active-key-id: ${JWT_ACTIVE_KEY:key-2026-06}
```
Se modifica cuando se añade una nueva y se borra la más antigua.
`active-key-id` se usa cuando el enabled del apartado rotation está desactivado por tanto se escoge la que clave que coincida con el active-key-id o se usa cuando la key con la fecha autogenerada por el rotation no existe aún.



### Base de datos

Las credenciales están en dos sitios sincronizados:

Core backend

- `docker/docker-compose.yml` → `postgres` → `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `backend/src/main/resources/application-dev.yml` → `spring.datasource`

Auth service
- `docker/docker-compose.yml` → `auth-postgres` →  `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`

## Datos de demo

Al arrancar con el perfil `dev` y la base de datos vacía, `DemoDataSeeder` carga automáticamente un conjunto de datos representativo: 3 organizaciones, 6 empresas, 16 unidades operativas, 8 fidelizados y más de 50 pedidos en distintos estados (internos, B2B entre organizaciones y B2C).

**Credenciales (contraseña `demo1234` para todos):**

| Rol | Email |
|---|---|
| Admin global | `admin@delivera.com` |
| Admin RapidLog Central / Retail | `carlos@rapidlog.com` |
| Admin TransNorte Logística | `sofia@transnorte.com` |
| Admin TransNorte Almacenamiento | `paula@transnorte.com` |
| Admin DistriSur Alimentación / Industrial | `elena@distrisur.com` |
| Cliente registrado (mis pedidos) | `clara@cliente.com` |

La contraseña se puede cambiar sin tocar el código con la propiedad `app.demo.seed-password` (o la variable de entorno `APP_DEMO_SEED_PASSWORD`).

## Tests

```bash
# Backend (H2 in-memory, no requiere PostgreSQL)
cd backend && mvn test

# Backend — con informe de cobertura JaCoCo
cd backend && mvn verify

# Frontend — unitarios (Vitest)
cd frontend && npm run test:unit

# Frontend — cobertura (lcov)
cd frontend && npm run test:coverage

# Frontend — E2E Playwright (requiere backend y PostgreSQL arrancados)
cd frontend && npm run test:e2e
cd frontend && npm run test:e2e -- --grep @auth    # por tag
```

Tags E2E disponibles: `@auth` · `@navigation` · `@register` · `@profile` · `@units` · `@orders` · `@tracking`

## CI/CD

| Workflow | Cuándo se ejecuta |
|---|---|
| `ci.yml` | Push/PR a `main` o `develop` — build+test backend y frontend, Trivy IaC scan, SonarCloud + Quality Gate |
| `playwright.yml` | PR + `workflow_dispatch` — tests E2E en Chromium |
| `pr-title.yml` | PR — valida formato `tipo/descripcion` en el título |
| `pr-branch.yml` | PR — valida nombre de rama `tipo/descripcion` |
| `pr-commits.yml` | PR — valida mensajes de commit (Conventional Commits) |
| `release.yml` | `workflow_dispatch` manual — crea tag git + GitHub Release con changelog |

Dependabot revisa dependencias Maven, npm y GitHub Actions semanalmente y abre PRs automáticas.

## Producción

Activa el perfil `prod` con `SPRING_PROFILES_ACTIVE=prod`. Variables de entorno requeridas:

| Variable | Uso |
|---|---|
| `DATABASE_URL` | URL JDBC de PostgreSQL |
| `DATABASE_USER` | Usuario de la base de datos |
| `DATABASE_PASSWORD` | Contraseña de la base de datos |
| `JWT_SECRET` | Secret para firmar los tokens JWT |

