package com.jiahao.jiahaoaicode.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {
    /**
     * 下载项目压缩包通用方法
     * @param projectPath 要打包的项目路径
     * @param downloadFileName 打包之后的文件名
     * @param response
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
