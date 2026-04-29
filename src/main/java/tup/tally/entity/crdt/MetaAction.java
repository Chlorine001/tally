package tup.tally.entity.crdt;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public  class MetaAction implements Action {
    private String id = java.util.UUID.randomUUID().toString();
    private long timestamp = System.currentTimeMillis();
    private String key;           // 元数据键，如 "tags", "budgets"
    private Object value;         // 整个元数据对象（覆盖式）
}
