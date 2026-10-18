package com.rokad.service.calculation;

import com.rokad.entity.Transaction;
import com.rokad.entity.TransactionType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FinancialCalculationService {

  public BalanceResult calculateBalance(BigDecimal openingBalance, List<Transaction> transactions) {
    List<Transaction> sorted =
        transactions.stream()
            .sorted(
                Comparator.comparing(Transaction::getTransactionDate)
                    .thenComparing(
                        Transaction::getTransactionId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();

    BigDecimal totalDebit = BigDecimal.ZERO;
    BigDecimal totalCredit = BigDecimal.ZERO;
    BigDecimal runningBalance = openingBalance;
    List<BalanceResult.RunningBalanceEntry> runningBalances = new ArrayList<>();

    for (Transaction t : sorted) {
      if (t.getTransactionType() == TransactionType.CREDIT) {
        totalCredit = totalCredit.add(t.getAmount());
        runningBalance = runningBalance.add(t.getAmount());
      } else {
        totalDebit = totalDebit.add(t.getAmount());
        runningBalance = runningBalance.subtract(t.getAmount());
      }
      runningBalances.add(
          new BalanceResult.RunningBalanceEntry(
              t.getTransactionId(), t.getTransactionDate(), runningBalance));
    }

    return new BalanceResult(
        openingBalance, totalDebit, totalCredit, runningBalance, runningBalances);
  }
}
