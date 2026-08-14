package br.com.nexapay.account.repository;

import br.com.nexapay.account.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
