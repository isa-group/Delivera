# EN
---

# Deployment Guide

## Overview

Delivera uses a modular environment configuration system designed to simplify deployment across multiple environments (local, development, production, etc.).

##  Development Mode: Start Only PostgreSQL Databases

> **Important**
>
> Even if you only plan to run the services from your IDE, you should still complete the initial platform setup:
>
> ```bash
> cd docker
>
> ./use-examples.sh
>
> ./generate-certs.sh
>
> ./generate-keys.sh
>
> ./generate-keys-env.sh
> ```
>
> These files are required by the different services and should be generated before starting development.

> **Space Configuration Required**
>
> Before running the application in development mode, you must configure your Space API keys in the corresponding `application-dev.yml` files.
>
> Update the following properties with your own credentials:
>
> ```yaml
> app:
>   space:
>     api-key: your-api-key
> ```
>
> The application will not be able to communicate with Space services correctly if these values are not configured.
``

Once the setup is complete, start only the PostgreSQL containers with:

```bash
docker compose -f docker-compose.dev.yml up -d delivera-postgres auth-postgres data-postgres
```

This will start:

- `delivera-postgres`
- `auth-postgres`
- `data-postgres`

without starting any application services.

### Stop the Databases

```bash
docker compose -f docker-compose.dev.yml down
```


## Quick Start (Local)

Run Delivera locally using the published Docker Hub images.

### 1. Enter the Docker directory

```bash
cd docker
```

### 2. Create configuration files

```bash
./use-examples.sh
```

> **Important**
>
> Before continuing, review and complete the generated configuration files.
>
> Particular attention should be paid to:
>
> - Space API configuration
> - Database configuration
> - Service URLs

### 3. Generate certificates

```bash
./generate-certs.sh
```

### 4. Generate JWT signing keys

```bash
./generate-keys.sh
```

Generated keys:

- Backup key
- Previous month key
- Current month key
- Next three monthly keys

Optional flags:

```bash
./generate-keys.sh --force
```

```bash
./generate-keys.sh --force-backup
```

### 5. Generate JWT environment configuration

```bash
./generate-keys-env.sh
```

Generated file:

```text
env/.env.jwt
```

### 6. Generate Docker Compose environment

```bash
./generate-compose-env.sh local
```

### 7. Activate the environment

```bash
./use-mode.sh local
```

### 8. Start Delivera

```bash
docker compose -f docker-compose.local.yml up -d
```

### Stop Delivera

```bash
docker compose -f docker-compose.local.yml down
```

## Default Start (Development)

```bash
cd docker

./use-examples.sh

# Review and complete the generated configuration files
# IMPORTANT: configure the Space variables correctly

./generate-certs.sh

./generate-keys.sh

./generate-keys-env.sh

./generate-compose-env.sh dev

./use-mode.sh dev

docker compose -f docker-compose.dev.yml up -d
```

### Stop the Application

```bash
docker compose -f docker-compose.dev.yml down
```

## Default Start (Local)

```bash
cd docker

./use-examples.sh

# Review and complete the generated configuration files
# IMPORTANT: configure the Space variables correctly

./generate-certs.sh

./generate-keys.sh

./generate-keys-env.sh

./generate-compose-env.sh local

./use-mode.sh local

docker compose -f docker-compose.local.yml up -d
```

### Stop the Application

```bash
docker compose -f docker-compose.local.yml down
```

## Solver Execution

The optimization services (FMS Solvers) run independently from the rest of the platform.

### Start the Solvers

From the project root directory:

```bash
cd Solver-FMS/

docker compose up -d
```

### Stop the Solvers

```bash
docker compose down
```

> The solvers are required for the optimization and route calculation features used by Delivera.

---
---
# ES
---
# Deployment Guide

# Guía de Despliegue

## Resumen

Delivera utiliza un sistema modular de configuración de entornos que permite simplificar y automatizar el despliegue de la plataforma en distintos escenarios, como entornos locales, de desarrollo y de producción.


##  Modo Desarrollo: Iniciar Solo las Bases de Datos

