package tup.tally.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tup.tally.entity.Transaction;
import tup.tally.entity.crdt.AddAction;
import tup.tally.entity.crdt.DeleteAction;
import tup.tally.entity.crdt.UpdateAction;
import tup.tally.service.ActionLogService;
import tup.tally.service.GitSyncService;

import java.util.List;
import java.util.UUID;

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


    private final ActionLogService actionLogService;
    private final GitSyncService gitSyncService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction add(@Valid @RequestBody Transaction transaction) throws Exception {
        transaction.setId(UUID.randomUUID().toString());
        long now = System.currentTimeMillis();
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);
        AddAction action = new AddAction();
        action.setContent(transaction);
        actionLogService.appendAction(action);
        gitSyncService.commitAndPush("Add transaction: " + transaction.getId());
        return transaction;
    }

    @PutMapping("/{id}")
    public Transaction update(@PathVariable String id, @Valid @RequestBody Transaction transaction) throws Exception {
        transaction.setId(id);
        transaction.setUpdatedAt(System.currentTimeMillis());
        UpdateAction action = new UpdateAction();
        action.setTargetId(id);
        action.setNewValue(transaction);
        actionLogService.appendAction(action);
        gitSyncService.commitAndPush("Update transaction: " + id);
        return transaction;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) throws Exception {
        DeleteAction action = new DeleteAction();
        action.setTargetId(id);
        actionLogService.appendAction(action);
        gitSyncService.commitAndPush("Delete transaction: " + id);
    }

    @GetMapping("/{id}")
    public Transaction getById(@PathVariable String id) {
        Transaction t = actionLogService.getTransaction(id);
        if (t == null) throw new RuntimeException("Transaction not found: " + id);
        return t;
    }

    @GetMapping
    public List<Transaction> list(@RequestParam(required = false) Integer year,
                                  @RequestParam(required = false) Integer month) {
        // 从内存中过滤（简单实现，后续可优化）
        return actionLogService.getAllTransactions().values().stream()
                .filter(t -> {
                    if (year != null && t.getDate().getYear() != year) return false;
                    if (month != null && t.getDate().getMonthValue() != month) return false;
                    return true;
                })
                .sorted((a,b) -> b.getDate().compareTo(a.getDate()))
                .toList();
    }
}