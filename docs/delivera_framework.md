# EN
---
# Delivera Framework

## Infrastructure Framework for Spring Boot Microservices

> **Repository name:** `microservice-client-starter`
>
> The repository keeps the historical name `microservice-client-starter`, although its scope has evolved beyond a traditional Spring Boot starter. Today it provides a complete infrastructure framework for microservice-based architectures, including communication, security, distributed operations and external platform integrations.

---

# Introduction

## What is Delivera Framework?

Delivera Framework is an infrastructure framework designed to simplify the development of Spring Boot microservices by providing a common set of components, abstractions and architectural patterns frequently required in distributed systems.

When building a microservice ecosystem, development teams often need to repeatedly implement the same technical concerns:

- Service-to-service communication.
- Internal authentication.
- Transport security.
- JWT validation.
- Error handling.
- Retry strategies.
- Service discovery.
- Distributed consistency.
- Integration with external platforms.

Although these concerns are not part of the business domain itself, they represent a significant amount of repetitive code that must be implemented, tested and maintained across multiple services.

Delivera Framework centralizes these responsibilities into a reusable infrastructure layer, allowing developers to focus primarily on business logic while reusing a common and consistent implementation across the entire platform.

---

# Objectives

The framework was designed to solve the following problems commonly found in microservice architectures:

## Reduce duplicated infrastructure code

Instead of implementing HTTP clients, security filters, retry policies and utility classes in every service, these capabilities are provided by a shared framework.

## Standardize service-to-service communication

All internal services use the same communication model, security rules, authentication headers and configuration structure.

## Improve internal security

The framework provides multiple security layers including:

- Mutual TLS (mTLS).
- Internal API Keys.
- JWT validation.
- Route-level authorization.

## Simplify service discovery

Services can communicate using logical names instead of hardcoded URLs.

## Facilitate distributed operations

The framework provides a compensable transaction model that helps maintain consistency when interacting with multiple services or external systems.

## Integrate subscription and consumption validation

The framework includes a dedicated integration layer for SPACE, allowing services to validate feature limits and register resource consumption.

---

# Core Features

The framework is divided into several functional modules.

## Reactive HTTP Client

Delivera Framework provides a unified abstraction over Spring WebClient.

Instead of manually creating requests, developers can use a fluent builder to construct calls in a consistent manner.

Example:

```java
client.request()
    .service("auth-service")
    .path("/internal/auth/check")
    .method(HttpMethod.GET);
```

The framework automatically handles:

- URL resolution.
- Base path resolution.
- Header injection.
- Protocol selection.
- Error management.
- Retry strategies.
- Logging.

---

## Logical Service Resolution

Services can be referenced using logical names rather than physical URLs.

Configuration:

```yaml
delivera:
  client:
    service-hosts:
      auth-service: https://localhost:9091

    service-base-paths:
      auth-service: /api/v2
```

Usage:

```java
client.request()
    .service("auth-service")
    .path("/internal/user");
```

Resolved URL:

```text
https://localhost:9091/api/v2/internal/user
```

This approach removes infrastructure concerns from application code and centralizes service locations in configuration files.

---

## Internal Authentication

The framework provides automatic service identification and authentication between internal services.

When a request is marked as internal:

```java
.useInternal()
```

the framework automatically injects:

```http
X-Service-Name
X-Internal-Key
```

These headers allow the receiving service to:

- Identify the caller.
- Validate its internal API key.
- Apply authorization rules.

This mechanism is designed to operate together with mTLS, providing both service authentication and encrypted transport.

---

## Mutual TLS (mTLS)

The framework supports Mutual TLS connections between services.

Unlike traditional HTTPS where only the server presents a certificate, mTLS requires both parties to authenticate each other using trusted certificates.

Benefits include:

- Encrypted communication.
- Mutual authentication.
- Protection against unauthorized clients.
- Additional security between internal services.

The framework supports:

- HTTP.
- HTTPS.
- mTLS.

