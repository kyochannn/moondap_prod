package com.moondap.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.moondap.common.SecurityUtil;
import com.moondap.dto.SeoMetaDTO;
import com.moondap.dto.TestHistoryDTO;
import com.moondap.service.TestHistoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 내 결과 보관함.
 *
 * <p>로그인하지 않아도 열 수 있다. 결과를 보려면 먼저 가입하라고 막으면 테스트를
 * 끝까지 하는 사람 자체가 줄어든다. 대신 화면에서 "로그인하면 기기를 바꿔도 남는다"를
 * 안내해 가입 이유를 만든다.
 */
@Slf4j
@Controller
@RequestMapping("/my")
@RequiredArgsConstructor
public class TestHistoryController {

    private final TestHistoryService testHistoryService;

    @GetMapping("/results")
    public String results(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
                          Model model) {

        // 로그인 전에 익명으로 쌓아둔 기록이 있으면 이 시점에 계정으로 옮긴다.
        // 로그인 핸들러를 건드리지 않으려는 선택이다(TestHistoryService.claimAnonymousHistory 참고).
        int claimed = testHistoryService.claimAnonymousHistory();

        int safePage = Math.max(0, page);
        int offset = safePage * TestHistoryService.PAGE_SIZE;

        List<TestHistoryDTO> histories = testHistoryService.list(offset);

        boolean hasMore = histories.size() > TestHistoryService.PAGE_SIZE;
        if (hasMore) {
            histories = histories.subList(0, TestHistoryService.PAGE_SIZE);
        }

        model.addAttribute("histories", histories);
        model.addAttribute("hasMore", hasMore);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("isLoggedIn", SecurityUtil.isAuthenticated());
        model.addAttribute("claimedCount", claimed);

        // 개인 화면이라 색인 대상이 아니다.
        SeoMetaDTO seo = SeoMetaDTO.of("내 결과 보관함 - 문답", "지금까지 참여한 테스트 결과를 모아 봅니다.");
        seo.setRobots("noindex, nofollow");
        model.addAttribute("seo", seo);

        return "my/results";
    }

    /**
     * 보관함에서 한 건 삭제.
     *
     * <p>소유자 확인은 서비스가 한다. 남의 번호를 넣어도 삭제되지 않는다.
     */
    @PostMapping("/results/{no}/delete")
    public String delete(@PathVariable("no") long no) {
        boolean deleted = testHistoryService.delete(no);
        if (!deleted) {
            log.warn("보관함 삭제 실패 또는 권한 없음: no={}", no);
        }
        return "redirect:/my/results";
    }
}
