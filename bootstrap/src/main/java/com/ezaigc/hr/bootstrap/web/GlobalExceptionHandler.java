package com.ezaigc.hr.bootstrap.web;

import com.ezaigc.hr.common.infrastructure.logging.SafeLogException;
import com.ezaigc.hr.common.web.error.FieldViolation;
import com.ezaigc.hr.common.web.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 在框架构造和绑定前检查字符串列表索引；保留业务异常及其他参数的原处理路径。 */
    @InitBinder
    void validateStringListIndexes(WebDataBinder binder, HttpServletRequest request) {
        Class<?> targetType = binder.getTarget() != null ? binder.getTarget().getClass()
                : binder.getTargetType() == null ? null : binder.getTargetType().resolve();
        if (targetType == null || !(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler)
                || Arrays.stream(handler.getMethodParameters()).noneMatch(parameter ->
                        parameter.hasParameterAnnotation(ModelAttribute.class) && parameter.getParameterType() == targetType)) return;
        request.getParameterNames().asIterator().forEachRemaining(parameterName -> {
            // 数组式参数由 WebDataBinder 原生转换或忽略，必须判断原始名称。
            if (parameterName.endsWith("[]")) return;
            String name = PropertyAccessorUtils.canonicalPropertyName(parameterName);
            int opening = name.indexOf('[');
            if (opening < 0) return;
            var property = BeanUtils.getPropertyDescriptor(targetType, name.substring(0, opening));
            if (property == null || !List.class.isAssignableFrom(property.getPropertyType())
                    || property.getReadMethod() == null
                    || !(property.getReadMethod().getGenericReturnType() instanceof ParameterizedType listType)
                    || listType.getActualTypeArguments()[0] != String.class) return;
            boolean valid;
            try {
                int index = name.endsWith("]") ? Integer.parseInt(name.substring(opening + 1, name.length() - 1)) : -1;
                valid = index >= 0 && index < binder.getAutoGrowCollectionLimit();
            } catch (NumberFormatException exception) {
                valid = false;
            }
            if (!valid) throw new PlatformException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求列表索引无效", true,
                    List.of(new FieldViolation(property.getName(), "列表索引必须在允许范围内")));
        });
    }

    /**
     * 处理平台异常。
     */
    @ExceptionHandler(PlatformException.class)
    ProblemDetail handlePlatformException(PlatformException exception, HttpServletRequest request) {
        if (exception.status().is5xxServerError()) {
            LOGGER.error(
                    "Platform request failure code={} path={} traceId={}",
                    exception.code(),
                    logRoute(request),
                    traceId(),
                    SafeLogException.from(exception));
        }
        return problem(
                exception.status().value(),
                exception.code(),
                exception.getMessage(),
                exception.recoverable(),
                exception.fieldErrors(),
                request);
    }

    /**
     * 处理校验。
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return new ResponseEntity<>(problem(400, "VALIDATION_FAILED", "请求字段校验失败", true, violations,
                servletRequest(request)), headers, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return problem(400, "VALIDATION_FAILED", "请求参数校验失败", true, violations, request);
    }

    /**
     * 处理 Spring MVC 7 对请求参数、路径参数和请求头执行方法校验时抛出的异常。
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // 返回值校验失败是服务端缺陷，不能降级为客户端400或暴露内部输出。
        if (exception.isForReturnValue()) return handleExceptionInternal(exception, null, headers, status, request);
        List<FieldViolation> violations = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String field = parameterName(result);
            result.getResolvableErrors().stream()
                    .map(error -> new FieldViolation(
                            error instanceof FieldError fieldError ? fieldError.getField() : field,
                            validationMessage(error)))
                    .forEach(violations::add);
        }
        exception.getCrossParameterValidationResults().stream()
                .map(error -> new FieldViolation("request", validationMessage(error)))
                .forEach(violations::add);
        return new ResponseEntity<>(problem(400, "VALIDATION_FAILED", "请求参数校验失败", true, violations,
                servletRequest(request)), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(problem(400, "REQUEST_BODY_INVALID", "请求正文无法解析", true, List.of(),
                servletRequest(request)), headers, status);
    }

    /** 接管 Boot 默认处理器的装配位置；保留框架状态/响应头，正文仅使用不含输入值的固定描述。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        HttpServletRequest servlet = servletRequest(request);
        // JSON 转换器会包装客户端断连；连接不可用时不能再生成错误正文。
        if (clientDisconnected(exception, servlet)) return null;
        String code = switch (status.value()) {
            case 400 -> "VALIDATION_FAILED";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 413 -> "FILE_REJECTED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 503 -> "EXTERNAL_DEPENDENCY_UNAVAILABLE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
        String detail = switch (status.value()) {
            case 400 -> "请求参数缺失或无效";
            case 404 -> "请求资源不存在";
            case 405 -> "请求方法不受支持";
            case 406 -> "无法提供请求的响应格式";
            case 413 -> "上传内容超过允许大小";
            case 415 -> "请求内容格式不受支持";
            default -> status.is5xxServerError() ? "系统暂时无法完成请求" : "请求无法处理";
        };
        List<FieldViolation> violations = switch (exception) {
            case MissingRequestHeaderException missing -> List.of(new FieldViolation(missing.getHeaderName(), "请求头不能为空"));
            case MissingServletRequestParameterException missing -> List.of(new FieldViolation(missing.getParameterName(), "请求参数不能为空"));
            case MissingServletRequestPartException missing -> List.of(new FieldViolation(missing.getRequestPartName(), "请求部分不能为空"));
            default -> List.of();
        };
        if (status.is5xxServerError()) {
            LOGGER.error("Framework request failure code={} path={} traceId={}", code, logRoute(servlet), traceId(),
                    SafeLogException.from(exception));
        }
        // 已提交的真实错误仍保留上面的脱敏诊断，避免父类再次记录原始异常消息。
        if (request instanceof ServletWebRequest servletWebRequest
                && servletWebRequest.getResponse() != null && servletWebRequest.getResponse().isCommitted()) {
            return null;
        }
        return super.handleExceptionInternal(exception,
                problem(status.value(), code, detail, true, violations, servlet), headers, status, request);
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }

    /** 请求参数转换失败属于输入错误，不输出可能含个人信息的原始值或异常消息。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", "请求参数格式无效", true,
                List.of(new FieldViolation(exception.getName(), "参数格式无效")), request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        if (clientDisconnected(exception, request)) return null;
        LOGGER.error("Unexpected request failure path={} traceId={}", logRoute(request), traceId(),
                SafeLogException.from(exception));
        return problem(500, "INTERNAL_ERROR", "系统暂时无法完成请求", true, List.of(), request);
    }

    /** 日志只记录框架匹配的路由模板，避免路径中的下载凭证和个人信息泄漏。 */
    private static String logRoute(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern instanceof String route && !route.isBlank() ? route : "unmapped";
    }

    private static boolean clientDisconnected(Exception exception, HttpServletRequest request) {
        if (!DisconnectedClientHelper.isClientDisconnectedException(exception)) return false;
        LOGGER.debug("Client disconnected path={} traceId={}", logRoute(request), traceId());
        return true;
    }

    private static ProblemDetail problem(
            int status,
            String code,
            String detail,
            boolean recoverable,
            List<FieldViolation> fieldErrors,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        problem.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
        problem.setType(URI.create("urn:vgo:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("traceId", traceId());
        problem.setProperty("recoverable", recoverable);
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return problem;
    }

    private static String traceId() {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        return traceId == null ? "unavailable" : traceId;
    }

    private static String parameterName(ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        return name == null ? "arg" + result.getMethodParameter().getParameterIndex() : name;
    }

    private static String validationMessage(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? "参数校验失败" : message;
    }
}
