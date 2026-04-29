package tup.tally.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理所有异常
     */
    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception e) {
        // 记录详细错误日志
        logger.error("系统异常：", e);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", e.getMessage() != null ? e.getMessage() : "系统内部错误");
        result.put("error", e.getClass().getSimpleName());

        // 提取 stackTrace 中的 className 和 lineNumber
        StackTraceElement[] stackTrace = e.getStackTrace();
        String[] simplifiedStack = new String[stackTrace.length];
        for (int i = 0; i < stackTrace.length; i++) {
            simplifiedStack[i] = stackTrace[i].getClassName() + ":" + stackTrace[i].getLineNumber();
        }
        result.put("stacktrace", simplifiedStack);

        return result;
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Map<String, Object> handleRuntimeException(RuntimeException e) {
        // 记录详细错误日志
        logger.error("运行时异常：", e);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", e.getClass().getSimpleName());
        result.put("message", e.getMessage() != null ? e.getMessage() : "运行时错误");
        return result;
    }

}
