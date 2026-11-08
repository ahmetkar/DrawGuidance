-- v42 web sitesi veritabanı (Vercel Neon Postgres). Uygulamanın sunucusundan (MongoDB) ayrıdır.
-- Kurulum: DATABASE_URL verilip `npm run db:migrate` (tekrar çalıştırmak güvenli: IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS site_content (
  locale      text        NOT NULL CHECK (locale IN ('tr', 'en')),
  key         text        NOT NULL CHECK (key IN ('home', 'about', 'download')),
  value       jsonb       NOT NULL,
  updated_at  timestamptz NOT NULL DEFAULT now(),
  updated_by  uuid,
  PRIMARY KEY (locale, key)
);

CREATE TABLE IF NOT EXISTS site_images (
  slot        text        PRIMARY KEY,
  url         text        NOT NULL DEFAULT '',
  alt_tr      text        NOT NULL DEFAULT '',
  alt_en      text        NOT NULL DEFAULT '',
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS contact_messages (
  id          bigserial   PRIMARY KEY,
  name        text        NOT NULL,
  email       text        NOT NULL,
  subject     text        NOT NULL,
  message     text        NOT NULL,
  locale      text        NOT NULL,
  ip_hash     text        NOT NULL,
  user_agent  text        NOT NULL DEFAULT '',
  created_at  timestamptz NOT NULL DEFAULT now(),
  read_at     timestamptz,
  archived    boolean     NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS contact_messages_created ON contact_messages (created_at DESC);

CREATE TABLE IF NOT EXISTS admin_users (
  id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  name            text        NOT NULL,
  webauthn_id     text        NOT NULL UNIQUE,
  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS admin_credentials (
  id            text        PRIMARY KEY,
  user_id       uuid        NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
  public_key    bytea       NOT NULL,
  counter       bigint      NOT NULL DEFAULT 0,
  transports    text[]      NOT NULL DEFAULT '{}',
  device_type   text        NOT NULL DEFAULT '',
  backed_up     boolean     NOT NULL DEFAULT false,
  nickname      text        NOT NULL DEFAULT '',
  created_at    timestamptz NOT NULL DEFAULT now(),
  last_used_at  timestamptz
);

CREATE TABLE IF NOT EXISTS admin_sessions (
  token_hash    text        PRIMARY KEY,
  user_id       uuid        NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
  created_at    timestamptz NOT NULL DEFAULT now(),
  last_seen_at  timestamptz NOT NULL DEFAULT now(),
  expires_at    timestamptz NOT NULL,
  ip_hash       text        NOT NULL DEFAULT '',
  user_agent    text        NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS admin_sessions_user ON admin_sessions (user_id);

CREATE TABLE IF NOT EXISTS webauthn_challenges (
  id          text        PRIMARY KEY,
  challenge   text        NOT NULL,
  purpose     text        NOT NULL CHECK (purpose IN ('register', 'login')),
  user_id     uuid,
  -- ilk kurulumda henüz kaydedilmemiş yöneticinin adı ve WebAuthn kimliği
  user_name   text,
  webauthn_id text,
  expires_at  timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS rate_limits (
  key           text        PRIMARY KEY,
  count         integer     NOT NULL,
  window_start  timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS admin_audit (
  id          bigserial   PRIMARY KEY,
  user_id     uuid,
  action      text        NOT NULL,
  detail      text        NOT NULL DEFAULT '',
  ip_hash     text        NOT NULL DEFAULT '',
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS admin_audit_created ON admin_audit (created_at DESC);
