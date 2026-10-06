#!/usr/bin/env bash
# ------------------------------------------------------------------------------
# Env configuration
# ------------------------------------------------------------------------------

load_env() {

    local env_file="$1"

    [[ -f "$env_file" ]] || {
        error "Environment file not found: $env_file"
        exit 1
    }

    set -a
    source "$env_file"
    set +a
}

ENV_FILE="${ENV_FILE:-./env/.env.certs}"

load_env $ENV_FILE




# ------------------------------------------------------------------------------
# Certificate Authority configuration
# ------------------------------------------------------------------------------

CA_NAME="${CA_NAME:-MyInternalCA}"
CA_VALIDITY_DAYS="${CA_VALIDITY_DAYS:-3650}"

# ------------------------------------------------------------------------------
# Service certificate configuration
# ------------------------------------------------------------------------------

CERT_VALIDITY_DAYS="${CERT_VALIDITY_DAYS:-365}"
KEY_SIZE="${KEY_SIZE:-2048}"

AUTH_PASSWORD="${AUTH_PASSWORD:-delivera_auth_password}"
DATA_PASSWORD="${DATA_PASSWORD:-delivera_data_password}"
DELIVERA_PASSWORD="${DELIVERA_PASSWORD:-delivera_password}"
NGINX_PASSWORD="${NGINX_PASSWORD:-delivera_nginx_password}"

DEFAULT_SAN_SUFFIXES="${DEFAULT_SAN_SUFFIXES:-DNS:localhost,DNS:host.docker.internal}"

FORCE=false
REGENERATE_CA=false

readonly CA_NAME
readonly CA_VALIDITY_DAYS
readonly CERT_VALIDITY_DAYS
readonly KEY_SIZE

set -euo pipefail

info() {
    echo "[INFO] $1"
}

warn() {
    echo "[WARN] $1"
}

error() {
    echo "[ERROR] $1"
}

check_dependencies() {

    info "Checking dependencies..."

    command -v openssl >/dev/null 2>&1 || {
        error "OpenSSL is not installed."
        exit 1
    }

    command -v keytool >/dev/null 2>&1 || {
        error "Keytool is not installed."
        exit 1
    }
}

