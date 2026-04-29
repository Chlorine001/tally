package tup.tally.entity.crdt;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * @author chlorine
 * @version 1.0
 * @Date 2026-04-29
 * @Description
 */

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AddAction.class, name = "add"),
        @JsonSubTypes.Type(value = UpdateAction.class, name = "update"),
        @JsonSubTypes.Type(value = DeleteAction.class, name = "delete"),
        @JsonSubTypes.Type(value = MetaAction.class, name = "meta")
})
public interface Action {
    String getId();          // 操作自身的唯一ID

    long getTimestamp();     // 操作发生时间（毫秒）
}

