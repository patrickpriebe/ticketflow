package com.ticketflow.payment.application.port.out;

import java.util.Optional;

/**
 * Lê no provedor o segredo que autoriza o navegador a confirmar a cobrança.
 *
 * <p>Porta separada da {@link PaymentGateway} de propósito: aquela é o caminho
 * de escrita — cria e autoriza cobrança —, esta é só leitura, e quem a usa é uma
 * consulta vinda de uma requisição HTTP, não o consumidor de Kafka.
 *
 * <p><strong>Este segredo não é persistido em lugar nenhum.</strong> Ele é
 * buscado no provedor a cada consulta, pelo id da cobrança que já guardamos.
 * Guardar renderia uma coluna a mais com uma credencial dentro, e credencial que
 * dá para não guardar não se guarda — o custo é uma chamada de rede numa tela
 * que a pessoa abre uma vez.
 */
public interface PaymentIntentReader {

    /**
     * O segredo, quando a cobrança ainda espera confirmação.
     *
     * <p>Vazio quando não há o que confirmar: cobrança já paga, cancelada, ou um
     * provedor que não trabalha assim — é o caso do gateway simulado, onde não
     * existe navegador nenhum no meio.
     */
    Optional<String> clientSecretFor(String transactionId);
}
