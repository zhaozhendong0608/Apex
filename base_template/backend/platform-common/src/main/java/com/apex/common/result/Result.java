package com.apex.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 RESTful API 响应实体
 *
 * @param <T> 业务数据泛型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码 (200: 成功, 其他: 异常)
     */
    private Integer code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 数据对象
     */
    private T data;

    /**
     * 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功响应 (带自定义消息)
     */
    public static <T> Result<T> success(T data, String message) {
        return new Result<>(200, message, data);
    }

    /**
     * 成功响应 (无数据)
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 失败响应
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(400, message, null);
    }

    /**
     * 失败响应 (自定义状态码)
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
