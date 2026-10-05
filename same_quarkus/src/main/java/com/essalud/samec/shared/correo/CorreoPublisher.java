package com.essalud.samec.shared.correo;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Publica eventos de correo en Kafka para que los envíe email-service.
 *
 * Si hay una transacción en curso, el evento sale recién cuando esta se confirma: así un correo
 * nunca avisa de algo que al final no se guardó (en Auditorías el evento salía antes del commit).
 * El envío no bloquea a quien llama; si Kafka no confirma, queda registrado en el log.
 */
@ApplicationScoped
public class CorreoPublisher {

    private static final Logger LOG = Logger.getLogger(CorreoPublisher.class);

    @Inject
    @Channel("correos")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 1024)
    Emitter<CorreoRequest> emitter;

    @Inject
    TransactionSynchronizationRegistry transacciones;

    public void enviar(CorreoRequest correo) {
        if (correo.to() == null || correo.to().isBlank()) {
            LOG.warnf("Correo '%s' sin destinatario: no se envía.", correo.subject());
            return;
        }
        if (transacciones.getTransactionStatus() == Status.STATUS_ACTIVE) {
            transacciones.registerInterposedSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {
                }

                @Override
                public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) publicar(correo);
                }
            });
        } else {
            publicar(correo);
        }
    }

    private void publicar(CorreoRequest correo) {
        try {
            emitter.send(correo).whenComplete((ok, error) -> {
                if (error != null) {
                    LOG.errorf("No se pudo publicar el correo '%s' para %s: %s",
                            correo.subject(), correo.to(), error.getMessage());
                }
            });
        } catch (RuntimeException e) {
            // Canal saturado o no disponible: el correo se pierde, pero nunca tumba la operación.
            LOG.errorf(e, "No se pudo encolar el correo '%s' para %s", correo.subject(), correo.to());
        }
    }
}
