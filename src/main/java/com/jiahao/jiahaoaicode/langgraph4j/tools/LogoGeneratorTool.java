//package com.jiahao.jiahaoaicode.langgraph4j.tools;
//
//import cn.hutool.core.util.StrUtil;
//import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
//import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
//import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
//import com.jiahao.jiahaoaicode.langgraph4j.model.ImageResource;
//import com.jiahao.jiahaoaicode.langgraph4j.model.enums.ImageCategoryEnum;
//import dev.langchain4j.agent.tool.P;
//import dev.langchain4j.agent.tool.Tool;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//@Slf4j
//@Component
//public class LogoGeneratorTool {
//
//    @Value("${dashscope.api-key:}")
//    private String dashScopeApiKey;
//
//    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
//    private String imageModel;
//
//    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
//    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
//        List<ImageResource> logoList = new ArrayList<>();
//        try {
//            // 构建 Logo 设计提示词
//            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
//            ImageSynthesisParam param = ImageSynthesisParam.builder()
//                    .apiKey(dashScopeApiKey)
//                    .model(imageModel)
//                    .prompt(logoPrompt)
//                    .size("512*512")
//                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
//                    .build();
//            ImageSynthesis imageSynthesis = new ImageSynthesis();
//            ImageSynthesisResult result = imageSynthesis.call(param);
//            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
//                List<Map<String, String>> results = result.getOutput().getResults();
//                for (Map<String, String> imageResult : results) {
//                    String imageUrl = imageResult.get("url");
//                    if (StrUtil.isNotBlank(imageUrl)) {
//                        logoList.add(ImageResource.builder()
//                                .category(ImageCategoryEnum.LOGO)
//                                .description(description)
//                                .url(imageUrl)
//                                .build());
//                    }
//                }
//            }
//        } catch (Exception e) {
//            log.error("生成 Logo 失败: {}", e.getMessage(), e);
//        }
//        return logoList;
//    }
//}
package com.jiahao.jiahaoaicode.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.jiahao.jiahaoaicode.langgraph4j.model.ImageResource;
import com.jiahao.jiahaoaicode.langgraph4j.model.enums.ImageCategoryEnum;
import com.jiahao.jiahaoaicode.manager.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class LogoGeneratorTool {

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Resource
    private CosManager cosManager;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size("512*512")
                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
                    .build();

            ImageSynthesis imageSynthesis = new ImageSynthesis();
            ImageSynthesisResult result = imageSynthesis.call(param);

            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
                List<Map<String, String>> results = result.getOutput().getResults();
                for (Map<String, String> imageResult : results) {
                    String imageUrl = imageResult.get("url");
                    if (StrUtil.isNotBlank(imageUrl)) {
                        // 1. 先把 DashScope 图片下载到本地临时文件
                        String cosUrl = downloadAndUploadToCos(imageUrl);

                        // 2. 使用 COS 地址替换原始地址
                        if (StrUtil.isNotBlank(cosUrl)) {
                            logoList.add(ImageResource.builder()
                                    .category(ImageCategoryEnum.LOGO)
                                    .description(description)
                                    .url(cosUrl)   // 使用 COS 的持久 URL
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }

    /**
     * 下载 DashScope 生成的图片到本地临时文件，然后上传到 COS，最后删除临时文件
     * 返回 COS 上的访问地址
     */
    private String downloadAndUploadToCos(String imageUrl) {
        File tempFile = null;
        try {
            // 1. 先去掉 URL 后面的查询参数（?后面的签名、过期时间等）
            String cleanUrl = imageUrl;
            int qIndex = cleanUrl.indexOf('?');
            if (qIndex >= 0) {
                cleanUrl = cleanUrl.substring(0, qIndex);
            }

            // 2. 再从“干净”的 URL 中解析扩展名
            String ext = cn.hutool.core.io.FileUtil.extName(cleanUrl);
            if (StrUtil.isBlank(ext)) {
                // DashScope 基本都是 png，你也可以按自己实际情况改成 jpg
                ext = "png";
            }

            // 3. 创建临时文件（注意这里用解析出来的扩展名）
            tempFile = FileUtil.createTempFile("logo_", "." + ext, true);

            // 4. 下载远程图片到本地临时文件
            //   Hutool 的 HttpUtil.downloadFile(url, destFile) 支持 destFile 是具体文件
            cn.hutool.http.HttpUtil.downloadFile(imageUrl, tempFile);

            if (!tempFile.exists() || tempFile.length() == 0) {
                log.warn("下载 Logo 图片失败，文件为空，url={}", imageUrl);
                return null;
            }

            // 5. 生成 COS 存储路径，例如：/logo/abcde/logo_xxx.png
            String keyName = String.format("/logo/%s/%s",
                    RandomUtil.randomString(5),
                    tempFile.getName());

            // 6. 上传到 COS
            String cosUrl = cosManager.uploadFile(keyName, tempFile);

            return cosUrl;
        } catch (Exception e) {
            log.error("下载并上传 Logo 到 COS 失败, url={}", imageUrl, e);
            return null;
        } finally {
            // 7. 删除本地临时文件
            if (tempFile != null && tempFile.exists()) {
                FileUtil.del(tempFile);
            }
        }
    }
}