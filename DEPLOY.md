# Deploying the Faculty Dashboard

## What runs

| Container | What it does | Exposed |
|---|---|---|
| `web` | nginx: serves the app, forwards `/api`, `/oauth2`, `/login/oauth2` to the backend | port 80 (`HTTP_PORT`) |
| `backend` | Spring Boot API, `prod` profile | internal only |
| `postgres` | PostgreSQL 16, data in the `pgdata` volume | internal only |

Uploaded files live in the `uploads` volume. Both volumes must be backed up.

## Production behaviour (the `prod` profile)

- **Sign-in is Microsoft Entra ID only.** Password-less dev sign-in, the bypass key and the user
  switcher do not exist (they answer 404). Only people who already have an account — created by the
  allocation import, or the admin from `APP_ADMIN_EMAIL` — can sign in.
- **CSRF protection is on.** The app sends the token automatically.
- **"Delete all data", "Reset activity" and "Delete all faculty" are switched off** (404, buttons hidden).
- Session cookies are `Secure`, so the site **must be served over HTTPS**.
- Swagger and every actuator endpoint except `/actuator/health` are off.

## 1. Register the app in Microsoft Entra ID (once)

Azure portal → Microsoft Entra ID → App registrations → New registration.

1. Supported account types: *Accounts in this organizational directory only*.
2. Redirect URI (Web): `https://<your-host>/login/oauth2/code/entra`
3. Certificates & secrets → New client secret → copy the **value**.
4. Note the **Directory (tenant) ID** and **Application (client) ID** from the overview.
5. API permissions: Microsoft Graph → delegated `openid`, `profile`, `email` (granted by default).

## 2. Configure

```bash
cp .env.example .env      # on the server; never commit .env
```

