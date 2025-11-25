package com.jiahao.jiahaoaicode.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * APP创建请求
 */
@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    private static final long serialVersionUID = 1L;
}
