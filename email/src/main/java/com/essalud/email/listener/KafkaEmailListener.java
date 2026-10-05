package com.essalud.email.listener;

import com.essalud.email.model.EmailRequest;
import com.essalud.email.service.EmailSenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaEmailListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaEmailListener.class);

    @Autowired
    private EmailSenderService emailService;

    /** Reintentos ante un fallo transitorio de SMTP/Gmail API — antes esto no existía y un solo
     *  timeout puntual descartaba el correo para siempre, sin ninguna forma de recuperarlo. */
    private static final int MAX_INTENTOS = 3;
    private static final long[] ESPERA_MS = {3_000, 10_000};

    @KafkaListener(topics = "${app.mail.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(ConsumerRecord<String, String> record) {
        log.info("Mensaje recibido de Kafka en {} (offset {})", record.topic(), record.offset());
        EmailRequest request;
        try {
            request = new ObjectMapper().readValue(record.value(), EmailRequest.class);
        } catch (Exception e) {
            log.error("Mensaje de correo malformado en {} (offset {}), se descarta.", record.topic(), record.offset(), e);
            return;
        }
        enviarConReintentos(request);
    }

    private void enviarConReintentos(EmailRequest request) {
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                emailService.sendEmail(request);
                return;
            } catch (Exception e) {
                log.warn("Intento {}/{} de enviar correo a {} falló: {}", intento, MAX_INTENTOS, request.getTo(), e.getMessage());
                if (intento < MAX_INTENTOS) {
                    try {
                        Thread.sleep(ESPERA_MS[intento - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        // Nota: no hay cola de reintento persistente (dead-letter topic) — si los 3 intentos
        // fallan, el correo se descarta. Cubre baches transitorios (timeout puntual, 429 de
        // Gmail); una caída larga de SMTP/Gmail igual requeriría un DLQ para no perder el envío.
        log.error("No se pudo enviar el correo a {} tras {} intentos. Se descarta.", request.getTo(), MAX_INTENTOS);
    }
}
