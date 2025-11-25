package com.jiahao.jiahaoaicode.service;

import com.jiahao.jiahaoaicode.model.dto.ChatHistoryQueryRequest;
import com.jiahao.jiahaoaicode.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.jiahao.jiahaoaicode.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author jiahao
 * @since 2025-11-14
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    /**
     * 添加消息记录
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return
     */
    boolean addChatMessage(Long appId,String message,String messageType,Long userId);

    /**
     *  删除对话历史
     * @param appId
     * @return
     */
    boolean deleteByAppId(Long appId);


    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 分页查询某app的历史对话
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