It can automatically select the appropriate transport based on configuration or explicitly force a specific protocol.

---

## JWT Validation

The framework includes built-in JWT validation using a JWKS endpoint.

Configuration:

```yaml
delivera:
  jwt:
    jwks-uri: http://localhost:9090/api/v2/.well-known/jwks.json
```

The validation layer automatically verifies:

- Signature.
- Expiration.
- Token structure.
- Public key availability.

Possible validation errors:

- TOKEN_INVALID_SIGNATURE
- TOKEN_EXPIRED
- TOKEN_MALFORMED
- TOKEN_INVALID

In addition, helper utilities are provided to simplify access to claims and authenticated user information.

---

## Retry and Resilience Features

The HTTP client includes built-in resilience features.

Supported capabilities:

### Automatic retries

```java
.retry(3)
.retryDelay(500)
```

Retries are automatically performed for:

- Network failures.
- HTTP 502.
- HTTP 503.
- HTTP 504.

### Request timeout

```java
.timeout(3000)
```

### Request logging

```java
.log()
```

The framework can generate detailed logs containing:

- Request protocol.
- HTTP method.
- Resolved URL.
- Request execution status.

---

## Route-Level Authorization

The framework supports access control for internal endpoints.

Every service can define which routes can be accessed by other services.

Example:

```yaml
delivera:
  security:
    services:
      auth-service:
        allowed-paths:
          - /internal/auth/register
```

This allows services to expose only the endpoints required for inter-service communication.

---

## Distributed Compensable Transactions

Traditional database transactions cannot rollback operations executed in:

- Other microservices.
- External APIs.
- Third-party systems.

To address this limitation, Delivera Framework provides a lightweight compensation mechanism inspired by the Saga pattern.

Example:

```java
@Compensable
public void createCompany() {

    String companyId = externalClient.createCompany();

    Compensations.registerRollback(
        () -> externalClient.deleteCompany(companyId)
    );

    createUsers();
}
```

If an exception occurs, rollback actions will be executed automatically.

### Rollback execution model

Compensations are executed in reverse order of registration:

```text
Create Company
Create Users
Create Resources

ERROR

Delete Resources
Delete Users
Delete Company
```

### Current limitations

Compensable transactions are implemented using ThreadLocal context storage.

Therefore they are only supported in synchronous execution flows.

Supported:

- Standard Spring services.
- Synchronous execution.

Not supported:

- @Async
- CompletableFuture
- ExecutorService
- Reactor Mono/Flux
- Reactive pipelines

unless custom context propagation is implemented.

---

## SPACE Integration

The framework includes native support for SPACE consumption management.

This integration allows microservices to:

- Validate resource limits.
- Check subscription capabilities.
- Register resource consumption.
- Rollback consumption operations when necessary.

### Feature abstraction

A new feature can be implemented by extending:

```java
AbstractSpaceFeature
```

Example:

```java
@Service
public class UsersFeature extends AbstractSpaceFeature {

    @Override
    protected String feature() {
        return "users";
    }
}
```

### Available operations

```java
usersFeature.add(userId);

usersFeature.delete(userId);

usersFeature.addAll(userId, 10);

usersFeature.deleteAll(userId, 10);
```

### Integration with compensable transactions

SPACE operations can automatically participate in compensable workflows.

```java
@Compensable
public void createUser() {

    usersFeature.addWithRollBack(userId);

    repository.save(user);
}
```

If the operation fails, the SPACE consumption will be automatically reverted.


# Complete Internal Request Flow Example

The following example shows a complete internal communication between two microservices using Delivera Framework.

```java
public OrderResponse executeB2CRequest(DataOrderRequest requestBody) {
    return client.request()
        .service("data-service")
        .path("/internal/orders/B2C")
        .method(HttpMethod.POST)
        .header(
            "Authorization",
            "Bearer " + securityUtils.getCurrentJwt()
        )
        .mtls()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(OrderResponse.class)
        .block();
}
```

