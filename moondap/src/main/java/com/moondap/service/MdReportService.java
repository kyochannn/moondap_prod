package com.moondap.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moondap.common.SecurityUtil;
import com.moondap.common.exception.UserMessageException;
import com.moondap.dto.MdReportDTO;
import com.moondap.dto.request.ReportRequest;
import com.moondap.mapper.BalanceGameMapper;
import com.moondap.mapper.MdReportMapper;
import com.moondap.mapper.MdTestMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 콘텐츠 신고.
 *
 * <p>금칙어 필터({@link com.moondap.common.ProfanityUtil})는 등록 시점의 1차 방어다.
 * 그것을 통과한 표현이나, 애초에 단어로 거를 수 없는 문제(저작권 침해 이미지, 도배)는
 * 이용자 신고로 잡는다. 광고가 붙은 페이지에 문제 콘텐츠가 오래 남으면 애드센스
 * 정책 위반이 되므로, 발견 경로를 운영자 순회에만 의존하지 않는다.
 *
 * <p>신고는 비로그인 이용자도 할 수 있다. 로그인을 요구하면 대부분의 방문자가
 * 신고하지 못해 기능이 있으나 마나가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdReportService {

    /** 신고 시 함께 저장하는 대상 내용의 최대 길이. 컬럼 정의와 맞춘다. */
    private static final int SNAPSHOT_MAX_LENGTH = 1000;

    private final MdReportMapper mdReportMapper;
    private final BalanceGameMapper balanceGameMapper;
    private final MdTestMapper mdTestMapper;

    /**
     * 신고를 접수한다.
     *
     * @param anonId 익명 신고자 토큰. 로그인 상태면 무시된다.
     */
    @Transactional
    public void report(ReportRequest request, String anonId) {

        // 화면이 보내는 값이므로 허용 목록으로 좁힌다. 임의의 문자열이 들어오면
        // 관리 화면의 분류가 무너지고 대상을 찾아갈 수도 없다.
        if (!request.hasValidTargetType()) {
            throw new UserMessageException("잘못된 신고 대상입니다.");
        }
        if (!request.hasValidReasonCode()) {
            throw new UserMessageException("신고 사유를 선택해주세요.");
        }

        String userId = SecurityUtil.isAuthenticated() ? SecurityUtil.getCurrentUsername() : null;

        // 로그인 사용자는 계정으로, 비로그인 사용자는 익명 쿠키로 식별한다.
        // 둘 다 없으면 같은 사람이 무한히 신고해 건수를 부풀릴 수 있다.
        if (userId == null && (anonId == null || anonId.isBlank())) {
            throw new UserMessageException("신고를 접수할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        if (mdReportMapper.countByReporter(request.getTargetType(), request.getTargetId(),
                userId, anonId) > 0) {
            throw new UserMessageException("이미 신고한 콘텐츠입니다.");
        }

        MdReportDTO report = new MdReportDTO();
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReasonCode(request.getReasonCode());
        report.setDetail(request.getDetail());
        report.setTargetSnapshot(truncate(lookupSnapshot(request)));
        report.setReporterUserId(userId);
        report.setReporterAnonId(userId == null ? anonId : null);

        mdReportMapper.insertReport(report);

        log.info("신고 접수: type={}, target={}, reason={}",
                request.getTargetType(), request.getTargetId(), request.getReasonCode());
    }

    public List<MdReportDTO> getReports(String status, int offset, int limit) {
        return mdReportMapper.selectReports(status, offset, limit);
    }

    public int countReports(String status) {
        return mdReportMapper.countReports(status);
    }

    public int countPending() {
        return mdReportMapper.countPending();
    }

    /**
     * 신고를 처리 상태로 바꾼다.
     *
     * <p>대상 콘텐츠를 실제로 지우는 것은 별개 작업이다. 여기서 함께 삭제하지 않는
     * 이유는, 신고가 늘 타당한 것은 아니어서 운영자가 내용을 보고 판단해야 하기
     * 때문이다. 관리 화면은 대상으로 이동하는 링크를 제공한다.
     */
    @Transactional
    public void updateStatus(Long no, String status) {
        if (!List.of("PENDING", "RESOLVED", "REJECTED").contains(status)) {
            throw new UserMessageException("잘못된 처리 상태입니다.");
        }
        if (mdReportMapper.selectReport(no) == null) {
            throw new UserMessageException("신고 내역을 찾을 수 없습니다.");
        }
        mdReportMapper.updateStatus(no, status, SecurityUtil.getCurrentUsername());
    }

    /**
     * 신고 대상의 내용을 서버에서 직접 읽는다.
     *
     * <p>화면이 보낸 텍스트를 그대로 저장하면 신고자가 아무 내용이나 적어 넣을 수
     * 있어서, 관리자가 보는 근거 자체를 조작할 수 있다. 그래서 원본을 다시 조회한다.
     *
     * <p>대상을 찾지 못해도 신고는 접수한다. 이미 지워진 콘텐츠를 신고하는 경우가
     * 있고, 그때 접수를 거부하면 신고자에게는 오류로만 보인다.
     */
    private String lookupSnapshot(ReportRequest request) {
        try {
            switch (request.getTargetType()) {
                case "COMMENT" -> {
                    var comment = balanceGameMapper.selectCommentByNo(Integer.parseInt(request.getTargetId()));
                    return comment == null ? null : comment.getNickname() + ": " + comment.getContent();
                }
                case "BALANCE" -> {
                    var game = balanceGameMapper.selectBalanceGame(request.getTargetId(), "1", null);
                    return game == null ? null
                            : game.getTitle() + " / " + game.getOption1Text() + " vs " + game.getOption2Text();
                }
                case "TEST" -> {
                    var test = mdTestMapper.selectTestByTestKey(request.getTargetId());
                    return test == null ? null : test.getTitle() + " / " + test.getDescription();
                }
                default -> {
                    return null;
                }
            }
        } catch (Exception e) {
            // 스냅샷은 부가 정보다. 조회에 실패했다고 신고 접수를 막을 이유가 없다.
            log.warn("신고 대상 스냅샷 조회 실패: type={}, target={}",
                    request.getTargetType(), request.getTargetId(), e);
            return null;
        }
    }

    private String truncate(String snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.length() <= SNAPSHOT_MAX_LENGTH
                ? snapshot
                : snapshot.substring(0, SNAPSHOT_MAX_LENGTH);
    }
}
