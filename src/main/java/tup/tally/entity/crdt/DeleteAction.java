package tup.tally.entity.crdt;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DeleteAction implements Action {
    private String id = java.util.UUID.randomUUID().toString();
    private long timestamp = System.currentTimeMillis();
    private String targetId;
}