## Execution Flow

When this request is executed, Delivera Framework performs the following steps:

### 1. Service resolution

The framework resolves the logical service name:

```java
.service("data-service")
```

using the configured host:

```yaml
delivera:
  client:
    service-hosts:
      data-service: https://localhost:9093

    service-base-paths:
      data-service: /api/v2
```

Resulting URL:

```text
https://localhost:9093/api/v2/internal/orders/B2C
```

---

### 2. mTLS client selection

Because the request explicitly uses:

```java
.mtls()
```

the framework selects the mTLS-enabled WebClient.

If the resolved URL is not HTTPS, an exception is thrown.

---

### 3. Internal authentication headers

Because the request is marked as:

```java
.internal()
```

the framework automatically adds:

```http
X-Service-Name: delivera-service
X-Internal-Key: delivera-internal-key
```

using the configured service information.

---

### 4. JWT propagation

The Authorization header is copied from the current authenticated user:

```http
Authorization: Bearer eyJ...
```

allowing downstream services to identify the original user.

---

### 5. Request execution

The configured HTTP method, body and headers are sent to the destination service.

---

### 6. Error handling

If a network error occurs:

```text
Connection refused
Host unavailable
DNS resolution failure
```

the framework maps the error to a NetworkException.

---

### 7. Automatic retries

If the request fails with:

- NetworkException
- HTTP 502
- HTTP 503
- HTTP 504

the framework automatically retries according to the configured policy:

```java
.retry(3)
```

---

### 8. Timeout control

If no response is received before:

```java
.timeout(5000)
```

the operation fails with:

```java
ServerException(503)
```

---

### 9. Logging

When enabled:

```java
.log()
```

request execution information is automatically written to application logs.

```
[mTLS] POST https://localhost:9093/api/v2/internal/orders/B2C
[mTLS] Response received
```


# Internal Authorization Rules

The authorization filter supports two operating modes controlled by the `default-deny` property.

---

## default-deny = false

```yaml
delivera:
  security:
    default-deny: false
```

This mode provides backward compatibility and a more permissive default behaviour.

### No allowed paths configured

```yaml
data-service:
  allowed-paths:
```

or

```yaml
data-service:
  allowed-paths: []
```

Result:

```text
All internal endpoints are accessible.
```

### Allowed paths configured

```yaml
data-service:
  allowed-paths:
    - /internal/orders/create
```

Result:

```text
/internal/orders/create      -> ALLOWED
/internal/orders/delete      -> DENIED
/internal/users/list         -> DENIED
```

Once at least one route is configured, the filter automatically switches to whitelist behaviour and only the configured routes are accessible.

This provides an additional security layer because newly created internal endpoints are blocked by default unless explicitly added to the service configuration.

---

## default-deny = true

```yaml
delivera:
  security:
    default-deny: true
```

This mode always follows a strict whitelist strategy.

### No allowed paths configured

```yaml
data-service:
  allowed-paths:
```

Result:

```text
All internal endpoints are denied.
```

### Allowed paths configured

```yaml
data-service:
  allowed-paths:
    - /internal/orders/create
```

Result:

```text
/internal/orders/create      -> ALLOWED
/internal/orders/delete      -> DENIED
/internal/users/list         -> DENIED
```

---

## Recommended usage

For production environments, it is recommended to define explicit route permissions using `allowed-paths`.

Even when `default-deny` is disabled, defining allowed paths provides additional protection by ensuring that newly added internal endpoints remain inaccessible to other services unless they are explicitly authorized.

---

# Design Philosophy

Delivera Framework follows a configuration-first approach.

Most infrastructure behaviour can be configured through application properties without requiring changes to application code.

The framework aims to provide secure defaults while remaining flexible enough to adapt to different deployment environments, communication protocols and business requirements.

The primary goal is to allow developers to focus on domain logic while reusing a common infrastructure layer across all microservices in the ecosystem.

# ES

---

# Delivera Framework