generate_ca() {

    mkdir -p ca

    if [[ -f "ca/ca.pem" && -f "ca/ca.key" && "$REGENERATE_CA" = false ]]; then

        info "Existing CA found. Reusing certificates."

        return
    fi

    info "Generating Certificate Authority..."

    rm -f ca/*

    openssl genrsa \
        -out ca/ca.key \
        "$KEY_SIZE"

    openssl req \
        -x509 \
        -new \
        -nodes \
        -key ca/ca.key \
        -sha256 \
        -days "$CA_VALIDITY_DAYS" \
        -out ca/ca.pem \
        -subj "//CN=${CA_NAME}"

    info "Certificate Authority created."
}

verify_ca() {

    info "Validating CA..."

    [[ -f "ca/ca.pem" ]] || {
        error "CA certificate not found."
        exit 1
    }

    [[ -f "ca/ca.key" ]] || {
        error "CA private key not found."
        exit 1
    }

    openssl x509 \
        -in ca/ca.pem \
        -noout >/dev/null
}

create_extensions_file() {

    local service="$1"

    local san
    san=$(build_san "$service")

    cat > "$service/ext.cnf" <<EOF
[ v3_req ]
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = ${san}
EOF
}

build_san() {

    local service="$1"

    echo "DNS:${service},${DEFAULT_SAN_SUFFIXES}"
}

generate_private_key() {

    local service="$1"

    info "Generating private key..."

    openssl genrsa \
        -out "$service/$service.key" \
        "$KEY_SIZE"
}

generate_csr() {

    local service="$1"

    info "Generating certificate signing request..."

   local san
    san=$(build_san "$service")

    openssl req -new \
        -key "$service/$service.key" \
        -out "$service/$service.csr" \
        -subj "//CN=${service}" \
        -addext "subjectAltName=${san}"
}

generate_certificate() {

    local service="$1"

    info "Generating certificate..."

    openssl x509 -req \
        -in "$service/$service.csr" \
        -CA ca/ca.pem \
        -CAkey ca/ca.key \
        -CAcreateserial \
        -out "$service/$service.crt" \
        -days "$CERT_VALIDITY_DAYS" \
        -sha256 \
        -extfile "$service/ext.cnf" \
        -extensions v3_req
}

verify_certificate() {

    local service="$1"

    info "Verifying certificate..."

    openssl verify \
        -CAfile ca/ca.pem \
        "$service/$service.crt"
}

generate_keystore() {

    local service="$1"
    local alias="$2"
    local password="$3"

    info "Generating keystore..."

    openssl pkcs12 -export \
        -in "$service/$service.crt" \
        -inkey "$service/$service.key" \
        -out "$service/$service-keystore.p12" \
        -name "$alias" \
        -password pass:"$password"
}

verify_keystore() {

    local service="$1"
    local password="$2"

    info "Verifying keystore..."

    keytool -list \
        -keystore "$service/$service-keystore.p12" \
        -storetype PKCS12 \
        -storepass "$password" \
        >/dev/null
}

generate_truststore() {

    local service="$1"
    local password="$2"

    info "Generating truststore..."

    keytool -import \
        -alias ca \
        -file ca/ca.pem \
        -keystore "$service/$service-truststore.p12" \
        -storetype PKCS12 \
        -storepass "$password" \
        -noprompt
}

verify_truststore() {

    local service="$1"
    local password="$2"

    info "Verifying truststore..."

    keytool -list \
        -keystore "$service/$service-truststore.p12" \
        -storetype PKCS12 \
        -storepass "$password" \
        >/dev/null
}

validate_service_name() {

    local service="$1"

    [[ "$service" =~ ^[a-zA-Z0-9._-]+$ ]] || {
        error "Invalid service name: $service"
        exit 1
    }
}


generate_service() {

    local service="$1"
    local alias="$2"
    local password="$3"

    info "Processing service: $service"

    mkdir -p "$service"

    if [[ \
        -f "$service/$service.crt" \
        && -f "$service/$service.key" \
        && -f "$service/$service-keystore.p12" \
        && -f "$service/$service-truststore.p12" \
        && "$FORCE" = false \
    ]]
    then

        warn "$service already exists. Skipping."

        return

    fi

    if [[ "$FORCE" = true ]]; then

        info "Removing existing files..."

        find "$service" -type f -delete
    fi

    validate_service_name "$service"

    create_extensions_file "$service"

    generate_private_key "$service"

    generate_csr "$service"

    generate_certificate "$service"

    verify_certificate "$service"

    generate_keystore \
        "$service" \
        "$alias" \
        "$password"

    verify_keystore \
        "$service" \
        "$password"

    generate_truststore \
        "$service" \
        "$password"

    verify_truststore \
        "$service" \
        "$password"
    }

head() {
    echo "Executing PKI generations"
}

modes() {
    if [[ "$FORCE" = true ]]; then

        info "Force mode"

    elif [[ "$REGENERATE_CA" = true ]]; then

        info "Regenerate CA mode"

    else

        info "Normal mode"

    fi
}

main() {

    head

    mkdir -p certs

    cd certs

    if [[ "$REGENERATE_CA" = true ]]; then
        FORCE=true
    fi
    modes

    generate_ca
    
    verify_ca

    readonly SERVICES=(
        "auth-service:auth:$AUTH_PASSWORD"
        "data-service:data:$DATA_PASSWORD"
        "delivera-service:delivera:$DELIVERA_PASSWORD"
        "nginx-service:nginx:$NGINX_PASSWORD"
    )

    for entry in "${SERVICES[@]}"; do

        IFS=':' read -r service alias password <<< "$entry"

        generate_service \
            "$service" \
            "$alias" \
            "$password"

    done

    info "Certificate generation completed successfully."

}

while [[ $# -gt 0 ]]; do
    case $1 in

        --force)
            FORCE=true
            shift
            ;;

        --regenerate-ca)
            REGENERATE_CA=true
            shift
            ;;

        *)
            echo "[ERROR] Unknown option: $1"
            exit 1
            ;;

    esac
done



main

