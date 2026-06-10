#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Postgres init script — runs automatically on first container start.
# Creates the second database (saham_core_db) and its owner (core_user).
# The primary database (mdes_vault_db / mdes_user) is created by
# the POSTGRES_DB / POSTGRES_USER environment variables automatically.
# ─────────────────────────────────────────────────────────────────────────────
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Create Core Banking user and database
    CREATE USER core_user WITH PASSWORD 'changeme';
    CREATE DATABASE saham_core_db OWNER core_user;
    GRANT ALL PRIVILEGES ON DATABASE saham_core_db TO core_user;
EOSQL
