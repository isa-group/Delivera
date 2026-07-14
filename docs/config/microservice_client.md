# Framework para trabajar con microservicios

## Funcionalidades implementadas
* Trabajar con mTLS, conexión cifrada que requiere dos certificados uno en cliente y otro en servidor, ambos deben de ser de confianza para el otro, con esto se asegura privacidad en la comunicación.

* Añadir un puerto único para mTLS si se desea mantener un puerto con http o https dado que facilita las comunicaciones en este sentido evitando tener que preguntar por el certificado en código si solo se pone https.

* Realizar peticiones de manera Async construyendo un proxy y un servicio sobre WebClient, con este proxy ofrecemos un builder más cómodo que evita repetición de elementos y añade funcionalidades como realizar peticiones directamente con nombre de servicios dichos nombres se intercambian por rutas en el .yml cuidado con la extensión http y https.

* Headers automáticos con `.internal()` en el builder añadiendo el identificador del servicio y su api key propia, este sistema está pensado para mandar la api key propia identificando que el servicio es el que es por eso los servicios que esperan llamadas también deben tener en el .yml la key de este, usando mTLS es muy difícil robar esta key, además para evitar timing attacks no se utiliza el método `.equals()` si no que se compara por bytes.

## Ejemplo de estructuras 

```yml
delivera:
  jwt:
    jwks-uri: http://localhost:9090/api/v2/.well-known/jwks.json

  client: 
    service-name: auth-service
    internal-hosts: 
      - delivera-service
    
    service-hosts:
      delivera-service: https://localhost:8081
    
    
    service-base-paths:
      delivera-service: /api/v2


    internal-auth:
      enabled: true
      filter-enabled: true
      header-name: X-Internal-Key
      service-header-name: X-Service-Name
  
  security:
    default-deny: false
    services:
      auth-service: 
        api-internal-key: auth-internal-key
        allowed-paths:
      delivera-service: 
        api-internal-key: delivera-internal-key
        allowed-paths: 
          - /internal/auth/register


  mtls:
    enabled: true
    key-store: ${AUTH_KEY_STORE:classpath:certs/auth-keystore.p12}
    key-store-password: ${AUTH_KEYSTORE_PASSWORD:delivera_auth_password}
    key-store-type: ${AUTH_KEY_STORE_TYPE:PKCS12}
    trust-store: ${AUTH_TRUST_STORE:classpath:certs/auth-truststore.p12}
    trust-store-password: ${AUTH_TRUSTSTORE_PASSWORD:delivera_auth_password}
    trust-store-type: ${AUTH_TRUST_STORE_TYPE:PKCS12}
    server:
      enabled: true
      port: 9091

      key-store: ${delivera.mtls.key-store}
      key-store-password: ${delivera.mtls.key-store-password}
      key-store-type: ${delivera.mtls.key-store-type}

      trust-store: ${delivera.mtls.trust-store}
      trust-store-password: ${delivera.mtls.trust-store-password}
      trust-store-type: ${delivera.mtls.trust-store-type}

      use-temp-file: false
```

LLa parte de mTLS no tiene mucho misterio, con enabled a false no se usa mTLS si no que se aplica http directamente, con esto hay que tener cuidado dado que si se usa la opción de `.service()` puede que coja el https según cómo definas la ruta del servicio en el yml. 
Indicar que se debe poner el protocolo file: para la ruta en los keystore, truststore y eso en producción, en desarrollo se puede usar classpath y si se hace un .jar usa `use-temp-file: true` para que cree un archivo temporal si te da problemas.


Apartado de Security podemos encontrar un apartado `default-deny` que con valor `false` no permite realizar peticiones al servicio si es que el `allowed-paths` del servicio que realiza la petición está vacío o es null, si se pone a `true` o se llena el `allowed-paths` se restringe el acceso por el filtro a esas rutas.

Con `internal-auth`, el `enabled` indica que se añade las cabeceras posteriores, está pensado para que solo sean esas dos, se tendría que modificar si se quieren más o meter a mano en el constructor con el `.headers()`. Además se puede activar o desactivar el filtro interno con el `filter-enabled`

Por último tenemos los `internal-hosts` que son los nombres de los servicios internos, luego los `service-hosts` donde se hace la equivalencia del nombre del servicio y por ejemplo “ https://localhost:8081” , los `service-base-paths` como en el anterior se pone la equivalencia pero aquí se pone por ejemplo “/api/v2” para que se ponga automáticamente por defecto se pone “” si falta. Y para terminar el service-name que es como se identifica este servicio con los otros.
 
Se ha preparado para la validación de los tokens y extración de la información de estos un filtro y un securityUtils para extraer la información correspondiente usando `jwt` la opción `jwks-uri` le pasamos a Nimbus la url a la que le tiene que preguntar las claves públicas para validar los tokens devolviendo mensajes de error:
 *  `TOKEN_INVALID_SIGNATURE` en el caso de que no haya sido firmado o no se tenga en los jwks .
 * `TOKEN_EXPIRED` cuando el token ya ha pasado su tiempo de vida.
  * `TOKEN_MALFORMED` en el caso de que no esté bien formado el token.
 * `TOKEN_INVALID` en los demás casos.

 ## 
 QUEDA POR HACER EL APARTADO DE CREACIÓN DE CERTIFICADOS, EJEMPLOS DE USO Y FUTURAS MEJORAS A IMPLEMENTAR