package com.moondap.service;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.moondap.common.AnonymousIdentity;
import com.moondap.common.SecurityUtil;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.dto.TestHistoryDTO;
import com.moondap.mapper.TestHistoryMapper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 내 결과 보관함.
 *
 * <p>테스트 결과가 어디에도 남지 않아 한 달 전에 한 테스트를 다시 볼 방법이 없었다.
 * 마이페이지는 콘텐츠 제작자·관리자용이라 일반 사용자에게는 로그인할 이유도 없었다.
 *
 * <p>비로그인 사용자도 쓸 수 있어야 한다. 결과를 보려면 먼저 가입하라고 막으면
 * 테스트를 끝까지 하는 사람 자체가 줄어든다. 그래서 익명 쿠키로 먼저 쌓아두고,
 * 나중에 로그인하면 그 기록을 계정으로 옮긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestHistoryService {

    /** 한 번에 보여줄 기록 수. */
    public static final int PAGE_SIZE = 20;

    private final TestHistoryMapper testHistoryMapper;

    /**
     * 테스트를 마친 기록을 남긴다.
     *
     * <p>보관함은 부가 기능이므로 실패해도 결과 화면은 그대로 보여준다. 기록이 하나
     * 빠지는 것보다 결과 화면이 오류 페이지가 되는 쪽이 훨씬 나쁘다.
     *
     * @param response 익명 사용자에게 식별 쿠키를 발급하기 위해 필요하다
     */
    public void record(MdTestDTO test, MdTestResultDTO result, String resultCode,
                       HttpServletResponse response) {
        if (test == null || result == null) {
            return;
        }

        try {
            TestHistoryDTO history = new TestHistoryDTO();
            history.setTestKey(test.getTestKey());
            history.setTestId(test.getId());
            history.setTestTitle(test.getTitle());
            history.setResultId(result.getId());
            history.setResultCode(resultCode);
            history.setResultTitle(result.getResultTitle());
            history.setResultImage(result.getResultImage());
            history.setScore(result.getCalculatedScore());

            String username = SecurityUtil.getCurrentUsername();
            if (username != null) {
                history.setUserId(username);
            } else {
                // 여기서 처음으로 익명 쿠키가 발급될 수 있다. 단순 열람이 아니라
                // 테스트를 끝까지 마친 시점이므로 식별자를 남길 근거가 있다.
                history.setAnonId(AnonymousIdentity.getOrCreate(response));
            }

            testHistoryMapper.insertHistory(history);
        } catch (Exception e) {
            log.error("결과 보관 실패: testKey={}", test.getTestKey(), e);
        }
    }

    /**
     * 보관함 목록.
     *
     * <p>로그인 상태면 계정 기준, 아니면 익명 쿠키 기준이다.
     */
    public List<TestHistoryDTO> list(int offset) {
        String username = SecurityUtil.getCurrentUsername();
        String anonId = (username == null) ? AnonymousIdentity.current() : null;

        if (username == null && anonId == null) {
            return Collections.emptyList();
        }

        try {
            return testHistoryMapper.selectHistory(username, anonId, Math.max(0, offset), PAGE_SIZE + 1);
        } catch (Exception e) {
            // 조회가 실패해도 오류 페이지를 띄우지 않는다. 보관함은 부가 기능인데
            // 테이블이 아직 없거나(마이그레이션 누락) DB 가 흔들릴 때 방문자에게
            // 500 을 보여주는 쪽이 더 나쁘다.
            // 원인은 로그에 남긴다 — 화면이 조용히 비어 있으면 알아챌 방법이 없다.
            log.error("보관함 조회 실패. 빈 목록으로 대체한다.", e);
            return Collections.emptyList();
        }
    }

    /**
     * 로그인 전에 익명으로 쌓아둔 기록을 계정으로 옮긴다.
     *
     * <p>로그인 성공 핸들러가 아니라 보관함을 열 때 수행한다. 로그인 경로에 손을 대면
     * 여기서 나는 오류가 로그인 자체를 막을 수 있다. 보관함은 로그인 직후 확인하는
     * 화면이므로 시점 차이도 사실상 없다.
     *
     * @return 옮긴 건수
     */
    public int claimAnonymousHistory() {
        String username = SecurityUtil.getCurrentUsername();
        String anonId = AnonymousIdentity.current();

        if (username == null || anonId == null) {
            return 0;
        }

        try {
            int moved = testHistoryMapper.claimAnonymousHistory(username, anonId);
            if (moved > 0) {
                log.info("익명 기록 {}건을 계정으로 옮김: {}", moved, username);
            }
            return moved;
        } catch (Exception e) {
            log.error("익명 기록 이전 실패: {}", username, e);
            return 0;
        }
    }

    /**
     * 보관함에서 한 건 삭제.
     *
     * @return 실제로 지워졌는지. 남의 기록이면 false.
     */
    public boolean delete(long no) {
        String username = SecurityUtil.getCurrentUsername();
        String anonId = (username == null) ? AnonymousIdentity.current() : null;

        if (username == null && anonId == null) {
            return false;
        }
        return testHistoryMapper.deleteHistory(no, username, anonId) == 1;
    }
}
