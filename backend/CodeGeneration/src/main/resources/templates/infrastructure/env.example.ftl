# ── Database ─────────────────────────────────────────────────────────
DB_NAME=${infra.databaseName}
DB_USER=${infra.databaseUser}
DB_PASSWORD=change-me
DB_ROOT_PASSWORD=change-me-root

# ── JWT ──────────────────────────────────────────────────────────────
JWT_SECRET=please-change-me-to-a-long-random-string-of-at-least-64-chars

# ── Mail (optional) ──────────────────────────────────────────────────
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=

# ── Ports ────────────────────────────────────────────────────────────
BACKEND_PORT=${infra.backendPort?c}
FRONTEND_PORT=${infra.frontendPort?c}
