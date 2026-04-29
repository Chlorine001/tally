package tup.tally.service;

import tup.tally.entity.Transaction;

import java.time.YearMonth;
import java.util.List;
/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */
public interface TransactionService {
    Transaction add(Transaction transaction);
    Transaction update(String id, Transaction transaction);
    void delete(String id);
    Transaction findById(String id);
    List<Transaction> list(Integer year, Integer month);
    List<Transaction> listAll();
}