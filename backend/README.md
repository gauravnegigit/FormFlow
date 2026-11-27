# FormFlow Backend Configuration

FormFlow uses Spring Boot 4.2, Java 21, Spring Security 7, MySQL, and JJWT 0.12.6.
All database credentials and JWT signing secrets are externalized via environment variables with **no hardcoded fallback secrets**. The application will fail at startup if required variables are missing or invalid.

---

## Required Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_USERNAME` | Yes | None (fails startup if missing) | MySQL database username |
| `DB_PASSWORD` | Yes | None (fails startup if missing) | MySQL database password |
| `JWT_SECRET` | Yes | None (fails startup if missing) | Base64-encoded HMAC secret with at least 256 bits (32 bytes) of entropy |
| `DB_URL` | No | `jdbc:mysql://localhost:3306/formflow_db` | JDBC MySQL connection URL |
| `JWT_EXPIRATION_MS` | No | `86400000` (24 hours) | Access token expiration in milliseconds |
| `FRONTEND_ORIGIN` | No (Yes in prod) | `http://localhost:5173` | Allowed CORS frontend origin(s), comma-separated (`*` is rejected) |
| `RATE_LIMIT_ENABLED` | No | `true` | Enables/disables in-memory rate limiting |
| `RATE_LIMIT_TRUST_PROXY_HEADERS` | No | `false` | Whether to read `X-Forwarded-For` for client IP (enable only behind a trusted reverse proxy) |
| `RATE_LIMIT_LOGIN_MAX` | No | `10` | Max `POST /api/auth/login` requests per IP per window |
| `RATE_LIMIT_LOGIN_WINDOW_SECONDS` | No | `60` | Login rate-limit window in seconds |
| `RATE_LIMIT_REGISTER_MAX` | No | `5` | Max `POST /api/auth/register` requests per IP per window |
| `RATE_LIMIT_REGISTER_WINDOW_SECONDS` | No | `300` | Registration rate-limit window in seconds |
| `RATE_LIMIT_SUBMIT_MAX` | No | `20` | Max `POST /api/submissions/public/{slug}` requests per IP per window |
| `RATE_LIMIT_SUBMIT_WINDOW_SECONDS` | No | `60` | Public submission rate-limit window in seconds |
| `RATE_LIMIT_TRACK_MAX` | No | `30` | Max `GET /api/submissions/track/{trackingToken}` requests per IP per window |
| `RATE_LIMIT_TRACK_WINDOW_SECONDS` | No | `60` | Tracking rate-limit window in seconds |
| `JPA_SHOW_SQL` | No | `false` | Enables Hibernate SQL logging (`true`/`false`) |
| `SERVER_PORT` | No | `8080` | HTTP server port |

---

## Generating a 256-bit Base64 `JWT_SECRET`

Never commit secrets or reuse previously committed keys. Generate a cryptographically random 256-bit (or 384-bit) Base64 key:

**PowerShell (Windows):**
```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

**OpenSSL (Linux / macOS / Git Bash):**
```bash
openssl rand -base64 32
```

---

## Configuring Environment Variables Locally

### PowerShell Terminal
```powershell
$env:DB_USERNAME="your_db_user"
$env:DB_PASSWORD="your_db_password"
$env:JWT_SECRET="<base64-generated-secret>"
cd backend
.\mvnw.cmd spring-boot:run
```

### VS Code (`launch.json`)
Add an `"env"` block inside `.vscode/launch.json` (already ignored by `.gitignore`):
```json
{
  "type": "java",
  "name": "DemoApplication",
  "request": "launch",
  "mainClass": "in.FormFlow.demo.DemoApplication",
  "projectName": "demo",
  "env": {
    "DB_USERNAME": "your_db_user",
    "DB_PASSWORD": "your_db_password",
    "JWT_SECRET": "<base64-generated-secret>"
  }
}
```

### IntelliJ IDEA
1. Open **Run → Edit Configurations...**
2. Select `DemoApplication`
3. Under **Environment variables**, add:
   `DB_USERNAME=your_db_user;DB_PASSWORD=your_db_password;JWT_SECRET=<base64-generated-secret>`
4. Click **Apply** and **Run**.
