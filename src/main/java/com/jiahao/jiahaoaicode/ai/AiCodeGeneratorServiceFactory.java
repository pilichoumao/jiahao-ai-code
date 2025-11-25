package com.jiahao.jiahaoaicode.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jiahao.jiahaoaicode.ai.guardrail.PromptSafetyInputGuardrail;
import com.jiahao.jiahaoaicode.ai.tools.ToolManager;
import com.jiahao.jiahaoaicode.exception.BusinessException;
import com.jiahao.jiahaoaicode.exception.ErrorCode;
import com.jiahao.jiahaoaicode.model.enums.CodeGenTypeEnum;
import com.jiahao.jiahaoaicode.service.ChatHistoryService;
import com.jiahao.jiahaoaicode.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AT服务创建工厂
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;
//
//    @Resource
//    private StreamingChatModel openAiStreamingChatModel;
//
//    @Resource
//    private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;


    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据 appId 获取服务（带缓存）(兼容老逻辑)
     * @param appId
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 获取服务（带缓存）
     * @param appId
     * @param codeGenType
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId,CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId,codeGenType);
        return serviceCache.get(cacheKey,key ->createAiCodeGeneratorService(appId,codeGenType));
    }

    /**
     * 创建新的 AI 服务实例
     */
    public AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);

        return switch (codeGenType){
            case HTML,MULTI_FILE -> {
                StreamingChatModel streamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(streamingChatModel)
                    .chatMemory(chatMemory)
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .maxSequentialToolsInvocations(20)  // 最多连续调用 20 次工具
//                        .outputGuardrails(new RetryOutputGuardrail()) 为了流式输出，注释掉护轨
                    .build();
            }
            case VUE_PROJECT -> {
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(reasoningStreamingChatModel)
                    .chatMemoryProvider(memoryId ->chatMemory)
                    .tools(toolManager.getAllTools())
                    .hallucinatedToolNameStrategy(toolExecutionRequest ->
                            ToolExecutionResultMessage.from(toolExecutionRequest,
                                    "Error: there is no tool called " + toolExecutionRequest.name())
                    )
                        .maxSequentialToolsInvocations(20)  // 最多连续调用 20 次工具
                        .inputGuardrails(new PromptSafetyInputGuardrail())
//                        .outputGuardrails(new RetryOutputGuardrail()) 为了流式输出，注释掉护轨
                        .build();
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码生成类型： "+codeGenType.getValue());
        };

    }


    /**
     * 创建AI代码生成器服务
     * @return
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    /**
     * 根据appId和codeGenType构造缓存键
     * @param appId
     * @param codeGenType
     * @return
     */
    private String buildCacheKey(long appId,CodeGenTypeEnum codeGenType){
        return appId+'_'+codeGenType.getValue();
    }
}
