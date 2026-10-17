package com.rokad.repository;

import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerRepository extends JpaRepository<Ledger, Long> {

    Optional<Ledger> findFirstByStatus(LedgerStatus status);

    List<Ledger> findAllByStatusOrderByClosedAtDesc(LedgerStatus status);
}