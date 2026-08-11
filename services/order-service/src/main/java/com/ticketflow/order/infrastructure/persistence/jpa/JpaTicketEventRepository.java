package com.ticketflow.order.infrastructure.persistence.jpa;

import com.ticketflow.order.domain.model.EventStatus;
import com.ticketflow.order.infrastructure.persistence.entity.TicketEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaTicketEventRepository extends JpaRepository<TicketEventEntity, UUID> {

    /**
     * Both filters are optional, switched on by the boolean flags.
     *
     * <p>The obvious {@code (:city is null or ...)} form does not work here: a null
     * parameter reaches PostgreSQL untyped, it infers {@code bytea}, and the query
     * dies with "function lower(bytea) does not exist". Passing a flag plus a
     * never-null value keeps every parameter properly typed.
     *
     * <p>Categories are deliberately NOT fetch-joined: joining a collection into a
     * paged query makes Hibernate paginate in memory. The adapter loads the
     * categories for the returned page in a second query.
     */
    @Query("""
            select e from TicketEventEntity e
            where (:filterByCity = false or lower(e.city) = lower(:city))
              and (:filterByStatus = false or e.status = :status)
            order by e.startsAt asc
            """)
    Page<TicketEventEntity> search(@Param("filterByCity") boolean filterByCity,
                                   @Param("city") String city,
                                   @Param("filterByStatus") boolean filterByStatus,
                                   @Param("status") EventStatus status,
                                   Pageable pageable);
}
