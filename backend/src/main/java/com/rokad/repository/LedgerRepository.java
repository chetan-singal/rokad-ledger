package com.rokad.repository;

import com.rokad.entity.Ledger;
import com.rokad.entity.LedgerStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepository extends JpaRepository<Ledger, Long> {

  Optional<Ledger> findFirstByStatus(LedgerStatus status);

  List<Ledger> findAllByStatusOrderByClosedAtDesc(LedgerStatus status);

  // Most recently closed first; the id settles a tie between two closures in the same second.
  List<Ledger> findAllByStatusOrderByClosedAtDescLedgerIdDesc(LedgerStatus status);

  long countByStatus(LedgerStatus status);
}
