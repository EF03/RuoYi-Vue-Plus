package com.ruoyi.common.excel;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.alibaba.fastjson.JSON;
import com.ruoyi.common.utils.ValidatorUtils;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Excel 导入监听
 *
 * @author Yjoioooo
 * @author Lion Li
 */
@Slf4j
@NoArgsConstructor
public class DefaultExcelListener<T> extends AnalysisEventListener<T> {

    /**
     * 是否Validator检验，默认为是
     */
    private Boolean isValidate = Boolean.TRUE;

    /**
     * 导入回执
     */
    private ExcelResult<T> excelResult;

    public DefaultExcelListener(boolean isValidate) {
        this.excelResult = new DefautExcelResult<>();
        this.isValidate = isValidate;
    }

    /**
     * 处理异常
     *
     * @param exception ExcelDataConvertException
     * @param context   Excel 上下文
     */
    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        String errMsg = null;
        if (exception instanceof ExcelDataConvertException) {
            // 如果是某一个单元格的转换异常 能获取到具体行号
            // 如果要获取头的信息 配合doAfterAllAnalysedHeadMap使用
            ExcelDataConvertException excelDataConvertException = (ExcelDataConvertException) exception;
            errMsg = StrUtil.format("第{}行-第{}列解析异常<br/>",
                excelDataConvertException.getRowIndex() + 1,
                excelDataConvertException.getColumnIndex() + 1);
            if (log.isDebugEnabled()) {
                log.error(errMsg);
            }
        }
        if (exception instanceof ConstraintViolationException) {
            ConstraintViolationException constraintViolationException = (ConstraintViolationException) exception;
            Set<ConstraintViolation<?>> constraintViolations = constraintViolationException.getConstraintViolations();
            String constraintViolationsMsg = constraintViolations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
            errMsg = StrUtil.format("第{}行数据校验异常: {}", context.readRowHolder().getRowIndex() + 1, constraintViolationsMsg);
            if (log.isDebugEnabled()) {
                log.error(errMsg);
            }
        }
        excelResult.getErrorList().add(errMsg);
        throw new ExcelAnalysisException(errMsg);
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        log.debug("解析到一条头数据: {}", JSON.toJSONString(headMap));
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        if (isValidate) {
            ValidatorUtils.validate(data);
        }
        excelResult.getList().add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.debug("所有数据解析完成！");
    }

    public ExcelResult<T> getExcelResult() {
        return excelResult;
    }

}