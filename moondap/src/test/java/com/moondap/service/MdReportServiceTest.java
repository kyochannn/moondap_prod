package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.moondap.common.exception.UserMessageException;
import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.MdReportDTO;
import com.moondap.dto.request.ReportRequest;
import com.moondap.mapper.BalanceGameMapper;
import com.moondap.mapper.MdReportMapper;
import com.moondap.mapper.MdTestMapper;

/**
 * 신고 접수 규칙.
 *
 * <p>신고는 비로그인 이용자도 할 수 있어야 하는데, 그러면 같은 사람이 무한히 신고해
 * 건수를 부풀릴 수 있다. 식별과 중복 차단이 이 기능의 핵심이라 테스트로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MdReportServiceTest {

    @Mock
    private MdReportMapper mdReportMapper;
    @Mock
    private BalanceGameMapper balanceGameMapper;
    @Mock
    private MdTestMapper mdTestMapper;

    @InjectMocks
    private MdReportService mdReportService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "pw", java.util.List.of()));
    }

    private ReportRequest request(String type, String targetId, String reason) {
        ReportRequest request = new ReportRequest();
        request.setTargetType(type);
        request.setTargetId(targetId);
        request.setReasonCode(reason);
        return request;
    }

    @Test
    @DisplayName("비로그인 이용자도 익명 토큰이 있으면 신고할 수 있다")
    void anonymousCanReport() {
        mdReportService.report(request("COMMENT", "12", "ABUSE"), "anon-token");

        ArgumentCaptor<MdReportDTO> captor = ArgumentCaptor.forClass(MdReportDTO.class);
        verify(mdReportMapper).insertReport(captor.capture());

        MdReportDTO saved = captor.getValue();
        assertThat(saved.getReporterAnonId()).isEqualTo("anon-token");
        assertThat(saved.getReporterUserId()).isNull();
        assertThat(saved.getStatus()).isNull();   // DB 기본값 PENDING 이 들어간다
    }

    @Test
    @DisplayName("로그인 사용자는 계정으로 기록하고 익명 토큰은 남기지 않는다")
    void loggedInReportUsesAccount() {
        loginAs("tester");

        mdReportService.report(request("COMMENT", "12", "ABUSE"), "anon-token");

        ArgumentCaptor<MdReportDTO> captor = ArgumentCaptor.forClass(MdReportDTO.class);
        verify(mdReportMapper).insertReport(captor.capture());

        assertThat(captor.getValue().getReporterUserId()).isEqualTo("tester");
        // 계정이 있는데 익명 토큰까지 남기면 같은 사람이 두 번 신고할 수 있다.
        assertThat(captor.getValue().getReporterAnonId()).isNull();
    }

    @Test
    @DisplayName("[회귀] 같은 대상을 두 번 신고할 수 없다")
    void rejectsDuplicateReport() {
        when(mdReportMapper.countByReporter(anyString(), anyString(), any(), any())).thenReturn(1);

        assertThatThrownBy(() -> mdReportService.report(request("COMMENT", "12", "ABUSE"), "anon"))
                .isInstanceOf(UserMessageException.class)
                .hasMessageContaining("이미 신고");

        verify(mdReportMapper, never()).insertReport(any());
    }

    @Test
    @DisplayName("[회귀] 식별할 수단이 없으면 접수하지 않는다")
    void rejectsUnidentifiableReporter() {
        // 로그인도 아니고 익명 토큰도 없으면 중복 신고를 막을 방법이 없다.
        assertThatThrownBy(() -> mdReportService.report(request("COMMENT", "12", "ABUSE"), null))
                .isInstanceOf(UserMessageException.class);

        verify(mdReportMapper, never()).insertReport(any());
    }

    @Test
    @DisplayName("[회귀] 허용 목록에 없는 대상·사유는 거부한다")
    void rejectsUnknownCodes() {
        assertThatThrownBy(() -> mdReportService.report(request("HACK", "12", "ABUSE"), "anon"))
                .isInstanceOf(UserMessageException.class)
                .hasMessageContaining("잘못된 신고 대상");

        assertThatThrownBy(() -> mdReportService.report(request("COMMENT", "12", "WHATEVER"), "anon"))
                .isInstanceOf(UserMessageException.class)
                .hasMessageContaining("신고 사유");

        verify(mdReportMapper, never()).insertReport(any());
    }

    @Test
    @DisplayName("[회귀] 대상 내용은 서버가 직접 읽어 저장한다")
    void snapshotComesFromServer() {
        // 화면이 보낸 텍스트를 그대로 저장하면 신고자가 근거를 조작할 수 있다.
        BalanceGameCommentDTO comment = new BalanceGameCommentDTO();
        comment.setNickname("작성자");
        comment.setContent("문제가 되는 댓글");
        when(balanceGameMapper.selectCommentByNo(12)).thenReturn(comment);

        mdReportService.report(request("COMMENT", "12", "ABUSE"), "anon");

        ArgumentCaptor<MdReportDTO> captor = ArgumentCaptor.forClass(MdReportDTO.class);
        verify(mdReportMapper).insertReport(captor.capture());
        assertThat(captor.getValue().getTargetSnapshot()).isEqualTo("작성자: 문제가 되는 댓글");
    }

    @Test
    @DisplayName("대상을 찾지 못해도 신고는 접수한다")
    void acceptsReportWhenTargetMissing() {
        // 이미 지워진 콘텐츠를 신고하는 경우가 있다. 여기서 막으면 오류로만 보인다.
        when(balanceGameMapper.selectCommentByNo(anyInt())).thenReturn(null);

        mdReportService.report(request("COMMENT", "99", "SPAM"), "anon");

        ArgumentCaptor<MdReportDTO> captor = ArgumentCaptor.forClass(MdReportDTO.class);
        verify(mdReportMapper).insertReport(captor.capture());
        assertThat(captor.getValue().getTargetSnapshot()).isNull();
    }

    @Test
    @DisplayName("스냅샷 조회가 예외를 던져도 접수를 막지 않는다")
    void survivesSnapshotFailure() {
        when(balanceGameMapper.selectCommentByNo(anyInt())).thenThrow(new RuntimeException("DB down"));

        mdReportService.report(request("COMMENT", "12", "ABUSE"), "anon");

        verify(mdReportMapper).insertReport(any());
    }

    @Test
    @DisplayName("[회귀] 처리 상태는 정해진 값만 허용한다")
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> mdReportService.updateStatus(1L, "DELETED"))
                .isInstanceOf(UserMessageException.class);

        verify(mdReportMapper, never()).updateStatus(any(), anyString(), any());
    }

    @Test
    @DisplayName("없는 신고를 처리하려 하면 거부한다")
    void rejectsUnknownReport() {
        when(mdReportMapper.selectReport(99L)).thenReturn(null);

        assertThatThrownBy(() -> mdReportService.updateStatus(99L, "RESOLVED"))
                .isInstanceOf(UserMessageException.class);
    }

    @Test
    @DisplayName("처리자 계정을 함께 기록한다")
    void recordsHandler() {
        loginAs("admin");
        when(mdReportMapper.selectReport(1L)).thenReturn(new MdReportDTO());

        mdReportService.updateStatus(1L, "RESOLVED");

        verify(mdReportMapper).updateStatus(eq(1L), eq("RESOLVED"), eq("admin"));
    }
}
