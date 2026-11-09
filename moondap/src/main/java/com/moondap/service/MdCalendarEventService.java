package com.moondap.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moondap.common.exception.UserMessageException;
import com.moondap.dto.MdCalendarEventDTO;
import com.moondap.mapper.MdCalendarEventMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MdCalendarEventService {

    private final MdCalendarEventMapper calendarEventMapper;

    public List<MdCalendarEventDTO> getAllEvents() {
        return calendarEventMapper.selectAllEvents();
    }

    /**
     * authorUsername 은 호출부(컨트롤러)가 로그인 계정에서 가져와 넘긴다.
     * 폼으로 들어온 값을 그대로 쓰면 다른 관리자 이름으로 등록할 수 있게 된다.
     */
    @Transactional
    public void createEvent(MdCalendarEventDTO event, String authorUsername) {
        validatePeriod(event);
        event.setAuthorUsername(authorUsername);
        normalizeCheckboxes(event);
        calendarEventMapper.insertEvent(event);
    }

    /**
     * authorUsername 은 건드리지 않는다. 일정 색은 "누가 만들었나" 기준이라,
     * 다른 관리자가 수정했다고 해서 색이 바뀌면 안 된다.
     */
    @Transactional
    public void updateEvent(MdCalendarEventDTO event) {
        validatePeriod(event);
        normalizeCheckboxes(event);
        calendarEventMapper.updateEvent(event);
    }

    @Transactional
    public void deleteEvent(Long id) {
        calendarEventMapper.deleteEvent(id);
    }

    @Transactional
    public void setCompleted(Long id, boolean completed) {
        calendarEventMapper.updateCompleted(id, completed);
    }

    /**
     * 캘린더에서 드래그로 옮기거나 기간을 늘인/줄인 결과를 저장한다.
     * 제목/설명/색상은 건드리지 않는다 — updateEvent 를 재사용하면 그 폼에 없는
     * 값들이 빈 값으로 덮어써진다.
     */
    @Transactional
    public void rescheduleEvent(Long id, LocalDate startAt, LocalDate endAt) {
        validatePeriod(startAt, endAt);
        calendarEventMapper.updateSchedule(id, startAt, endAt);
    }

    /**
     * 체크박스를 안 누르면 폼에 그 필드 자체가 안 실려와 null 로 바인딩된다.
     * shared/completed 둘 다 컬럼이 NOT NULL 이라 null 그대로 넘기면 insert/update 가 실패한다.
     */
    private void normalizeCheckboxes(MdCalendarEventDTO event) {
        event.setShared(Boolean.TRUE.equals(event.getShared()));
        event.setCompleted(Boolean.TRUE.equals(event.getCompleted()));
    }

    private void validatePeriod(MdCalendarEventDTO event) {
        validatePeriod(event.getStartAt(), event.getEndAt());
    }

    private void validatePeriod(LocalDate startAt, LocalDate endAt) {
        if (startAt == null || endAt == null) {
            throw new UserMessageException("시작/종료 날짜를 입력해 주세요.");
        }
        if (endAt.isBefore(startAt)) {
            throw new UserMessageException("종료일은 시작일보다 빠를 수 없습니다.");
        }
    }
}
