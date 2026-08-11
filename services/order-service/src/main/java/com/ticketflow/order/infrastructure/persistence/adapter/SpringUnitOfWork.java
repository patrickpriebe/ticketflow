package com.ticketflow.order.infrastructure.persistence.adapter;

import com.ticketflow.order.application.port.out.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Driven adapter: turns "run this atomically" into a real Spring transaction.
 *
 * <p>This class exists so {@code @Transactional} never has to appear on a use case.
 * The rule it enforces is the one the outbox pattern depends on: the order row and
 * the outbox row commit together, or neither does.
 */
@Component
public class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactionTemplate;

    public SpringUnitOfWork(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
