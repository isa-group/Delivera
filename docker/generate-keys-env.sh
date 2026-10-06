#!/usr/bin/env bash


readonly FUTURE_MONTHS=3

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


month_key() {

    date -d "$1 month" '+key-%Y-%m'
}



head() {
    echo "Executing env.jwt generation"
}

write_slot() {

    local slot="$1"
    local offset="$2"

    local kid
    kid=$(month_key "$offset")

    cat >> .env.jwt <<EOF
JWT_SLOT${slot}_ID=${kid}
JWT_SLOT${slot}_PRIVATE=file:/app/keys/${kid}-private.pem
JWT_SLOT${slot}_PUBLIC=file:/app/keys/${kid}-public.pem

EOF
}

main() {

    head

    cd env

    cat > .env.jwt <<EOF
# Loaded before env.mode files.
# Any variable defined in env.dev, env.prod or env.local
# will override the values declared here.

JWT_BACKUP_ID=key-backup
JWT_BACKUP_PRIVATE=file:/app/keys/key-backup-private.pem
JWT_BACKUP_PUBLIC=file:/app/keys/key-backup-public.pem

EOF
    slot=1

    for ((i=-1, slot=1; i<=FUTURE_MONTHS; i++, slot++)); do

        write_slot "$slot" "$i"

    done

}


main
