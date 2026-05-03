package com.moondap.controller;

import com.moondap.config.auth.PrincipalDetails;
import com.moondap.service.MdTestCategoryService;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.service.MdTestAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/test")
@RequiredArgsConstructor
public class MdTestAdminController {

    private final MdTestAdminService mdTestAdminService;
    private final MdTestCategoryService categoryService;
    private final ObjectMapper objectMapper;

    // ─── 테스트 목록 ───────────────────────────────────────────

    @GetMapping("/list")
    public String testList(Model model) {
        model.addAttribute("testList", mdTestAdminService.getTestList());
        return "admin/test/testList";
    }

    // ─── 테스트 생성 ───────────────────────────────────────────

    // [신규] 테이블 기반 등록 페이지 (표준) - 모바일 접속 시 자동 리다이렉트 (force=pc 시 제외)
    @GetMapping("/insertTestView")
    public String insertTestView(@RequestHeader(value = "User-Agent", required = false) String userAgent, 
                                @RequestParam(value = "force", required = false) String force,
                                Model model) {
        if (!"pc".equals(force) && userAgent != null && (userAgent.contains("Mobi") || userAgent.contains("Android") || userAgent.contains("iPhone"))) {
            return "redirect:/admin/test/insertTestMobileView";
        }
        model.addAttribute("test", new MdTestDTO());
        model.addAttribute("categories", categoryService.getActiveCategories());
        return "admin/test/insertTestView";
    }

    // [기존] 단계별(Wizard) 등록 페이지 (모바일 최적화)
    @GetMapping("/insertTestMobileView")
    public String insertTestMobileView(Model model) {
        model.addAttribute("test", new MdTestDTO());
        model.addAttribute("categories", categoryService.getActiveCategories());
        return "admin/test/insertTestMobileView";
    }

    @PostMapping("/insert")
    @ResponseBody
    public ResponseEntity<?> testInsert(
            @ModelAttribute MdTestDTO dto,
            @RequestParam(value = "resultsJson", required = false) String resultsJson,
            @RequestParam(value = "questionsJson", required = false) String questionsJson,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnail,
            @RequestParam(value = "resultFiles", required = false) List<MultipartFile> resultFiles) {
        try {
            // JSON 질문 리스트 파싱
            if (questionsJson != null && !questionsJson.isBlank()) {
                List<MdTestQuestionDTO> questions = objectMapper.readValue(questionsJson, new TypeReference<List<MdTestQuestionDTO>>() {});
                dto.setQuestions(questions);
            }

            // JSON 결과 리스트 파싱 [NEW]
            if (resultsJson != null && !resultsJson.isBlank()) {
                List<MdTestResultDTO> results = objectMapper.readValue(resultsJson, new TypeReference<List<MdTestResultDTO>>() {});
                dto.setResults(results);
            }

            mdTestAdminService.createTest(dto, thumbnail, resultFiles, getCurrentUsername());
            return ResponseEntity.ok(Map.of("success", true, "message", "테스트가 생성되었습니다."));
        } catch (Exception e) {
            log.error("테스트 생성 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── 테스트 수정 ───────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String testEditForm(@PathVariable("id") Long id, 
                              @RequestHeader(value = "User-Agent", required = false) String userAgent,
                              @RequestParam(value = "force", required = false) String force,
                              Model model) {
        MdTestDTO test = mdTestAdminService.getTest(id);
        if (test == null) return "redirect:/admin/test/list";
        
        if (!"pc".equals(force) && userAgent != null && (userAgent.contains("Mobi") || userAgent.contains("Android") || userAgent.contains("iPhone"))) {
            model.addAttribute("test", test);
            model.addAttribute("categories", categoryService.getActiveCategories());
            return "admin/test/insertTestMobileView";
        }
        
        model.addAttribute("test", test);
        model.addAttribute("categories", categoryService.getActiveCategories());
        return "admin/test/updateTestForm";
    }

    @PostMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<?> testUpdate(
            @PathVariable("id") Long id,
            @ModelAttribute MdTestDTO dto,
            @RequestParam(value = "resultsJson", required = false) String resultsJson,
            @RequestParam(value = "questionsJson", required = false) String questionsJson,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnail,
            @RequestParam(value = "resultFiles", required = false) List<MultipartFile> resultFiles) {
        dto.setId(id);
        try {
            // JSON 질문 리스트 파싱
            if (questionsJson != null && !questionsJson.isBlank()) {
                List<MdTestQuestionDTO> questions = objectMapper.readValue(questionsJson, new TypeReference<List<MdTestQuestionDTO>>() {});
                dto.setQuestions(questions);
            }

            // JSON 결과 리스트 파싱 [NEW]
            if (resultsJson != null && !resultsJson.isBlank()) {
                List<MdTestResultDTO> results = objectMapper.readValue(resultsJson, new TypeReference<List<MdTestResultDTO>>() {});
                dto.setResults(results);
            }

            mdTestAdminService.updateTest(dto, thumbnail, resultFiles);
            return ResponseEntity.ok(Map.of("success", true, "message", "테스트가 수정되었습니다."));
        } catch (Exception e) {
            log.error("테스트 수정 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── 테스트 삭제 ───────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String testDelete(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            mdTestAdminService.deleteTest(id);
            redirectAttributes.addFlashAttribute("successMsg", "테스트가 삭제되었습니다.");
        } catch (Exception e) {
            log.error("테스트 삭제 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "테스트 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/test/list";
    }

    // ─── 질문 목록 ───────────────────────────────────────────

    @GetMapping("/{testId}/questions")
    public String questionList(@PathVariable("testId") Long testId, Model model) {
        MdTestDTO test = mdTestAdminService.getTest(testId);
        if (test == null) return "redirect:/admin/test/list";
        model.addAttribute("test", test);
        model.addAttribute("questions", mdTestAdminService.getQuestions(testId));
        return "admin/test/questionList";
    }

    // ─── 유틸 ───────────────────────────────────────────

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "unknown";
        Object principal = auth.getPrincipal();
        if (principal instanceof PrincipalDetails) {
            return ((PrincipalDetails) principal).getUsername();
        }
        return auth.getName();
    }
}