## Framework de Infraestructura para Microservicios Spring Boot

> **Nombre del repositorio:** `microservice-client-starter`
>
> El repositorio mantiene el nombre histórico `microservice-client-starter`, aunque su alcance ha evolucionado más allá de un starter tradicional de Spring Boot. Actualmente proporciona un framework de infraestructura completo para arquitecturas basadas en microservicios, incluyendo comunicación, seguridad, operaciones distribuidas e integración con plataformas externas.

---

# Introducción

## ¿Qué es Delivera Framework?

Delivera Framework es un framework de infraestructura diseñado para simplificar el desarrollo de microservicios Spring Boot proporcionando un conjunto común de componentes, abstracciones y patrones arquitectónicos utilizados frecuentemente en sistemas distribuidos.

Al construir un ecosistema de microservicios, los equipos de desarrollo suelen necesitar implementar repetidamente los mismos aspectos técnicos:

- Comunicación entre servicios.
- Autenticación interna.
- Seguridad en el transporte.
- Validación de JWT.
- Gestión de errores.
- Estrategias de reintento.
- Descubrimiento de servicios.
- Consistencia distribuida.
- Integración con plataformas externas.

Aunque estas preocupaciones no forman parte de la lógica de negocio, representan una cantidad significativa de código repetitivo que debe desarrollarse, probarse y mantenerse en múltiples servicios.

Delivera Framework centraliza estas responsabilidades en una capa de infraestructura reutilizable, permitiendo a los desarrolladores centrarse principalmente en la lógica de negocio mientras reutilizan una implementación común y consistente en toda la plataforma.

---

# Objetivos

El framework ha sido diseñado para resolver los problemas más comunes encontrados en arquitecturas basadas en microservicios.

## Reducir código de infraestructura duplicado

En lugar de implementar clientes HTTP, filtros de seguridad, políticas de reintento y utilidades comunes en cada servicio, estas capacidades se proporcionan desde un framework compartido.

## Estandarizar la comunicación entre servicios

Todos los servicios internos utilizan el mismo modelo de comunicación, las mismas reglas de seguridad, cabeceras de autenticación y estructura de configuración.

## Mejorar la seguridad interna

El framework proporciona múltiples capas de seguridad incluyendo:

- Mutual TLS (mTLS).
- API Keys internas.
- Validación de JWT.
- Autorización basada en rutas.

## Simplificar el descubrimiento de servicios

Los servicios pueden comunicarse utilizando nombres lógicos en lugar de URLs codificadas directamente en el código.

## Facilitar operaciones distribuidas

El framework proporciona un modelo de transacciones compensables que ayuda a mantener la consistencia cuando se interactúa con múltiples servicios o sistemas externos.

## Integrar validación y consumo de suscripciones

El framework incluye una capa de integración con SPACE que permite validar límites de funcionalidades y registrar consumos de recursos.

---

# Funcionalidades Principales

El framework está dividido en varios módulos funcionales.

## Cliente HTTP Reactivo

Delivera Framework proporciona una abstracción unificada sobre Spring WebClient.

En lugar de construir manualmente las peticiones HTTP, los desarrolladores pueden utilizar un builder fluido para crear llamadas de forma consistente.

Ejemplo:

```java
client.request()
    .service("auth-service")
    .path("/internal/auth/check")
    .method(HttpMethod.GET);
```

El framework gestiona automáticamente:

- Resolución de URLs.
- Resolución de rutas base.
- Inyección de cabeceras.
- Selección de protocolo.
- Gestión de errores.
- Estrategias de reintento.
- Logging.

---

## Resolución Lógica de Servicios

Los servicios pueden referenciarse mediante nombres lógicos en lugar de URLs físicas.

Configuración:

```yaml
delivera:
  client:
    service-hosts:
      auth-service: https://localhost:9091

    service-base-paths:
      auth-service: /api/v2
```

Uso:

```java
client.request()
    .service("auth-service")
    .path("/internal/user");
```

