package tup.tally.entity.crdt;

import lombok.Data;
import lombok.NoArgsConstructor;
import tup.tally.entity.Transaction;

@Data
@NoArgsConstructor
public class AddAction implements Action {
    private String id = java.util.UUID.randomUUID().toString();
    private long timestamp = System.currentTimeMillis();
    private Transaction content;
}
