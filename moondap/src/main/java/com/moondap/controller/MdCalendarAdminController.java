package com.moondap.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.MdCalendarEventDTO;
import com.moondap.service.MdCalendarEventService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 2계정이 공유하는 개인용 캘린더.
 *
 * <p>URL 이 {@code /admin/**} 이라 SecurityConfig 에서 이미 ADMIN 으로 막히지만
 * {@code @PreAuthorize} 를 함께 둔다. URL 규칙은 경로를 바꾸는 순간 조용히 풀리는데,
 * 메서드에 붙은 것은 같이 따라온다.
 *
 * <p>일정 수정·삭제는 소유권을 따지지 않는다 — 관리자 2명이 같은 일정을 함께 관리한다는
 * 전제라, 서로의 일정도 고칠 수 있어야 한다.
 */
@Slf4j
@Controller
@RequestMapping("/admin/calendar")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class MdCalendarAdminController {

    private final MdCalendarEventService calendarEventService;

    @GetMapping
    public String calendarView() {
        return "admin/calendar/calendarView";
    }

    // FullCalendar 가 JSON 피드로 직접 GET 호출한다.
    @GetMapping("/events")
    @ResponseBody
    public List<MdCalendarEventDTO> events() {
        return calendarEventService.getAllEvents();
    }

    @PostMapping("/events/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createEvent(@ModelAttribute MdCalendarEventDTO dto,
                                            @AuthenticationPrincipal PrincipalDetails principalDetails) {
        try {
            calendarEventService.createEvent(dto, principalDetails.getUsername());
            return ResponseEntity.ok(Map.of("success", true, "message", "일정이 등록되었습니다."));
        } catch (Exception e) {
            log.error("캘린더 일정 생성 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/events/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateEvent(@ModelAttribute MdCalendarEventDTO dto) {
        try {
            calendarEventService.updateEvent(dto);
            return ResponseEntity.ok(Map.of("success", true, "message", "일정이 수정되었습니다."));
        } catch (Exception e) {
            log.error("캘린더 일정 수정 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // 체크박스 토글 전용. updateEvent 와 분리해 두어 일반 수정 저장이 완료 상태를
    // 건드리지 않게 한다(수정 폼에는 완료 입력이 없다).
    @PostMapping("/events/{id}/complete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> completeEvent(@PathVariable Long id,
                                                               @RequestParam boolean completed) {
        try {
            calendarEventService.setCompleted(id, completed);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("캘린더 일정 완료 처리 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // 드래그 이동/리사이즈 전용. updateEvent 와 분리해 두어 날짜만 바뀌어도
    // 제목/설명/색상이 빈 값으로 덮어써지지 않게 한다(완료 토글과 같은 이유).
    @PostMapping("/events/{id}/reschedule")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rescheduleEvent(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startAt,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endAt) {
        try {
            calendarEventService.rescheduleEvent(id, startAt, endAt);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("캘린더 일정 기간 변경 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/events/delete/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteEvent(@PathVariable Long id) {
        try {
            calendarEventService.deleteEvent(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "일정이 삭제되었습니다."));
        } catch (Exception e) {
            log.error("캘린더 일정 삭제 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
