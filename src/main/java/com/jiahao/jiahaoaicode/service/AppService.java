package com.jiahao.jiahaoaicode.service;

import com.jiahao.jiahaoaicode.model.dto.AppAddRequest;
import com.jiahao.jiahaoaicode.model.dto.AppQueryRequest;
import com.jiahao.jiahaoaicode.model.entity.User;
import com.jiahao.jiahaoaicode.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.jiahao.jiahaoaicode.model.entity.App;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author jiahao
 * @since 2025-11-13
 */
public interface AppService extends IService<App> {

    /**
     * 获取脱敏后的APP信息
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 构造查询条件

 * 根据AppQueryRequest对象构建QueryWrapper，用于数据库查询条件的组装
     * @param appQueryRequest 包含查询条件的请求对象，用于构建查询条件
     * @return QueryWrapper 返回组装好的查询条件对象，可用于MyBatis-Plus的查询操作
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取脱敏后的APP信息列表
     * @param appList
     * @return
     */

    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 通过对话生成应用代码
     * @param appId
     * @param meesage
     * @param loginUser
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String meesage, User loginUser);

    /**
     *
     * @param appId
     * @param loginUser
     * @return
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 异步生成应用截图并更新数据库中的Cover字段
     * @param appId
     * @param appUrl
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 创建app
     * @param appAddRequest
     * @param loginUser
     * @return
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);
}
