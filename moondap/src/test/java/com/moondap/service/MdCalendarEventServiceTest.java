package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.moondap.common.exception.UserMessageException;
import com.moondap.dto.MdCalendarEventDTO;
import com.moondap.mapper.MdCalendarEventMapper;

/**
 * 캘린더 일정 생성/수정 검증.
 *
 * <p>종료가 시작보다 빠른 일정이 저장되면 캘린더 화면이 깨지거나(음수 기간) 혼란을
 * 준다. 또한 작성자는 폼이 아니라 로그인 계정으로만 정해져야 한다 — 그렇지 않으면
 * 다른 관리자 이름으로 일정을 등록할 수 있게 된다.
 */
@ExtendWith(MockitoExtension.class)
class MdCalendarEventServiceTest {

    @Mock
    private MdCalendarEventMapper calendarEventMapper;

    private MdCalendarEventService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new MdCalendarEventService(calendarEventMapper);
    }

    @Test
    @DisplayName("종료가 시작보다 빠르면 생성할 수 없다")
    void createEventRejectsInvalidPeriod() {
        MdCalendarEventDTO event = new MdCalendarEventDTO();
        event.setTitle("회의");
        event.setStartAt(LocalDate.of(2026, 1, 10));
        event.setEndAt(LocalDate.of(2026, 1, 9));

        assertThatThrownBy(() -> service.createEvent(event, "mdadmin"))
                .isInstanceOf(UserMessageException.class);

        verify(calendarEventMapper, never()).insertEvent(any());
    }

    @Test
    @DisplayName("생성 시 작성자는 호출부가 넘긴 로그인 계정으로 채워진다")
    void createEventSetsAuthorFromCaller() {
        MdCalendarEventDTO event = new MdCalendarEventDTO();
        event.setTitle("회의");
        event.setStartAt(LocalDate.of(2026, 1, 10));
        event.setEndAt(LocalDate.of(2026, 1, 10));

        service.createEvent(event, "mdadmin");

        assertThat(event.getAuthorUsername()).isEqualTo("mdadmin");
        verify(calendarEventMapper).insertEvent(event);
    }

    @Test
    @DisplayName("수정 시 작성자는 바뀌지 않는다")
    void updateEventDoesNotChangeAuthor() {
        MdCalendarEventDTO event = new MdCalendarEventDTO();
        event.setId(1L);
        event.setTitle("회의 변경");
        event.setStartAt(LocalDate.of(2026, 1, 10));
        event.setEndAt(LocalDate.of(2026, 1, 10));
        event.setAuthorUsername("mdadmin");

        service.updateEvent(event);

        assertThat(event.getAuthorUsername()).isEqualTo("mdadmin");
        verify(calendarEventMapper).updateEvent(event);
    }

    @Test
    @DisplayName("공통 일정/완료 체크박스를 안 누르면 null 대신 false 로 저장된다")
    void createEventNormalizesNullCheckboxesToFalse() {
        MdCalendarEventDTO event = new MdCalendarEventDTO();
        event.setTitle("회의");
        event.setStartAt(LocalDate.of(2026, 1, 10));
        event.setEndAt(LocalDate.of(2026, 1, 10));
        // shared/completed 를 세팅하지 않음 — 체크박스를 안 누르면 폼에서 null 로 바인딩되는 상황 재현.

        service.createEvent(event, "mdadmin");

        // 두 컬럼 다 NOT NULL 이라 null 그대로 넘기면 insert 가 실패한다.
        assertThat(event.getShared()).isFalse();
        assertThat(event.getCompleted()).isFalse();
    }

    @Test
    @DisplayName("공통 일정으로 체크하면 그 값 그대로 저장된다")
    void updateEventKeepsSharedTrue() {
        MdCalendarEventDTO event = new MdCalendarEventDTO();
        event.setId(1L);
        event.setTitle("같이 보는 일정");
        event.setStartAt(LocalDate.of(2026, 1, 10));
        event.setEndAt(LocalDate.of(2026, 1, 10));
        event.setShared(true);

        service.updateEvent(event);

        assertThat(event.getShared()).isTrue();
        verify(calendarEventMapper).updateEvent(event);
    }

    @Test
    @DisplayName("수정 폼에서 완료를 체크하면 그 값 그대로 저장된다")
    void updateEventKeepsCompletedTrue() {
        MdCalendarEventDTO event = new MdCalendarEventDTO();
        event.setId(1L);
        event.setTitle("다 끝낸 일정");
        event.setStartAt(LocalDate.of(2026, 1, 10));
        event.setEndAt(LocalDate.of(2026, 1, 10));
        event.setCompleted(true);

        service.updateEvent(event);

        assertThat(event.getCompleted()).isTrue();
        verify(calendarEventMapper).updateEvent(event);
    }

    @Test
    @DisplayName("드래그로 옮긴 기간도 종료가 시작보다 빠르면 거부한다")
    void rescheduleEventRejectsInvalidPeriod() {
        assertThatThrownBy(() -> service.rescheduleEvent(1L, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 9)))
                .isInstanceOf(UserMessageException.class);

        verify(calendarEventMapper, never()).updateSchedule(any(), any(), any());
    }

    @Test
    @DisplayName("드래그/리사이즈로 옮긴 기간은 제목/설명/색상을 건드리지 않고 날짜만 바꾼다")
    void rescheduleEventUpdatesOnlyDates() {
        LocalDate start = LocalDate.of(2026, 1, 10);
        LocalDate end = LocalDate.of(2026, 1, 12);

        service.rescheduleEvent(1L, start, end);

        verify(calendarEventMapper).updateSchedule(1L, start, end);
        verify(calendarEventMapper, never()).updateEvent(any());
    }
}
