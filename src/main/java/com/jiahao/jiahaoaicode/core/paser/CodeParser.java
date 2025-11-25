package com.jiahao.jiahaoaicode.core.paser;

/**
 * 代码解析器策略接口
 * 
 * @author yupi
 */
public interface CodeParser<T> {

    /**
     * 解析代码内容
     * 
     * @param codeContent 原始代码内容
     * @return 解析后的结果对象
     */
    //返回值是一个范型
    T parseCode(String codeContent);
}
