package com.moondap.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 관리자 공유 캘린더 일정.
 *
 * <p>시간은 다루지 않는다 — 몇 시에 했는지가 아니라 어느 날인지만 공유하면 되는
 * 용도라 start/end 를 LocalDate 로 둔다. end 는 DB/폼에서는 "마지막 날 포함"이지만,
 * FullCalendar 의 all-day 이벤트는 end 를 배타적(포함 안 함)으로 해석하므로
 * 화면으로 나가는 JSON(eventDataTransform)과 수정 폼에 다시 채울 때(eventClick)
 * 하루를 더하고 빼는 보정을 프런트에서 한다.
 *
 * <p>JSON 키는 {@code start}/{@code end} 로 내려간다({@code @JsonProperty}). FullCalendar 의
 * JSON 피드는 이 두 키만 날짜로 인식하고, 그 외 키는 전부 extendedProps 로 들어가 화면에
 * 배치되지 않는다. 자바 필드/폼 파라미터 이름은 그대로 startAt/endAt 을 쓴다(MyBatis 컬럼
 * 매핑과 date 폼 바인딩은 자바 프로퍼티 이름을 기준으로 하므로 영향이 없다).
 */
@Data
public class MdCalendarEventDTO {

    private Long id;

    private String title;

    private String description;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonProperty("start")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate startAt;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonProperty("end")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate endAt;

    // 작성한 관리자 계정. 클라이언트가 보낸 값을 쓰지 않고 서비스가 로그인 계정으로 채운다.
    private String authorUsername;

    // 일정별로 직접 지정한 색상(#rrggbb). NULL/빈 값이면 프런트가 작성자 해시 기반
    // 기본 색상으로 대체한다. FullCalendar 는 "color" 라는 키를 표준 필드로 인식해
    // backgroundColor/borderColor 양쪽에 그대로 적용하므로 이름을 바꾸지 않는다.
    private String color;

    // 완료 체크박스 상태. 수정 폼에도 체크박스가 있어 updateEvent/insertEvent 가 다룬다.
    // 캘린더 칸의 체크박스를 직접 클릭하는 가벼운 경로(updateCompleted 전용 엔드포인트)도
    // 모달을 열지 않고 바로 토글하려는 용도로 별도로 남아 있다 — 둘 다 유효한 저장 경로다.
    private Boolean completed;

    // 공통 일정 여부. authorUsername(누가 만들었나)은 그대로 두고, 화면 표시만
    // 작성자 색상/아바타 대신 중립 스타일로 바꾸는 데 쓴다. 생성/수정 폼에서
    // 체크박스로 받으므로 updateEvent/insertEvent 둘 다에서 다룬다.
    private Boolean shared;

    // md_user 를 조인해 채우는 조회 전용 값. 캘린더 화면에서 작성자 아바타를 보여주는 데만 쓴다.
    private String authorProfileImage;

    // md_user 를 조인해 채우는 조회 전용 값. 화면에 작성자를 username(로그인 id) 대신
    // 닉네임으로 보여주는 데만 쓴다.
    private String authorNickname;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
