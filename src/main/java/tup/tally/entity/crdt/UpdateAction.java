package tup.tally.entity.crdt;

import lombok.Data;
import lombok.NoArgsConstructor;
import tup.tally.entity.Transaction;

@Data
@NoArgsConstructor
public class UpdateAction implements Action {
    private String id = java.util.UUID.randomUUID().toString();
    private long timestamp = System.currentTimeMillis();
    private String targetId;      // 要修改的交易ID
    private Transaction newValue; // 修改后的完整交易对象
}
