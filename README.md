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

**3. Frontend**

```bash
cd frontend
npm install        # solo la primera vez
npm run dev
```

## Puertos

| Servicio | Puerto |
|---|---|
| PostgreSQL (host) | 5433 |
| Spring Boot | 8080 |
|FMS | 8082 |
| Vite dev server | 3000 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |

El proxy de Vite reenvía `/api/*` al backend, por lo que no hace falta configurar CORS en desarrollo. Si cambias el puerto de Spring Boot, actualiza también el `target` del proxy en `vite.config.js`.

### Base de datos

Las credenciales están en dos sitios sincronizados:

- `docker/docker-compose.yml` → `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `backend/src/main/resources/application-dev.yml` → `spring.datasource`

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

