package com.moondap.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.moondap.dto.EgenTetoDTO;
import com.moondap.service.EgenTetoService;


import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 에겐/테토 테스트 컨트롤러
 */
@Slf4j
@Controller
@RequestMapping("/egenTeto")
public class EgenTetoController {

    private final EgenTetoService egenTetoService;

    public EgenTetoController(EgenTetoService egenTetoService) {
        this.egenTetoService = egenTetoService;
    }

    /**
     * 에겐/테토 테스트 상세 설명 페이지 (Intro)
     */
    @GetMapping("/selectEgenTetoGame")
    public String egenTetoView(Model model) {
        // 실시간 참여자 수 조회
        Map<String, Long> counts = egenTetoService.getGenderCounts();
        long totalCount = counts.get("maleCount") + counts.get("femaleCount");
        
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("maleCount", counts.get("maleCount"));
        model.addAttribute("femaleCount", counts.get("femaleCount"));
        
        return "egenTeto/selectEgenTetoGame";
    }

    /**
     * 성별 선택 화면 (테스트 인입점)
     */
    @GetMapping("/select")
    public String selectGender(Model model) {
        // 성별별 참여자 수 조회
        Map<String, Long> counts = egenTetoService.getGenderCounts();
        model.addAttribute("maleCount", counts.get("maleCount"));
        model.addAttribute("femaleCount", counts.get("femaleCount"));
        
        return "egenTeto/selectGender";
    }

    /**
     * 선택된 성별을 세션에 저장하고 테스트 시작
     */
    @GetMapping("/start")
    public String startTest(@RequestParam(value = "gender", defaultValue = "M") String gender, HttpServletRequest request) {
        request.getSession().setAttribute("gender", gender);
        return "redirect:/egenTeto/questions";
    }


    /**
     * 질문 페이지 (단일 페이지 위저드)
     */
    @GetMapping("/questions")
    public String egenTetoQuestions(HttpServletRequest request, Model model) {
        String gender = (String) request.getSession().getAttribute("gender");
        if (gender == null) gender = "M"; // 기본값
        
        model.addAttribute("gender", gender);
        return "egenTeto/questions";
    }

    /**
     * 결과 제출 및 계산
     */
    @PostMapping("/submit")
    public String submitAnswers(@RequestParam("gender") String gender, 
                                @RequestParam("answers") String answersJson, 
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        
        try {
            EgenTetoDTO result = egenTetoService.calculateResult(gender, answersJson);
            request.getSession().setAttribute("userBroker", result);
            return "redirect:/egenTeto/result";
        } catch (IllegalArgumentException e) {
            log.error("테스트 데이터 검증 실패: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/egenTeto/select";
        } catch (Exception e) {
            log.error("테스트 처리 중 서버 오류 발생", e);
            redirectAttributes.addFlashAttribute("errorMessage", "처리 중 오류가 발생했습니다. 다시 시도해 주세요.");
            return "redirect:/egenTeto/select";
        }
    }


    /**
     * 에겐/테토 테스트 결과 화면
     */
    @GetMapping("/result")
    public String egenTetoResult(@RequestParam(value = "userId", required = false) String userId,
                                HttpServletRequest request, 
                                RedirectAttributes redirectAttributes,
                                Model model) {
        
        EgenTetoDTO userBroker = null;

        // 1. 파라미터로 userId가 넘어온 경우 DB에서 조회
        if (userId != null && !userId.isEmpty()) {
            userBroker = egenTetoService.getTestResult(userId);
        }

        // 2. 파라미터가 없거나 DB에 데이터가 없는 경우 세션에서 조회
        if (userBroker == null) {
            userBroker = (EgenTetoDTO) request.getSession().getAttribute("userBroker");
        }
        
        // 3. 둘 다 없으면 에러 발생 및 리다이렉트
        if (userBroker == null) {
            log.warn("테스트 결과 데이터가 존재하지 않습니다. (userId: {})", userId);
            redirectAttributes.addFlashAttribute("errorMessage", "조회된 테스트 결과가 없거나 세션이 만료되었습니다.");
            return "redirect:/egenTeto/select";
        }


        model.addAttribute("userBroker", userBroker);
        
        // URL 정보 설정
        String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), "");
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("fullUrl", request.getRequestURL().toString());
        model.addAttribute("shareUrl", baseUrl + "/egenTeto/result?userId=" + userBroker.getUserNo());
        
        // 전체 점수 통계 정보 추가 (정규분포 그래프용)
        java.util.Map<String, Object> stats = egenTetoService.getScoreStatistics();
        model.addAttribute("stats", stats);

        return "egenTeto/result";
    }

}