URL resultante:

```text
https://localhost:9091/api/v2/internal/user
```

Este enfoque elimina detalles de infraestructura del código de aplicación y centraliza la ubicación de los servicios en la configuración.

---

## Autenticación Interna

El framework proporciona identificación y autenticación automática entre servicios internos.

Cuando una petición se marca como interna:

```java
.internal()
```

el framework añade automáticamente:

```http
X-Service-Name
X-Internal-Key
```

Estas cabeceras permiten al servicio receptor:

- Identificar quién realiza la llamada.
- Validar su API Key interna.
- Aplicar reglas de autorización.

Este mecanismo está diseñado para funcionar junto con mTLS, proporcionando autenticación de servicios y transporte cifrado.

---

## Mutual TLS (mTLS)

El framework soporta conexiones Mutual TLS entre servicios.

A diferencia de HTTPS tradicional, donde únicamente el servidor presenta un certificado, mTLS requiere que ambas partes se autentiquen mutuamente utilizando certificados de confianza.

Beneficios:

- Comunicación cifrada.
- Autenticación mutua.
- Protección frente a clientes no autorizados.
- Seguridad adicional entre servicios internos.

El framework soporta:

- HTTP.
- HTTPS.
- mTLS.

Además, puede seleccionar automáticamente el protocolo adecuado o forzar uno específico según la configuración.

---

## Validación JWT

El framework incorpora validación de JWT mediante un endpoint JWKS.

Configuración:

```yaml
delivera:
  jwt:
    jwks-uri: http://localhost:9090/api/v2/.well-known/jwks.json
```

La capa de validación verifica automáticamente:

- Firma.
- Expiración.
- Estructura del token.
- Disponibilidad de la clave pública.

Posibles errores:

- TOKEN_INVALID_SIGNATURE
- TOKEN_EXPIRED
- TOKEN_MALFORMED
- TOKEN_INVALID

Además, se proporcionan utilidades para facilitar el acceso a los claims y a la información del usuario autenticado.

---

## Características de Resiliencia

El cliente HTTP incorpora mecanismos integrados de tolerancia a fallos.

### Reintentos automáticos

```java
.retry(3)
.retryDelay(500)
```

Los reintentos se realizan automáticamente ante:

- Errores de red.
- HTTP 502.
- HTTP 503.
- HTTP 504.

### Timeout de peticiones

```java
.timeout(3000)
```

### Logging de peticiones

```java
.log()
```

El framework puede generar trazas detalladas incluyendo:

- Protocolo utilizado.
- Método HTTP.
- URL resuelta.
- Estado de ejecución de la petición.

---

## Autorización Basada en Rutas

El framework permite controlar qué endpoints internos pueden ser accesibles desde otros servicios.

Ejemplo:

```yaml
delivera:
  security:
    services:
      auth-service:
        allowed-paths:
          - /internal/auth/register
```

Esto permite exponer únicamente los endpoints necesarios para la comunicación entre servicios.

---

## Transacciones Compensables Distribuidas

Las transacciones tradicionales de base de datos no pueden revertir operaciones realizadas sobre:

- Otros microservicios.
- APIs externas.
- Sistemas de terceros.

Para resolver este problema, Delivera Framework proporciona un mecanismo ligero de compensación inspirado en el patrón Saga.

Ejemplo:

```java
@Compensable
public void createCompany() {

    String companyId = externalClient.createCompany();

    Compensations.registerRollback(
        () -> externalClient.deleteCompany(companyId)
    );

    createUsers();
}
```

Si se produce una excepción, las acciones de rollback se ejecutarán automáticamente.

### Modelo de ejecución del rollback

Las compensaciones se ejecutan en orden inverso al registro:

```text
Crear Empresa
Crear Usuarios
Crear Recursos

ERROR

Eliminar Recursos
Eliminar Usuarios
Eliminar Empresa
```

### Limitaciones actuales

Las transacciones compensables utilizan un contexto basado en ThreadLocal.

