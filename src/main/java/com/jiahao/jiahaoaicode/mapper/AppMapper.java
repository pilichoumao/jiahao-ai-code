package com.jiahao.jiahaoaicode.mapper;

import com.jiahao.jiahaoaicode.model.entity.ChatHistory;
import com.mybatisflex.core.BaseMapper;
import com.jiahao.jiahaoaicode.model.entity.App;

/**
 * 应用 映射层。
 *
 * @author jiahao
 * @since 2025-11-13
 */
public interface AppMapper extends BaseMapper<App> {

    /**
     * 对话历史 映射层。
     *
     * @author jiahao
     * @since 2025-11-14
     */
    interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    }
}
