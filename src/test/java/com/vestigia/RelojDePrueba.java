package com.vestigia;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Hora del sistema más un desfase que las pruebas adelantan. Microsegundos, como TIMESTAMPTZ. */
public class RelojDePrueba extends Clock {

	private volatile Duration desfase = Duration.ZERO;

	public void avanzar(Duration duracion) {
		desfase = desfase.plus(duracion);
	}

	public void reiniciar() {
		desfase = Duration.ZERO;
	}

	@Override
	public Instant instant() {
		return Instant.now().plus(desfase).truncatedTo(ChronoUnit.MICROS);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

}
