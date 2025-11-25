package com.jiahao.jiahaoaicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.jiahao.jiahaoaicode.ai.AiCodeGenTypeRoutingService;
import com.jiahao.jiahaoaicode.ai.AiCodeGenTypeRoutingServiceFactory;
import com.jiahao.jiahaoaicode.constant.AppConstant;
import com.jiahao.jiahaoaicode.core.AiCodeGeneratorFacade;
import com.jiahao.jiahaoaicode.core.builder.VueProjectBuilder;
import com.jiahao.jiahaoaicode.core.handler.StreamHandlerExecutor;
import com.jiahao.jiahaoaicode.exception.BusinessException;
import com.jiahao.jiahaoaicode.exception.ErrorCode;
import com.jiahao.jiahaoaicode.exception.ThrowUtils;
import com.jiahao.jiahaoaicode.mapper.AppMapper;
import com.jiahao.jiahaoaicode.model.dto.AppAddRequest;
import com.jiahao.jiahaoaicode.model.dto.AppQueryRequest;
import com.jiahao.jiahaoaicode.model.entity.App;
import com.jiahao.jiahaoaicode.model.entity.User;
import com.jiahao.jiahaoaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.jiahao.jiahaoaicode.model.enums.CodeGenTypeEnum;
import com.jiahao.jiahaoaicode.model.vo.AppVO;
import com.jiahao.jiahaoaicode.model.vo.UserVO;
import com.jiahao.jiahaoaicode.monitor.MonitorContext;
import com.jiahao.jiahaoaicode.monitor.MonitorContextHolder;
import com.jiahao.jiahaoaicode.service.AppService;
import com.jiahao.jiahaoaicode.service.ChatHistoryService;
import com.jiahao.jiahaoaicode.service.ScreenshotService;
import com.jiahao.jiahaoaicode.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author jiahao
 * @since 2025-11-13
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{
    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;



    @Resource
    private UserService userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

//    @Resource
//    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;
    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String meesage, User loginUser) {
        //1、参数校验
        ThrowUtils.throwIf(appId == null || appId < 0,ErrorCode.PARAMS_ERROR,"应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(meesage),ErrorCode.PARAMS_ERROR,"用户消息不能为空");
        //2、查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null,ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        //3、验证用户是否有权限访问该应用，仅本人可以生成代码
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()),ErrorCode.NO_AUTH_ERROR,"无权限访问该应用");
        //4、获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        ThrowUtils.throwIf(codeGenTypeEnum == null ,ErrorCode.SYSTEM_ERROR,"不支持的代码生成类型");
        //5、添加用户对话消息到对话历史中
        chatHistoryService.addChatMessage(appId,meesage, ChatHistoryMessageTypeEnum.USER.getValue(),loginUser.getId());
        // 6. 设置监控上下文
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .userId(loginUser.getId().toString())
                        .appId(appId.toString())
                        .build());
        //7、调用AI生成代码
        Flux<String> codeStream = aiCodeGeneratorFacade.generatorAndSaveCodeStream(meesage, codeGenTypeEnum, appId);
        // 8. 收集AI响应内容,并在完成后记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doFinally(signalType -> {
                    //流结束后清理（无论成功失败与否）
                    MonitorContextHolder.clearContext();
                });
    }





    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // Vue项目特殊处理：重新构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if(codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT){
            //重新构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess,ErrorCode.SYSTEM_ERROR,"Vue项目构建失败，请重试");
            //检查dist目录是否存在
            File distFile = new File(sourceDirPath,"dist");
            ThrowUtils.throwIf(!distFile.exists(),ErrorCode.SYSTEM_ERROR,"dist目录为空");
            //更新Vue项目的原路径（因为Vue项目部署时要用的是构建后的Dist目录下的文件，而不需要src文件）
            sourceDir = distFile;
            log.info("Vue项目构建成功，将部署Dist目录：{}",sourceDir.getAbsolutePath());
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 9. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 得到可访问的 URL
//        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
        // 11. 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId,appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 异步生成应用截图并更新数据库中的Cover字段
     * @param appId
     * @param appUrl
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl){
        //使用虚拟线程异步执行
        Thread.startVirtualThread(()->{
            //调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            //更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated,ErrorCode.OPERATION_ERROR,"更新应用封面字段失败");

        });
    }

    /**
     * 删除应用时，同时删除对话历史（重写原本的removeById方法）
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id){
        if(id ==null){
            return false;
        }
        Long appId = Long.valueOf(id.toString());
        if(appId<0){
            return false;
        }
        try {
            //删除相关的对话历史
            chatHistoryService.deleteByAppId(appId);
        }catch (Exception e){
            //记录日志但不阻止应用删除
            log.error("删除应用时历史对话删除失败：" +e.getMessage());
        }
        //调用原本的方法
        return super.removeById(appId);
    }



    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型
        AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }




}
