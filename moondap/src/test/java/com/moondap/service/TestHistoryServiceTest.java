package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.moondap.common.AnonymousIdentity;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.dto.TestHistoryDTO;
import com.moondap.mapper.TestHistoryMapper;

import jakarta.servlet.http.Cookie;

/**
 * 내 결과 보관함.
 *
 * <p>보관은 부가 기능이다. 여기서 나는 오류가 결과 화면을 오류 페이지로 만들면
 * 테스트를 마친 사람이 자기 결과를 아예 못 보게 되므로, 실패해도 조용히 넘어가야 한다.
 */
@ExtendWith(MockitoExtension.class)
class TestHistoryServiceTest {

    @Mock
    private TestHistoryMapper testHistoryMapper;

    @InjectMocks
    private TestHistoryService service;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("비로그인 사용자는 익명 쿠키로 저장된다")
    void recordsWithAnonymousCookie() {
        bindRequest(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.record(test(), result(), "7", response);

        ArgumentCaptor<TestHistoryDTO> captor = ArgumentCaptor.forClass(TestHistoryDTO.class);
        verify(testHistoryMapper).insertHistory(captor.capture());

        TestHistoryDTO saved = captor.getValue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getAnonId()).isNotBlank();
        // 테스트가 지워져도 보관함이 빈칸이 되지 않도록 제목을 복사해 둔다.
        assertThat(saved.getTestTitle()).isEqualTo("연애 성향 테스트");
        assertThat(saved.getResultTitle()).isEqualTo("따뜻한 낭만가");
        assertThat(saved.getResultCode()).isEqualTo("7");
    }

    @Test
    @DisplayName("[회귀] 저장에 실패해도 결과 화면은 막지 않는다")
    void swallowsFailure() {
        bindRequest(null);
        when(testHistoryMapper.insertHistory(any())).thenThrow(new RuntimeException("DB 장애"));

        // 예외가 밖으로 나가면 결과 페이지가 통째로 500 이 된다.
        service.record(test(), result(), "7", new MockHttpServletResponse());
    }

    @Test
    @DisplayName("결과가 없으면 아무것도 저장하지 않는다")
    void skipsWhenNothingToSave() {
        service.record(test(), null, "7", new MockHttpServletResponse());
        service.record(null, result(), "7", new MockHttpServletResponse());

        verify(testHistoryMapper, never()).insertHistory(any());
    }

    @Test
    @DisplayName("신원을 알 수 없으면 빈 목록을 준다")
    void emptyWhenNoIdentity() {
        bindRequest(null);

        assertThat(service.list(0)).isEmpty();
        // 조건 없이 전체를 읽는 사고를 막기 위해 아예 조회하지 않는다.
        verify(testHistoryMapper, never()).selectHistory(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("익명 쿠키가 있으면 그 기준으로 조회한다")
    void listsByAnonymousCookie() {
        bindRequest("anon-123");
        when(testHistoryMapper.selectHistory(null, "anon-123", 0, TestHistoryService.PAGE_SIZE + 1))
                .thenReturn(List.of(new TestHistoryDTO()));

        assertThat(service.list(0)).hasSize(1);
    }

    @Test
    @DisplayName("[회귀] 조회가 실패해도 오류 페이지 대신 빈 보관함을 보여준다")
    void listSurvivesDatabaseFailure() {
        bindRequest("anon-123");
        when(testHistoryMapper.selectHistory(anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Table 'md_test_history' doesn't exist"));

        // 마이그레이션을 아직 적용하지 않은 서버에서 이 페이지가 통째로 500 이 났다.
        assertThat(service.list(0)).isEmpty();
    }

    @Test
    @DisplayName("음수 offset 은 0 으로 본다")
    void clampsNegativeOffset() {
        bindRequest("anon-123");
        when(testHistoryMapper.selectHistory(null, "anon-123", 0, TestHistoryService.PAGE_SIZE + 1))
                .thenReturn(List.of());

        service.list(-10);

        verify(testHistoryMapper).selectHistory(null, "anon-123", 0, TestHistoryService.PAGE_SIZE + 1);
    }

    @Test
    @DisplayName("로그인하지 않았으면 기록을 계정으로 옮기지 않는다")
    void doesNotClaimWhenAnonymous() {
        bindRequest("anon-123");

        assertThat(service.claimAnonymousHistory()).isZero();
        verify(testHistoryMapper, never()).claimAnonymousHistory(anyString(), anyString());
    }

    @Test
    @DisplayName("신원을 알 수 없으면 삭제하지 않는다")
    void doesNotDeleteWithoutIdentity() {
        bindRequest(null);

        assertThat(service.delete(1L)).isFalse();
        verify(testHistoryMapper, never()).deleteHistory(anyLong(), anyString(), anyString());
    }

    /** 요청 컨텍스트를 만든다. anonId 가 있으면 익명 쿠키를 실어준다. */
    private void bindRequest(String anonId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (anonId != null) {
            request.setCookies(new Cookie(AnonymousIdentity.COOKIE_NAME, anonId));
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private MdTestDTO test() {
        MdTestDTO dto = new MdTestDTO();
        dto.setId(1L);
        dto.setTestKey("love-style");
        dto.setTitle("연애 성향 테스트");
        return dto;
    }

    private MdTestResultDTO result() {
        MdTestResultDTO dto = new MdTestResultDTO();
        dto.setId(7L);
        dto.setResultTitle("따뜻한 낭만가");
        dto.setResultImage("abc.png");
        dto.setCalculatedScore(42);
        return dto;
    }
}
