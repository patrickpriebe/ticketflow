package com.ticketflow.order.infrastructure.persistence.jpa;

import com.ticketflow.order.infrastructure.persistence.entity.TicketCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaTicketCategoryRepository extends JpaRepository<TicketCategoryEntity, UUID> {

    List<TicketCategoryEntity> findByTicketEventIdInOrderByPriceAmountAsc(Collection<UUID> ticketEventIds);
}
