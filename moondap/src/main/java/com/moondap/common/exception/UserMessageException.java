package com.moondap.common.exception;

/**
 * 메시지를 사용자에게 그대로 보여줘도 되는 예외.
 *
 * <p>예: "이미 사용 중인 아이디입니다.", "댓글은 50자 이내로 입력 가능합니다."
 *
 * <p>이 타입이 필요한 이유는 예외 메시지 노출 정책 때문이다.
 * GlobalExceptionHandler 는 이 예외의 메시지만 응답에 싣고, 나머지 예외는
 * 일반 문구로 바꿔 내보낸다. 그렇지 않으면 MyBatis/JDBC 예외 메시지에 담긴
 * SQL 문과 테이블 이름이 그대로 사용자에게 노출된다.
 *
 * <p>즉 "사용자에게 보여줄 말"과 "로그에만 남길 말"을 타입으로 구분한다.
 */
public class UserMessageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UserMessageException(String message) {
        super(message);
    }

    public UserMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
