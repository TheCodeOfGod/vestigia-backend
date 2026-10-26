CREATE TABLE codigos_pueblo (
  codigo VARCHAR(30) PRIMARY KEY CHECK (codigo ~ '^[A-Z]+$')
);

CREATE TABLE salas (
  id               UUID PRIMARY KEY,
  codigo           VARCHAR(30) NOT NULL REFERENCES codigos_pueblo(codigo),
  estado           VARCHAR(20) NOT NULL CHECK (estado IN ('esperando_app','activa','finalizada','expirada')),
  escena_actual    VARCHAR(20) NOT NULL DEFAULT 'introduccion',
  token_juego_hash CHAR(64) NOT NULL,
  token_app_hash   CHAR(64),
  creada_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
  ultima_actividad TIMESTAMPTZ NOT NULL DEFAULT now(),
  expira_en        TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX salas_codigo_viva ON salas(codigo) WHERE estado IN ('esperando_app','activa');

CREATE TABLE puzzles_estado (
  sala_id        UUID NOT NULL REFERENCES salas(id) ON DELETE CASCADE,
  puzzle         VARCHAR(20) NOT NULL CHECK (puzzle IN ('radio','fotografia','mirador','observatorio')),
  estado         VARCHAR(20) NOT NULL CHECK (estado IN ('bloqueado','disponible','en_curso','resuelto')),
  datos          JSONB NOT NULL DEFAULT '{}',
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (sala_id, puzzle)
);

CREATE TABLE descubrimientos (
  id        BIGSERIAL PRIMARY KEY,
  sala_id   UUID NOT NULL REFERENCES salas(id) ON DELETE CASCADE,
  clave     VARCHAR(40) NOT NULL,
  datos     JSONB NOT NULL DEFAULT '{}',
  origen    VARCHAR(10) NOT NULL CHECK (origen IN ('juego','app','servidor')),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (sala_id, clave)
);

CREATE TABLE eventos (
  id        BIGSERIAL PRIMARY KEY,
  sala_id   UUID NOT NULL REFERENCES salas(id) ON DELETE CASCADE,
  tipo      VARCHAR(40) NOT NULL,
  payload   JSONB NOT NULL DEFAULT '{}',
  origen    VARCHAR(10) NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX eventos_sala_tiempo ON eventos(sala_id, creado_en);
