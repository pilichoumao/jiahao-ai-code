package com.jiahao.jiahaoaicode.ai.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式消息响应基类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamMessage {
    /**
     * 响应给AI的消息类型
     */
    private String type;
}
