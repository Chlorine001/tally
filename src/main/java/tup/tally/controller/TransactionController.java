package tup.tally.controller;

import org.springframework.web.bind.annotation.*;
import tup.tally.entity.Transaction;

import tup.tally.entity.Transaction;
import tup.tally.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


import java.util.List;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction add(@Valid @RequestBody Transaction transaction) {
        return transactionService.add(transaction);
    }

    @PutMapping("/{id}")
    public Transaction update(@PathVariable String id, @Valid @RequestBody Transaction transaction) {
        transaction.setId(id); // 确保 path 中的 id 被使用
        return transactionService.update(id, transaction);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        transactionService.delete(id);
    }

    @GetMapping("/{id}")
    public Transaction getById(@PathVariable String id) {
        Transaction t = transactionService.findById(id);
        if (t == null) {
            throw new RuntimeException("Transaction not found: " + id);
        }
        return t;
    }

    @GetMapping
    public List<Transaction> list(@RequestParam(required = false) Integer year,
                                  @RequestParam(required = false) Integer month) {
        if (year != null && month != null) {
            return transactionService.list(year, month);
        } else if (year == null && month == null) {
            return transactionService.listAll();
        } else {
            // 只传了一个参数的情况，默认返回全部（也可以抛错，但为了友好返回全部）
            return transactionService.listAll();
        }
    }
}