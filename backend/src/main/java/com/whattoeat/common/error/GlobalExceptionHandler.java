package com.whattoeat.common.error;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 에러 응답을 Spring 표준 형식(ProblemDetail, RFC 9457)으로 통일하고, 프론트가 구분할 수 있게 code를 붙인다.
 * 예: {"status": 404, "title": "Not Found", "detail": "방을 찾을 수 없습니다.", "code": "ROOM_NOT_FOUND"}
 * 여기서 다루지 않는 에러는 Spring Boot 기본 처리기가 같은 형식(code 없음)으로 응답한다.
 */
// Spring Boot 기본 처리기도 HttpMessageNotReadableException을 다루므로, 이 처리기가 먼저 잡도록 순서를 앞에 둔다.
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ProblemDetail> handleBusiness(BusinessException e) {
		return toResponse(e.errorCode());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException e) {
		return toResponse(ErrorCode.INVALID_REQUEST);
	}

	private ResponseEntity<ProblemDetail> toResponse(ErrorCode errorCode) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), errorCode.message());
		problem.setProperty("code", errorCode.name());
		return ResponseEntity.status(errorCode.status()).body(problem);
	}

}
