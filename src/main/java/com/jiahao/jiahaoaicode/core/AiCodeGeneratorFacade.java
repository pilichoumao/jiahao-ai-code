package com.jiahao.jiahaoaicode.core;

import cn.hutool.json.JSONUtil;
import com.jiahao.jiahaoaicode.ai.AiCodeGeneratorService;
import com.jiahao.jiahaoaicode.ai.AiCodeGeneratorServiceFactory;
import com.jiahao.jiahaoaicode.ai.model.HtmlCodeResult;
import com.jiahao.jiahaoaicode.ai.model.MultiFileCodeResult;
import com.jiahao.jiahaoaicode.ai.model.message.AiResponseMessage;
import com.jiahao.jiahaoaicode.ai.model.message.ToolExecutedMessage;
import com.jiahao.jiahaoaicode.ai.model.message.ToolRequestMessage;
import com.jiahao.jiahaoaicode.constant.AppConstant;
import com.jiahao.jiahaoaicode.core.builder.VueProjectBuilder;
import com.jiahao.jiahaoaicode.core.paser.CodeParserExecutor;
import com.jiahao.jiahaoaicode.core.saver.CodeFileSaverExecutor;
import com.jiahao.jiahaoaicode.exception.BusinessException;
import com.jiahao.jiahaoaicode.exception.ErrorCode;
import com.jiahao.jiahaoaicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
@Slf4j
/**
 * AI代码生成门面类，组合代码生成和保存功能
 */
@Service
public class AiCodeGeneratorFacade {
    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 统一入口：根据类型生成并保存代码
     * @param appId
     * @param userMessage
     * @param codeGenType
     * @return
     */
    public File generatorAndSaveCode(String userMessage , CodeGenTypeEnum codeGenType,Long appId){
        if(codeGenType == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenType);

        return switch (codeGenType){
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield  CodeFileSaverExecutor.executeSaver(result,CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield  CodeFileSaverExecutor.executeSaver(result,CodeGenTypeEnum.MULTI_FILE,appId);
            }
            default -> {
                String errorMessage ="不支持的生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,errorMessage);
            }
        };

    }

    /**
     * 统一入口：根据类型生成并保存代码(流式)
     * @param appId
     * @param userMessage
     * @param codeGenType
     * @return
     */
    public Flux<String> generatorAndSaveCodeStream(String userMessage , CodeGenTypeEnum codeGenType,Long appId){
        if(codeGenType == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenType);

        return switch (codeGenType){
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream,CodeGenTypeEnum.HTML,appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream,CodeGenTypeEnum.MULTI_FILE,appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream,appId);
            }
            default -> {
                String errorMessage ="不支持的生成类型：" + codeGenType.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,errorMessage);
            }
        };

    }

    /**
     * 适配器（tokenStream -> Flux<String>）
     * @param tokenStream
     * @param appId
     * @return
     */
    private Flux<String> processTokenStream(TokenStream tokenStream,Long appId) {
        return Flux.create(sink->{
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        sink.complete();
                    })

                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }


    /**
     * 通用流式代码处理方法
     * @param appId
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType,Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType,appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }




//    /**
//     * 生成HTML模式的代码并保存
//     * @param userMessage
//     * @return
//     */
//
//    private File generatorAndSaveHtmlCode(String userMessage) {
//        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
//        return CodeFileSaver.saveHtmlCodeResult(result);
//    }
//
//    /**
//     * 生成多文件模式的代码并保存
//     * @param userMessage
//     * @return
//     */
//
//    private File generatorAndSaveMultiFileCode(String userMessage) {
//        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
//        return CodeFileSaver.saveMultiFileCodeResult(result);
//    }

//    /**
//     * 生成HTML模式的代码并保存(流式)
//     * @param userMessage
//     * @return
//     */
//    private Flux<String> generatorAndSaveHtmlCodeStream(String userMessage) {
//        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
//        //当流式返回生成的代码完成后再保存
//        StringBuffer codeBuilder = new StringBuffer();
//        return result
//                .doOnNext(chunk ->{
//                    //实时收集代码片段
//                    codeBuilder.append(chunk);
//                }).doOnComplete(() ->{
//                    //流式返回完成后保存代码
//                    try{
//                        String completeHtmlCode = codeBuilder.toString();
//                        HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(completeHtmlCode);
//                        //保存代码到文件
//                        File savedDir = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
//                        log.info("保存成功，路径为: " + savedDir.getAbsolutePath());
//                    }catch (Exception e){
//                        log.error("保存失败:{}",e.getMessage());
//                    }
//                });
//
//    }
//
//    /**
//     * 生成MultiFile模式的代码并保存(流式)
//     * @param userMessage
//     * @return
//     */
//    private Flux<String> generatorAndSaveMultiFileCodeStream(String userMessage) {
//        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
//        //当流式返回生成的代码完成后再保存
//        StringBuffer codeBuilder = new StringBuffer();
//        return result
//                .doOnNext(chunk ->{
//                    //实时收集代码片段
//                    codeBuilder.append(chunk);
//                }).doOnComplete(() ->{
//                    //流式返回完成后保存代码
//                    try{
//                        String completeMultiFileCode = codeBuilder.toString();
//                        MultiFileCodeResult multiFileCodeResult = CodeParser.parseMultiFileCode(completeMultiFileCode);
//                        //保存代码到文件
//                        File savedDir = CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
//                        log.info("保存成功，路径为: " + savedDir.getAbsolutePath());
//                    }catch (Exception e){
//                        log.error("保存失败:{}",e.getMessage());
//                    }
//                });
//    }

}
