package com.jiahao.jiahaoaicode.core;

import com.jiahao.jiahaoaicode.ai.AiCodeGeneratorService;
import com.jiahao.jiahaoaicode.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class AiCodeGeneratorFacadeTest {
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generatorAndSaveCode() {
        File file = aiCodeGeneratorFacade.generatorAndSaveCode("生成一个个人主页,少于50行代码", CodeGenTypeEnum.MULTI_FILE,1L);
        Assertions.assertNotNull(file);
    }

    @Test
    void generatorAndSaveCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generatorAndSaveCodeStream("生成一个个人主页,少于50行代码", CodeGenTypeEnum.MULTI_FILE,1L);
        //阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);

    }
    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generatorAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeGenTypeEnum.VUE_PROJECT, 1L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }

}