# CREACIÓN DE CERTIFICADOS


## ÍNDICE

* [1. INTRODUCCIÓN](#1-introducción)
* [2. CREACIÓN DEL CA](#2-creación-del-ca)
* [3. Creación del certificado del AUTH-SERVICE](#3-creación-del-certificado-del-auth-service)
* [4. Creación del certificado del DELIVERA-SERVICE](#4-creación-del-certificado-del-delivera-service)
* [5. Creación del certificado del DATA-SERVICE](#5-creación-del-certificado-del-data-service)
* [6. Añadir los certificados al proyecto](#6-añadir-los-certificados-al-proyecto)

## 1. INTRODUCCIÓN

En este documento se expone una serie de instrucciones para la creación de certificados y uso en el proyecto, esto con el fin de poder realizar llamadas internas usando TLS en el caso del proyecto se usa mTLS es decir ambos sistemas deben confiar en el certificado del otro para que se produzca la comunicación.

Antes de empezar con la creación se recomienda ,para poder copiar y pegar directamente el código, crear una carpeta llamada **certs** está carpeta contendrá las siguientes carpetas:

* ca
* auth-service
* monolith-service
* data-service

Una vez creadas las carpetas nos situamos desde la terminal en la carpeta de certs. (**bash**)

## 2. CREACIÓN DEL CA
Para la creación del CA usamos el siguiente comando:

**IMPORTANTE: Hay que tener en cuenta que este código está pensado para desarrollo si se quiere usar para producción es recomendable añadir una contraseña que se preguntara cada vez que se vaya a firmar cualquier certificados para mayor seguridad en caso de robo del CA:** 

```bash
openssl genrsa -out ca/ca.key 2048

openssl req -x509 -new -nodes \
  -key ca/ca.key \
  -sha256 -days 3650 \
  -out ca/ca.pem \
  -subj "//CN=MyInternalCA"
```

Se puede cambiar el CN al nombre deseado.

## 3. Creación del certificado del AUTH-SERVICE
**IMPORTANTE: En está sección aparece la contraseña delivera_auth_password es necesario cambiarla para mayor seguridad.**


```bash
echo "
[ v3_req ]
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth 
subjectAltName = DNS:auth-service,DNS:localhost,DNS:host.docker.internal
" > auth-service/auth-ext.cnf

openssl genrsa -out auth-service/auth.key 2048

openssl req -new \
  -key  auth-service/auth.key \
  -out  auth-service/auth.csr \
  -subj "//CN=auth-service" \
  -addext "subjectAltName=DNS:auth-service,DNS:localhost,DNS:host.docker.internal" 

openssl x509 -req \
  -in auth-service/auth.csr \
  -CA ca/ca.pem \
  -CAkey ca/ca.key \
  -CAcreateserial \
  -out auth-service/auth.crt \
  -days 365 \
  -sha256 \
  -extfile auth-service/auth-ext.cnf \
  -extensions v3_req

openssl pkcs12 -export \
  -in auth-service/auth.crt \
  -inkey auth-service/auth.key \
  -out auth-service/auth-keystore.p12 \
  -name auth \
  -password pass:delivera_auth_password

keytool -import \
  -alias ca \
  -file ca/ca.pem \
  -keystore auth-service/auth-truststore.p12 \
  -storetype PKCS12 \
  -storepass delivera_auth_password \
  -noprompt
```

## 4. Creación del certificado del DELIVERA-SERVICE
**IMPORTANTE: En está sección aparece la contraseña delivera_password es necesario cambiarla para mayor seguridad.**

```bash
echo "
[ v3_req ]
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth 
subjectAltName = DNS:delivera-service,DNS:localhost,DNS:host.docker.internal
" > monolith-service/delivera-ext.cnf

openssl genrsa -out monolith-service/delivera.key 2048

openssl req -new \
  -key  monolith-service/delivera.key \
  -out  monolith-service/delivera.csr \
  -subj "//CN=delivera-service" \
  -addext "subjectAltName=DNS:delivera-service,DNS:localhost,DNS:host.docker.internal" 

openssl x509 -req \
  -in monolith-service/delivera.csr \
  -CA ca/ca.pem \
  -CAkey ca/ca.key \
  -CAcreateserial \
  -out monolith-service/delivera.crt \
  -days 365 \
  -sha256 \
  -extfile monolith-service/delivera-ext.cnf \
  -extensions v3_req

openssl pkcs12 -export \
  -in monolith-service/delivera.crt \
  -inkey monolith-service/delivera.key \
  -out monolith-service/delivera-keystore.p12 \
  -name delivera\
  -password pass:delivera_password

keytool -import \
  -alias ca \
  -file ca/ca.pem \
  -keystore monolith-service/delivera-truststore.p12 \
  -storetype PKCS12 \
  -storepass delivera_password \
  -noprompt

```

## 5. Creación del certificado del DATA-SERVICE
**IMPORTANTE: En está sección aparece la contraseña delivera_data_password es necesario cambiarla para mayor seguridad.**
```bash
echo "
[ v3_req ]
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth 
subjectAltName = DNS:data-service,DNS:localhost,DNS:host.docker.internal
" > data-service/data-ext.cnf

openssl genrsa -out data-service/data.key 2048

openssl req -new \
  -key  data-service/data.key \
  -out  data-service/data.csr \
  -subj "//CN=data-service" \
  -addext "subjectAltName=DNS:data-service,DNS:localhost,DNS:host.docker.internal" 

openssl x509 -req \
  -in data-service/data.csr \
  -CA ca/ca.pem \
  -CAkey ca/ca.key \
  -CAcreateserial \
  -out data-service/data.crt \
  -days 365 \
  -sha256 \
  -extfile data-service/data-ext.cnf \
  -extensions v3_req

openssl pkcs12 -export \
  -in data-service/data.crt \
  -inkey data-service/data.key \
  -out data-service/data-keystore.p12 \
  -name data\
  -password pass:delivera_data_password

keytool -import \
  -alias ca \
  -file ca/ca.pem \
  -keystore data-service/data-truststore.p12 \
  -storetype PKCS12 \
  -storepass delivera_data_password \
  -noprompt

```

## 6. Añadir los certificados al proyecto
Una vez creados los certificados lo que tendremos que hacer será copiar los respectivos archivos terminados en **-keystore.p12** y **-truststore.p12** a la respectiva carpeta dentro de cada microservicio siguiendo la ruta **/src/main/resources/certs** con el mismo nombre, quizás haya que crear la carpeta certs.