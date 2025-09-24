package com.luckyh.cloud.common.exception;

import com.luckyh.cloud.common.result.ResultCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统异常
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SystemException extends RuntimeException {
    
    private Integer code;
    private String message;
    
    public SystemException() {
        super();
    }
    
    public SystemException(String message) {
        super(message);
        this.code = ResultCode.SYSTEM_ERROR.getCode();
        this.message = message;
    }
    
    public SystemException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    public SystemException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }
    
    public SystemException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.message = message;
    }
    
    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.SYSTEM_ERROR.getCode();
        this.message = message;
    }
    
    public SystemException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }
}
