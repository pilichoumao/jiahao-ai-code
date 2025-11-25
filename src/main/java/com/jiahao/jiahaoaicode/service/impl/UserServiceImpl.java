package com.jiahao.jiahaoaicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.jiahao.jiahaoaicode.exception.BusinessException;
import com.jiahao.jiahaoaicode.exception.ErrorCode;
import com.jiahao.jiahaoaicode.model.dto.UserQueryRequest;
import com.jiahao.jiahaoaicode.model.enums.UserRoleEnum;
import com.jiahao.jiahaoaicode.model.vo.LoginUserVO;
import com.jiahao.jiahaoaicode.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.jiahao.jiahaoaicode.model.entity.User;
import com.jiahao.jiahaoaicode.mapper.UserMapper;
import com.jiahao.jiahaoaicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.jiahao.jiahaoaicode.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author jiahao
 * @since 2025-11-11
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{


    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1、校验参数
        if(StrUtil.hasBlank(userAccount,userPassword,checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if(!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次输入的密码不一致");
        }
        if(userPassword.length()<8 ||checkPassword.length()<8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码长度过短");
        }
        if(userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号长度过短");
        }

        //2、查询用户是否存在
        QueryWrapper queryWrapper =new QueryWrapper();
        queryWrapper.eq("userAccount",userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if(count>0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号已存在");
        }
        //3、密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        //4、创建用户，加入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("未设置用户名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if(!saveResult){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"数据库更新失败");
        }

        return user.getId();
    }

    @Override
    public String getEncryptPassword(String uesrPassword){
        //盐值加密
        final String SALT = "jiahao";
        return DigestUtils.md5DigestAsHex((uesrPassword+SALT).getBytes(StandardCharsets.UTF_8));

    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if(user==null){
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user,loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1、校验参数
        if(StrUtil.hasBlank(userAccount,userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"输入为空");
        }
        if(userAccount.length()<4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号过短");
        }
        if(userPassword.length()<8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码过短");
        }
        //2、加密
        String encryptPassword = getEncryptPassword(userPassword);
        //3、查询用户是否存在
        QueryWrapper queryWrapper =new QueryWrapper();
        queryWrapper.eq("userAccount",userAccount)
                .eq("userPassword",encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if(user == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名或密码错误");
        }
        //4、如果用户存在，记录用户的登录状态
        request.getSession().setAttribute(USER_LOGIN_STATE,user);
        //5、返回脱敏后的用户信息
        return getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        //判断用户是否登录
        Object objest = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) objest;
        if(currentUser==null || currentUser.getId()==null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"未登录");
        }
        //从数据库获取用户最新的信息，避免不同步的问题
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("id",currentUser.getId());
        User user = this.mapper.selectOneByQuery(queryWrapper);
        //返回用户信息
        return user;
    }

    @Override
    public UserVO getUserVO(User user) {
        if(user==null){
            return null;
        }
        UserVO UserVO = new UserVO();
        BeanUtil.copyProperties(user,UserVO);
        return UserVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if(CollUtil.isEmpty(userList)){
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        //判断用户是否登录
        Object objest = request.getSession().getAttribute(USER_LOGIN_STATE);
        if(objest==null ){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"用户未登录");
        }
        //移除用户登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }



}
