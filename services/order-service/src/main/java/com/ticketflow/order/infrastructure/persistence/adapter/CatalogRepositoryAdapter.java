package com.ticketflow.order.infrastructure.persistence.adapter;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.domain.exception.ConcurrentInventoryUpdateException;
import com.ticketflow.order.domain.model.EventStatus;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;
import com.ticketflow.order.infrastructure.persistence.entity.TicketCategoryEntity;
import com.ticketflow.order.infrastructure.persistence.entity.TicketEventEntity;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaTicketCategoryRepository;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaTicketEventRepository;
import com.ticketflow.order.infrastructure.persistence.mapper.CatalogMapper;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CatalogRepositoryAdapter implements CatalogRepository {

    private final JpaTicketEventRepository jpaEvents;
    private final JpaTicketCategoryRepository jpaCategories;

    public CatalogRepositoryAdapter(JpaTicketEventRepository jpaEvents,
                                    JpaTicketCategoryRepository jpaCategories) {
        this.jpaEvents = jpaEvents;
        this.jpaCategories = jpaCategories;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TicketEvent> findById(UUID ticketEventId) {
        return jpaEvents.findById(ticketEventId)
                .map(entity -> CatalogMapper.toDomain(entity, categoriesOf(List.of(entity.getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TicketEvent> search(String city, EventStatus status, PageQuery pageQuery) {
        // Never pass a null down: see the note on JpaTicketEventRepository.search.
        // The placeholder values are unreachable because the flag is false.
        Page<TicketEventEntity> page = jpaEvents.search(
                city != null, city == null ? "" : city,
                status != null, status == null ? EventStatus.DRAFT : status,
                PageRequest.of(pageQuery.page(), pageQuery.size()));

        // Second query instead of a fetch join: joining a collection into a paged
        // query forces Hibernate to paginate in memory, which quietly stops scaling.
        List<UUID> eventIds = page.getContent().stream().map(TicketEventEntity::getId).toList();
        Map<UUID, List<TicketCategoryEntity>> byEvent = eventIds.isEmpty()
                ? Map.of()
                : categoriesOf(eventIds).stream()
                        .collect(Collectors.groupingBy(category -> category.getTicketEvent().getId()));

        List<TicketEvent> content = page.getContent().stream()
                .map(entity -> CatalogMapper.toDomain(entity, byEvent.getOrDefault(entity.getId(), List.of())))
                .toList();

        return new PageResult<>(content, pageQuery.page(), pageQuery.size(), page.getTotalElements());
    }

    @Override
    public void updateInventory(List<TicketCategory> categories) {
        if (categories.isEmpty()) {
            return;
        }
        Map<UUID, TicketCategory> byId = categories.stream()
                .collect(Collectors.toMap(TicketCategory::id, category -> category));

        List<TicketCategoryEntity> entities = jpaCategories.findAllById(byId.keySet());
        for (TicketCategoryEntity entity : entities) {
            TicketCategory updated = byId.get(entity.getId());
            entity.applyCounters(updated.reservedQuantity(), updated.soldQuantity());
        }

        try {
            // Forces the optimistic-lock check now, so a lost race is reported as a
            // domain failure the web layer turns into 409 - not as a 500 at commit.
            jpaCategories.saveAllAndFlush(entities);
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentInventoryUpdateException();
        }
    }

    private List<TicketCategoryEntity> categoriesOf(Collection<UUID> eventIds) {
        return jpaCategories.findByTicketEventIdInOrderByPriceAmountAsc(eventIds);
    }
}
