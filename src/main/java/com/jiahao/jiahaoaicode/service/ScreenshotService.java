package com.jiahao.jiahaoaicode.service;

/**
 * 截图服务
 */

public interface ScreenshotService {
    /**
     * 通用的截图服务，可以得到截图之后的访问地址
     * @param webUrl
     * @return
     */
    String generateAndUploadScreenshot(String webUrl);
}