Por tanto únicamente son compatibles con flujos de ejecución síncronos.

Compatibles:

- Servicios Spring convencionales.
- Ejecución síncrona.

No compatibles:

- @Async
- CompletableFuture
- ExecutorService
- Reactor Mono/Flux
- Pipelines reactivos

salvo que se implemente propagación de contexto personalizada.

---

## Integración con SPACE

El framework incluye soporte nativo para la gestión de consumos mediante SPACE.

Esta integración permite:

- Validar límites de recursos.
- Comprobar capacidades asociadas a una suscripción.
- Registrar consumos.
- Revertir consumos cuando sea necesario.

### Abstracción de Features

Una nueva funcionalidad puede implementarse extendiendo:

```java
AbstractSpaceFeature
```

Ejemplo:

```java
@Service
public class UsersFeature extends AbstractSpaceFeature {

    @Override
    protected String feature() {
        return "users";
    }
}
```

### Operaciones disponibles

```java
usersFeature.add(userId);

usersFeature.delete(userId);

usersFeature.addAll(userId, 10);

usersFeature.deleteAll(userId, 10);
```

### Integración con transacciones compensables

Las operaciones de SPACE pueden participar automáticamente en flujos compensables.

```java
@Compensable
public void createUser() {

    usersFeature.addWithRollBack(userId);

    repository.save(user);
}
```

Si la operación falla, el consumo registrado en SPACE será revertido automáticamente.

# Ejemplo Completo de Flujo de Petición Interna

El siguiente ejemplo muestra una comunicación interna completa entre dos microservicios utilizando Delivera Framework.

```java
public OrderResponse executeB2CRequest(DataOrderRequest requestBody) {
    return client.request()
        .service("data-service")
        .path("/internal/orders/B2C")
        .method(HttpMethod.POST)
        .header(
            "Authorization",
            "Bearer " + securityUtils.getCurrentJwt()
        )
        .mtls()
        .body(requestBody)
        .internal()
        .failOn4xx(true)
        .failOn5xx(true)
        .retry(3)
        .timeout(5000)
        .log()
        .executeBasicRequest(OrderResponse.class)
        .block();
}
```

## Flujo de ejecución

Cuando esta petición es ejecutada, Delivera Framework realiza automáticamente los siguientes pasos:

### 1. Resolución del servicio

El framework resuelve el nombre lógico del servicio:

```java
.service("data-service")
```

utilizando la configuración definida:

```yaml
delivera:
  client:
    service-hosts:
      data-service: https://localhost:9093

    service-base-paths:
      data-service: /api/v2
```

URL resultante:

```text
https://localhost:9093/api/v2/internal/orders/B2C
```

---

### 2. Selección del cliente mTLS

Debido a que la petición utiliza explícitamente:

```java
.mtls()
```

el framework selecciona automáticamente el cliente configurado para comunicaciones mTLS.

Si la URL resuelta no utiliza HTTPS, se lanzará una excepción para evitar configuraciones inconsistentes.

---

### 3. Cabeceras de autenticación interna

Debido a que la petición está marcada como:

```java
.internal()
```

el framework añade automáticamente las cabeceras configuradas:

```http
X-Service-Name: delivera-service
X-Internal-Key: delivera-internal-key
```

utilizando la información del servicio definida en la configuración.

---

### 4. Propagación del JWT

La cabecera Authorization se propaga desde el usuario actualmente autenticado:

```http
Authorization: Bearer eyJ...
```

permitiendo que los servicios posteriores puedan identificar al usuario original que inició la petición.

---

### 5. Ejecución de la petición

El framework construye la petición final utilizando:

- El método HTTP configurado.
- El cuerpo de la petición.
- Las cabeceras personalizadas.
- Las cabeceras internas automáticas.
- El protocolo seleccionado.

Finalmente, la petición es enviada al servicio de destino.

---

### 6. Gestión de errores

Si ocurre un error de red, por ejemplo:

