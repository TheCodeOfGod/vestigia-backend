CREATE TABLE usuarios (
  id            UUID PRIMARY KEY,
  nombre        VARCHAR(80)  NOT NULL,
  correo        VARCHAR(254) NOT NULL CHECK (correo = lower(correo)),
  password_hash VARCHAR(100) NOT NULL,
  rol           VARCHAR(20)  NOT NULL CHECK (rol IN ('admin')),
  activo        BOOLEAN      NOT NULL DEFAULT TRUE,
  creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT usuarios_correo_unico UNIQUE (correo)
);

CREATE TABLE sesiones_admin (
  token_hash CHAR(64)    PRIMARY KEY,
  usuario_id UUID        NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  creada_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expira_en  TIMESTAMPTZ NOT NULL
);
CREATE INDEX sesiones_admin_usuario ON sesiones_admin(usuario_id);
CREATE INDEX sesiones_admin_expira ON sesiones_admin(expira_en);
