#!/usr/bin/env bash


readonly FUTURE_MONTHS=3
readonly KEY_SIZE=2048
FORCE=false
FORCE_BACKUP=false

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
}


verify_keypair() {

    local kid="$1"

    [[ -f "keys/${kid}-private.pem" ]] || {
        error "Private key not found for $kid"
        exit 1
    }

    [[ -f "keys/${kid}-public.pem" ]] || {
        error "Public key not found for $kid"
        exit 1
    }

    local private_modulus
    local public_modulus

    private_modulus=$(
        openssl rsa \
            -noout \
            -modulus \
            -in "keys/${kid}-private.pem"
    )

    public_modulus=$(
        openssl rsa \
            -pubin \
            -noout \
            -modulus \
            -in "keys/${kid}-public.pem"
    )

    [[ "$private_modulus" = "$public_modulus" ]] || {
        error "Key pair verification failed for $kid"
        exit 1
    }

    info "$kid verified"
}

generate_keypair() {

    local kid="$1"

    if [[ \
        -f "keys/${kid}-private.pem" \
        && -f "keys/${kid}-public.pem" \
        && "$FORCE" = false \
    ]]
    then

        warn "$kid already exists. Skipping."

        return

    fi

    if [[ "$FORCE" = true ]]; then

        rm -f "keys/${kid}-private.pem"
        rm -f "keys/${kid}-public.pem"

    fi

    info "Generating $kid"

    openssl genrsa \
        -out "keys/${kid}-private.pem" \
        "$KEY_SIZE"

    openssl rsa \
        -in "keys/${kid}-private.pem" \
        -pubout \
        -out "keys/${kid}-public.pem"
}

generate_backup() {

    local kid

    kid="key-backup"

    if [[ \
        -f "keys/${kid}-private.pem" \
        && -f "keys/${kid}-public.pem" \
        && "$FORCE_BACKUP" = false \
    ]]
    then

        warn "Backup key already exists. Skipping."

        return

    fi

    if [[ "$FORCE_BACKUP" = true ]]; then

        info "Regenerating backup key"

        rm -f "keys/${kid}-private.pem"
        rm -f "keys/${kid}-public.pem"

    fi

    info "Generating backup key $kid"

    openssl genrsa \
        -out "keys/${kid}-private.pem" \
        "$KEY_SIZE"

    openssl rsa \
        -in "keys/${kid}-private.pem" \
        -pubout \
        -out "keys/${kid}-public.pem"

    verify_keypair "$kid"
}


month_key() {

    date -d "$1 month" '+key-%Y-%m'
}



head() {
    echo "Executing RSA generations"
}


main() {

    head

    check_dependencies

    mkdir -p keys

    generate_backup

    for ((i=-1; i<=FUTURE_MONTHS; i++)); do

        local key

        key=$(month_key "$i")

        generate_keypair "$key"

        verify_keypair "$key"

    done

}


while [[ $# -gt 0 ]]; do
    case $1 in

        --force)
            FORCE=true
            shift
            ;;

        --force-backup)
            FORCE_BACKUP=true
            shift
            ;;

        *)
            echo "[ERROR] Unknown option: $1"
            exit 1
            ;;

    esac
done

main