```text
Connection refused
Host unavailable
DNS resolution failure
```

el framework transforma automáticamente el error en una excepción de tipo:

```java
NetworkException
```

facilitando un tratamiento uniforme de errores de conectividad.

---

### 7. Reintentos automáticos

Si la petición falla debido a:

- NetworkException
- HTTP 502
- HTTP 503
- HTTP 504

el framework aplicará automáticamente la política de reintentos configurada:

```java
.retry(3)
```

utilizando una estrategia de backoff progresivo.

---

### 8. Control de timeout

Si el servicio no responde antes del tiempo configurado:

```java
.timeout(5000)
```

la operación finalizará con:

```java
ServerException(503)
```

evitando que la llamada permanezca bloqueada indefinidamente.

---

### 9. Logging

Cuando está habilitado:

```java
.log()
```

el framework genera automáticamente información de ejecución en los logs de la aplicación.

Ejemplo:

```text
[mTLS] POST https://localhost:9093/api/v2/internal/orders/B2C
[mTLS] Response received
```

---

# Reglas de Autorización Interna

El filtro de autorización interna soporta dos modos de funcionamiento controlados mediante la propiedad:

```yaml
delivera.security.default-deny
```

---

## default-deny = false

```yaml
delivera:
  security:
    default-deny: false
```

Este modo proporciona compatibilidad con servicios existentes y un comportamiento más permisivo por defecto.

### Sin rutas configuradas

```yaml
data-service:
  allowed-paths:
```

o

```yaml
data-service:
  allowed-paths: []
```

Resultado:

```text
Todos los endpoints internos son accesibles.
```

---

### Con rutas configuradas

```yaml
data-service:
  allowed-paths:
    - /internal/orders/create
```

Resultado:

```text
/internal/orders/create      -> PERMITIDO
/internal/orders/delete      -> DENEGADO
/internal/users/list         -> DENEGADO
```

Una vez que existe al menos una ruta configurada, el filtro adopta automáticamente un comportamiento de lista blanca (whitelist) y únicamente permite acceder a las rutas definidas.

Este comportamiento proporciona una capa adicional de seguridad ya que cualquier nuevo endpoint interno quedará bloqueado automáticamente hasta que sea añadido explícitamente a la configuración.

---

## default-deny = true

```yaml
delivera:
  security:
    default-deny: true
```

Este modo aplica siempre una estrategia estricta de lista blanca.

### Sin rutas configuradas

```yaml
data-service:
  allowed-paths:
```

Resultado:

```text
Todos los endpoints internos quedan bloqueados.
```

---

### Con rutas configuradas

```yaml
data-service:
  allowed-paths:
    - /internal/orders/create
```

Resultado:

```text
/internal/orders/create      -> PERMITIDO
/internal/orders/delete      -> DENEGADO
/internal/users/list         -> DENEGADO
```

---

## Uso recomendado

Para entornos de producción se recomienda definir explícitamente los permisos mediante:

```yaml
allowed-paths
```

Incluso cuando `default-deny` está desactivado, definir rutas permitidas aporta una capa adicional de protección, garantizando que los nuevos endpoints internos permanezcan inaccesibles para otros servicios hasta que sean autorizados explícitamente.

Este enfoque sigue el principio de mínimo privilegio y ayuda a evitar la exposición accidental de nuevas operaciones internas.

---

# Filosofía de Diseño

Delivera Framework sigue una filosofía **configuration-first**.

La mayor parte del comportamiento de infraestructura puede configurarse mediante propiedades sin necesidad de modificar el código de aplicación.

El framework busca proporcionar configuraciones seguras por defecto manteniendo al mismo tiempo la flexibilidad suficiente para adaptarse a distintos entornos de despliegue, protocolos de comunicación y necesidades de negocio.

El objetivo principal es permitir a los desarrolladores centrarse en la lógica de dominio mientras reutilizan una capa de infraestructura común, consistente y mantenible en todo el ecosistema de microservicios.