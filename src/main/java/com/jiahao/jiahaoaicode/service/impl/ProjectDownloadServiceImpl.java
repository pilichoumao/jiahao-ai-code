package com.jiahao.jiahaoaicode.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.jiahao.jiahaoaicode.exception.BusinessException;
import com.jiahao.jiahaoaicode.exception.ErrorCode;
import com.jiahao.jiahaoaicode.exception.ThrowUtils;
import com.jiahao.jiahaoaicode.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {
    /**
     * 需要过滤的文件和目录名称
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );

    /**
     * 下载项目压缩包通用方法
     * @param projectPath 要打包的项目路径
     * @param downloadFileName 打包之后的文件名
     * @param response HTTP响应
     */
    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        //基础校验
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR,"项目路径不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName),ErrorCode.PARAMS_ERROR,"下载文件名不能为空");
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(),ErrorCode.NOT_FOUND_ERROR,"项目目录不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(),ErrorCode.PARAMS_ERROR,"指定路径不是一个目录");
        log.info("开始打包下载项目：{} -> {}.zip",projectPath,downloadFileName);
        //设置HTTP响应头
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.addHeader("Content-Disposition",String.format("attachment; filename=\"%s.zip\"",downloadFileName));
        //定义文件过滤器
        FileFilter filter = file -> isPathAllowed(projectDir.toPath(),file.toPath());

        try {
            //使用Hutool的ZipUtil将过滤后的目录压缩到响应输出流（隐含了遍历projectDir下的所有文件，使用filter）
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8,false,filter,projectDir);
            log.info("项目打包下载完成：{}",downloadFileName);
        } catch (IOException e) {
            log.error("项目打包下载异常",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"项目打包下载失败");
        }

    }

    /**
     * 校验路径是否语序包含在压缩包中 （用来过滤某单个文件）
     * @param projectRoot 项目根目录
     * @param fullPath 完整路径
     * @return 是否允许
     */
    private boolean isPathAllowed(Path projectRoot , Path fullPath){
        //获取相对路径
        Path relativePath = projectRoot.relativize(fullPath);
        //检查路径中的每一部分
        for(Path part : relativePath){
            //获取文件名
            String partName = part.toString();
            //检查是否在过滤文件名中
            if(IGNORED_NAMES.contains(partName)){
                return false;
            }
            //检查文件扩展名
            if(IGNORED_EXTENSIONS.stream().anyMatch(partName::endsWith)){
                return false;
            }

        }
        return true;
    }

}