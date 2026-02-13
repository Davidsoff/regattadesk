#!/bin/bash
#
# Generate users_database.yml from template with secure passwords
#
# Usage: ./generate-users-database.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTHELIA_DIR="$SCRIPT_DIR/authelia"
TEMPLATE_FILE="$AUTHELIA_DIR/users_database.yml.example"
OUTPUT_FILE="$AUTHELIA_DIR/users_database.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if template exists
if [ ! -f "$TEMPLATE_FILE" ]; then
    log_error "Template file not found: $TEMPLATE_FILE"
    exit 1
fi

# Check if output file already exists
if [ -f "$OUTPUT_FILE" ]; then
    log_warn "users_database.yml already exists"
    read -p "Do you want to overwrite it? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Aborted. Existing file unchanged."
        exit 0
    fi
fi

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    log_error "Docker is required to generate password hashes"
    log_error "Please install Docker and try again"
    exit 1
fi

log_info "Generating password hashes for users..."
echo

# Users to generate passwords for
USERS=("superadmin" "regattaadmin" "headofjury" "infodesk" "financialmanager" "operator")
declare -A PASSWORDS

for user in "${USERS[@]}"; do
    # Generate a random password
    PASSWORD=$(openssl rand -base64 16 | tr -d "=+/" | cut -c1-16)
    
    log_info "Generating hash for user: $user"
    
    # Generate Argon2id hash using Authelia
    HASH=$(docker run --rm authelia/authelia:latest authelia crypto hash generate argon2 --password "$PASSWORD" 2>/dev/null | grep '^\$argon2id')
    
    if [ -z "$HASH" ]; then
        log_error "Failed to generate hash for user: $user"
        exit 1
    fi
    
    # Store password for display at the end
    PASSWORDS[$user]=$PASSWORD
    
    # Escape special characters in hash for sed
    HASH_ESCAPED=$(echo "$HASH" | sed 's/[\/&]/\\&/g')
    
    # Replace placeholder with actual hash
    if [ ! -f "$OUTPUT_FILE" ]; then
        cp "$TEMPLATE_FILE" "$OUTPUT_FILE"
    fi
    
    # Replace the first occurrence of REPLACE_WITH_YOUR_HASH with the actual hash
    sed -i "0,/REPLACE_WITH_YOUR_HASH/s/REPLACE_WITH_YOUR_HASH/$HASH_ESCAPED/" "$OUTPUT_FILE"
done

log_info "users_database.yml generated successfully!"
echo
log_warn "=============================================="
log_warn "  IMPORTANT: Save these passwords securely!"
log_warn "=============================================="
echo
for user in "${USERS[@]}"; do
    echo "  $user: ${PASSWORDS[$user]}"
done
echo
log_warn "These passwords will not be shown again!"
log_warn "Consider storing them in a password manager."
echo
log_info "File location: $OUTPUT_FILE"