Fill in the database password, the three `ENTRA_*` values, `APP_ADMIN_EMAIL` (the
admin's real Microsoft email), and optionally the SMTP mailbox used for reminder emails.

## 3. Start

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml ps          # backend should become "healthy"
```

Database migrations run automatically on start. Put HTTPS in front: the institution's reverse proxy
or load balancer terminates TLS and forwards to `web` on `HTTP_PORT` (send `X-Forwarded-Proto: https`).

## 4. First use

1. Sign in with the coordinator's Microsoft account.
2. Overview → **Semester**: set the semester name and teaching dates.
3. Course Allocations → **Import Spreadsheet**: the allocation sheet (format in `docs/samples/README.md`).
   Faculty accounts, departments, courses and sections are created from it.
4. Timetable → **Import timetable (CSV)**.
5. Faculty can now sign in with their Microsoft accounts and log classes.

## Backups

```bash
# database (daily, e.g. from cron)
docker compose -f docker-compose.prod.yml exec -T postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > backup-$(date +%F).sql.gz
# uploaded files
docker run --rm -v poori-sabzi_uploads:/data -v "$PWD":/out alpine tar czf /out/uploads-$(date +%F).tgz -C /data .
```

Restore: `gunzip -c backup.sql.gz | docker compose -f docker-compose.prod.yml exec -T postgres psql -U "$POSTGRES_USER" "$POSTGRES_DB"`.

## Updating

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

## Hosting on Vercel + Render

An alternative to the Docker Compose stack above: the React app on **Vercel**, the Spring Boot API and
PostgreSQL on **Render**. Config lives in `frontend/vercel.json` and `render.yaml` (a Render Blueprint).

**How the pieces talk.** Vercel serves the app and *proxies* `/api/*`, `/oauth2/*`, `/login/oauth2/*`
and `/actuator/health` to the Render service. The browser only ever talks to the Vercel domain, so the
session cookie (`Secure`, `SameSite=Lax`) and the `XSRF-TOKEN` cookie are same-origin and no CORS is
needed. The backend builds the Microsoft sign-in redirect URI from `APP_PUBLIC_URL` (the Vercel
address), because Vercel's proxy is not relied on to pass the original host through, and it answers
the post-sign-in redirect with a relative `Location: /` so the browser stays on the Vercel domain.

Secrets go only into the Render dashboard. Nothing secret is written to `render.yaml` or `vercel.json`.

### 1. Render: database and backend

1. Push the repo to GitHub (or GitLab).
2. Render dashboard → **New** → **Blueprint** → connect the repo. Render reads `render.yaml` and
   proposes a database `faculty-dashboard-db` and a web service `faculty-dashboard-api`.
3. Render asks for every variable marked `sync: false`. Fill in:
   | Variable | Value |
   |---|---|
   | `APP_PUBLIC_URL` | The Vercel address, e.g. `https://faculty-dashboard.vercel.app` (no trailing slash). If you do not know it yet, enter a guess and fix it after step 2. |
   | `ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID`, `ENTRA_CLIENT_SECRET` | From the Entra app registration (step 3) |
   | `APP_ADMIN_EMAIL` | The admin's Microsoft email; they set the admin password at first sign-in |
   | `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | The SMTP mailbox for reminder emails |
4. **Apply**. Render creates the database, builds `backend/Dockerfile` and starts the service with
   `SPRING_PROFILES_ACTIVE=prod`. Flyway runs the migrations on first start. The deploy counts as
   live once `/actuator/health` reports `UP`.
5. Copy the service URL (`https://<name>.onrender.com`). To change a variable later, open the
   service → **Environment** → edit → **Save, rebuild and deploy**.

What the Blueprint sets up:

- **Database connection.** Render hands out `postgres://user:pass@host:port/db`, which JDBC does not
  accept. The Blueprint passes the parts instead (`DB_HOST`, `DB_PORT`, `DB_NAME` and
  `SPRING_DATASOURCE_USERNAME`/`PASSWORD`, all `fromDatabase`), and `application-prod.yml` assembles
  `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`. Setting `SPRING_DATASOURCE_URL` still
  overrides this, which is how the Compose stack does it. The database has no public access
  (`ipAllowList: []`). Only services in the same region can reach it, so keep both in one `region`.
- **Uploaded files.** These go on a 1 GB persistent disk mounted at `/data/uploads`
  (`app.storage.upload-dir`, set by `STORAGE_UPLOAD_DIR` in the Dockerfile). Render snapshots disks
  daily. Disks need a paid instance (`plan: starter`), and a service with a disk cannot scale out
  and has a few seconds of downtime on each deploy.
- **Memory.** `JAVA_OPTS` is tuned for a 512 MB instance (capped heap, serial GC, small stacks and
  code cache, exit on OOM so Render restarts the service), and the DB pool is capped at 5 connections.
- **Port.** The app listens on `$PORT` (`server.port: ${PORT:8080}` in `application-prod.yml`). The
  Blueprint pins `PORT=8080`.

### 2. Vercel: frontend

1. In `frontend/vercel.json`, replace every `https://YOUR-RENDER-SERVICE.onrender.com` with the
   Render URL from step 1. There are four rewrites. Commit and push.
2. Vercel dashboard → **Add New…** → **Project** → import the repo.
3. **Root Directory**: `frontend`. Vercel picks up the framework (Vite), `npm run build` and the
   output directory `dist` from `vercel.json`. No environment variables are needed.
4. **Deploy**. Note the production domain (e.g. `faculty-dashboard.vercel.app`, or your own domain
   added under **Settings → Domains**).
5. If this domain differs from what you entered for `APP_PUBLIC_URL`, correct it on Render (it must
   be exactly `https://<vercel-domain>`).

`vercel.json` also sends the same security headers as `frontend/nginx.conf` (CSP, `X-Frame-Options`,
`nosniff`, referrer and permissions policies) plus HSTS. Hashed `/assets/*` files are cached for a
year, and every other path is `no-store`. Unknown paths fall back to `index.html` for client-side
routing.

### 3. Microsoft Entra ID app registration

Azure portal → **Microsoft Entra ID** → **App registrations** → **New registration**:

1. Name: e.g. *Faculty Dashboard*. Supported account types: *Accounts in this organizational
   directory only*.
2. Redirect URI, platform **Web**: `https://<vercel-domain>/login/oauth2/code/entra`. This must be
   the **Vercel** domain, not the onrender.com one, and match `APP_PUBLIC_URL` exactly. If you add a
   custom domain later, add its redirect URI too and update `APP_PUBLIC_URL`.
3. **Certificates & secrets** → **New client secret** → copy the **Value** (shown only once) into
   `ENTRA_CLIENT_SECRET` on Render. Note its expiry date: sign-in stops working when the secret
   expires.
4. **Overview**: copy **Directory (tenant) ID** → `ENTRA_TENANT_ID`, and **Application (client) ID**
   → `ENTRA_CLIENT_ID`.
5. **API permissions**: Microsoft Graph, delegated `openid`, `profile`, `email` (`User.Read` is
   there by default and harmless). Grant admin consent if your tenant requires it.

### Before Microsoft sign-in is ready (preview)

The backend starts without any Microsoft settings. To let people in meanwhile, set
`APP_PREVIEW_SIGNIN=true` and `APP_ADMIN_INITIAL_PASSWORD` (12+ characters; stored hashed). Then
everyone signs in by typing their email, and the admin also enters that password.

Preview sign-in trusts the typed email, so anyone who knows a registered email can open that
person's dashboard. Use it for testing only. To switch to Microsoft: register the app (section 3),
set the `ENTRA_*` variables, set `SPRING_PROFILES_ACTIVE=prod,entra`, and delete
`APP_PREVIEW_SIGNIN` and `APP_ADMIN_INITIAL_PASSWORD`.

### 4. The admin, coordinators and faculty

- **Admin:** the account in `APP_ADMIN_EMAIL` (e.g. `s.taruna@jklu.edu.in`). It is created on
  startup. At their first Microsoft sign-in they choose the **admin password**; only a BCrypt
  hash is stored. Every later sign-in asks for it. After 5 wrong tries it locks for 15 minutes.
  Forgotten password: set `APP_ADMIN_RESET_PASSWORD=true`, redeploy, let the admin choose a new
  one, then set it back to `false`.
- **Coordinators and faculty** come from the allocation sheet the admin uploads. Its **Role**
  column (`Coordinator` or `Faculty`) decides who gets the coordinator dashboard. People are
  matched by email when they sign in with Microsoft; they don't need a password. Re-uploading
  updates roles. The admin and the person uploading are never demoted by a sheet.
- Email is optional (`SPRING_MAIL_*`): it is used for reminders only.

Then follow **4. First use** above (semester dates, allocation import, timetable import).

### 5. Render free tier: what changes

- **Sleeping:** free web services spin down after 15 minutes without requests. The backend pings its
  own public URL (`RENDER_EXTERNAL_URL`, set by Render) every 10 minutes, so it stays awake. One
  service running all month (~744 h) fits inside Render's 750 free instance hours. As a backup, add
  a free uptime monitor (UptimeRobot or cron-job.org) on `https://<service>.onrender.com/actuator/health`
  every 10 minutes.

- **Cold starts.** A free web service sleeps after about 15 minutes without traffic. The next request
  wakes it, which takes a minute or more for a Spring Boot app. Meanwhile the Vercel proxy may time
  out and show an error, so reload after a minute. Every wake is also a restart, and sessions are
  held in memory, so everyone has to sign in again. A paid instance (`starter`) never sleeps.
- **No persistent disk.** Remove the `disk:` block and set `plan: free`. Uploaded files then go to the
  container's ephemeral filesystem and are **lost on every deploy, restart and spin-down**. This
  does not work for real use.
- **Free Postgres expires** 30 days after creation, and has no backups. Use `basic-256mb` or larger
  for anything real.
- Upload limits and request timeouts of Vercel's external proxy apply to `/api` traffic. Test a
  full-size (up to 25 MB) upload through the Vercel domain before go-live.

### Checklist for this setup

- [ ] All four rewrites in `frontend/vercel.json` point at the real Render URL
- [ ] `APP_PUBLIC_URL` = `https://<vercel-domain>`, and the Entra redirect URI is that plus `/login/oauth2/code/entra`
- [ ] `https://<vercel-domain>/actuator/health` returns `{"status":"UP"}`
- [ ] `https://<vercel-domain>/api/auth/config` shows `"devLogin":false` and `"destructiveActions":false`
- [ ] Coordinator sign-in returns you to the Vercel domain (not onrender.com) and stays signed in
- [ ] An uploaded file still downloads after a redeploy (persistent disk works)
- [ ] `SPRING_PROFILES_ACTIVE` is `prod`. Never `dev`.

## Local development (not for servers)

```bash
docker compose up -d                 # postgres + mailpit (http://localhost:8025)
scripts/dev-backend.sh               # backend with auto-reload, dev profile
cd frontend && npm run dev           # http://localhost:5173
```

The `dev` profile turns on dev sign-in, the user switcher and the bulk-delete buttons. Never set
`SPRING_PROFILES_ACTIVE=dev` on a server.

## Pre-launch checklist

- [ ] HTTPS in front of `web`; `http://` redirects to `https://`
- [ ] `.env` filled; database password is long and random; `.env` not in git
- [ ] Entra redirect URI matches the public host exactly
- [ ] Coordinator can sign in; a faculty member from the sheet can sign in; someone not in the sheet is refused
- [ ] `/api/auth/config` shows `"devLogin":false` and `"destructiveActions":false`
- [ ] A reminder email arrives (SMTP works)
- [ ] Database and uploads backups scheduled and one restore tested