> **Importante**
>
> Aunque vayas a ejecutar los servicios directamente desde tu IDE, es necesario realizar previamente la configuración inicial de la plataforma:
>
> ```bash
> cd docker
>
> ./use-examples.sh
>
> ./generate-certs.sh
>
> ./generate-keys.sh
>
> ./generate-keys-env.sh
> ```
>
> Estos archivos son necesarios para el correcto funcionamiento de los servicios y deben generarse antes de comenzar el desarrollo.

> **Configuración de Space Obligatoria**
>
> Antes de ejecutar la aplicación en modo desarrollo, es necesario configurar las claves de la API de Space en los correspondientes archivos `application-dev.yml`.
>
> Actualiza las siguientes propiedades con tus propias credenciales:
>
> ```yaml
> app:
>   space:
>     api-key: tu-api-key
> ```
>
> La aplicación no podrá comunicarse correctamente con los servicios de Space si estos valores no están configurados.

Una vez completada la configuración inicial, puedes arrancar únicamente los contenedores PostgreSQL con:

```bash
docker compose -f docker-compose.dev.yml up -d delivera-postgres auth-postgres data-postgres
```

Esto iniciará:

- `delivera-postgres`
- `auth-postgres`
- `data-postgres`

sin arrancar ninguno de los microservicios de la aplicación.

### Detener las Bases de Datos

```bash
docker compose -f docker-compose.dev.yml down
```


## Inicio Rápido (Local)

Ejecuta Delivera localmente utilizando las imágenes publicadas en Docker Hub.

### 1. Acceder a la carpeta Docker

```bash
cd docker
```

### 2. Crear los archivos de configuración

```bash
./use-examples.sh
```

> **Importante**
>
> Antes de continuar revisa y completa los archivos generados.
>
> Especialmente:
>
> - Configuración de Space
> - Configuración de bases de datos
> - URLs de los servicios

### 3. Generar certificados

```bash
./generate-certs.sh
```

### 4. Generar claves JWT

```bash
./generate-keys.sh
```

Se generarán:

- Clave de respaldo
- Clave del mes anterior
- Clave del mes actual
- Claves para los tres meses siguientes

Opciones disponibles:

```bash
./generate-keys.sh --force
```

```bash
./generate-keys.sh --force-backup
```

### 5. Generar la configuración JWT

```bash
./generate-keys-env.sh
```

Archivo generado:

```text
env/.env.jwt
```

### 6. Generar el entorno de Docker Compose

```bash
./generate-compose-env.sh local
```

### 7. Activar el entorno

```bash
./use-mode.sh local
```

### 8. Ejecutar Delivera

```bash
docker compose -f docker-compose.local.yml up -d
```

### Detener Delivera

```bash
docker compose -f docker-compose.local.yml down
```


##  Ejecución por defecto (Desarrollo)

```bash
cd docker

./use-examples.sh

# Revisar y completar la configuración generada
# IMPORTANTE: configurar correctamente las variables de Space

./generate-certs.sh

./generate-keys.sh

./generate-keys-env.sh

./generate-compose-env.sh dev

./use-mode.sh dev

docker compose -f docker-compose.dev.yml up -d
```

### Detener la aplicación

```bash
docker compose -f docker-compose.dev.yml down

```

##  Ejecución por defecto (Local)

```bash
cd docker

./use-examples.sh

# Revisar y completar la configuración generada
# IMPORTANTE: configurar correctamente las variables de Space

./generate-certs.sh

./generate-keys.sh

./generate-keys-env.sh

./generate-compose-env.sh local

./use-mode.sh local

docker compose -f docker-compose.local.yml up -d
```

### Detener la aplicación

```bash
docker compose -f docker-compose.local.yml down
```


## Ejecución de los Solvers

Los servicios de optimización (FMS Solvers) se ejecutan de forma independiente al resto de la plataforma.

### Iniciar los Solvers

Desde la raíz del proyecto:

```bash
cd Solver-FMS/

docker compose up -d
```

### Detener los Solvers

```bash
docker compose down
```

> Los solvers son necesarios para las funcionalidades de optimización y cálculo de rutas utilizadas por Delivera.