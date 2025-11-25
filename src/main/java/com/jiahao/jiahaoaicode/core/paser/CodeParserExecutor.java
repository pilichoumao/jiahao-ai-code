package com.jiahao.jiahaoaicode.core.paser;

import com.jiahao.jiahaoaicode.exception.BusinessException;
import com.jiahao.jiahaoaicode.exception.ErrorCode;
import com.jiahao.jiahaoaicode.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器
 * 根据代码生成的类型执行相应的解析逻辑
 */
public class CodeParserExecutor {

    private static final HtmlCodeParser htmlCodeParser =new HtmlCodeParser();

    private static  final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /**
     * 实行代码解析
     * @param codeContent 代码内容
     * @param codeGenTypeEnum 生成类型
     * @return 解析结果（HtmlCodeResult或者MultiFileCodeResult）
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenTypeEnum){
        return switch (codeGenTypeEnum){
            case HTML ->htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码生成类型: "+codeGenTypeEnum);
        };
    }
}
