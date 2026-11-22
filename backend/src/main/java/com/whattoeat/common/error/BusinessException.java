package com.whattoeat.common.error;

/**
 * 서비스 규칙을 어겼을 때 던지는 예외. {@link GlobalExceptionHandler}가 에러 코드에 맞는 응답으로 바꾼다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}

}
