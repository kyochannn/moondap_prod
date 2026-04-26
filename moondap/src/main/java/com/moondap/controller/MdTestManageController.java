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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/test/manage")
@RequiredArgsConstructor
public class MdTestManageController {

    private final MdTestAdminService mdTestAdminService;
    private final MdTestCategoryService categoryService;
    private final ObjectMapper objectMapper;

    // ─── 테스트 목록 ───────────────────────────────────────────

    @GetMapping("/list")
    public String testList(@AuthenticationPrincipal PrincipalDetails principalDetails, Model model) {
        boolean isAdmin = principalDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (isAdmin) {
            model.addAttribute("testList", mdTestAdminService.getTestList());
            model.addAttribute("manageTitle", "전체 테스트 관리");
        } else {
            model.addAttribute("testList", mdTestAdminService.getTestListByUser(principalDetails.getUsername()));
            model.addAttribute("manageTitle", "내가 만든 테스트");
        }
        
        return "admin/test/testList";
    }

    // ─── 테스트 생성 ───────────────────────────────────────────

    @GetMapping("/new")
    public String testNewForm(Model model) {
        model.addAttribute("test", new MdTestDTO());
        model.addAttribute("categories", categoryService.getActiveCategories());
        return "admin/test/insertTestForm";
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> testCreate(
            @AuthenticationPrincipal PrincipalDetails principalDetails,
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

            // JSON 결과 리스트 파싱
            if (resultsJson != null && !resultsJson.isBlank()) {
                List<MdTestResultDTO> results = objectMapper.readValue(resultsJson, new TypeReference<List<MdTestResultDTO>>() {});
                dto.setResults(results);
            }

            mdTestAdminService.createTest(dto, thumbnail, resultFiles, principalDetails.getUsername());
            return ResponseEntity.ok(Map.of("success", true, "message", "테스트가 생성되었습니다."));
        } catch (Exception e) {
            log.error("테스트 생성 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── 테스트 수정 ───────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String testEditForm(@PathVariable("id") Long id, 
                              @AuthenticationPrincipal PrincipalDetails principalDetails,
                              Model model) {
        boolean isAdmin = principalDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!mdTestAdminService.checkOwnership(id, principalDetails.getUsername(), isAdmin)) {
            return "redirect:/test/manage/list";
        }

        MdTestDTO test = mdTestAdminService.getTest(id);
        if (test == null) return "redirect:/test/manage/list";
        model.addAttribute("test", test);
        model.addAttribute("categories", categoryService.getActiveCategories());
        return "admin/test/updateTestForm";
    }

    @PostMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<?> testUpdate(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal PrincipalDetails principalDetails,
            @ModelAttribute MdTestDTO dto,
            @RequestParam(value = "resultsJson", required = false) String resultsJson,
            @RequestParam(value = "questionsJson", required = false) String questionsJson,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnail,
            @RequestParam(value = "resultFiles", required = false) List<MultipartFile> resultFiles) {
        
        boolean isAdmin = principalDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!mdTestAdminService.checkOwnership(id, principalDetails.getUsername(), isAdmin)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "수정 권한이 없습니다."));
        }

        dto.setId(id);
        try {
            // JSON 질문 리스트 파싱
            if (questionsJson != null && !questionsJson.isBlank()) {
                List<MdTestQuestionDTO> questions = objectMapper.readValue(questionsJson, new TypeReference<List<MdTestQuestionDTO>>() {});
                dto.setQuestions(questions);
            }

            // JSON 결과 리스트 파싱
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
    public String testDelete(@PathVariable("id") Long id, 
                            @AuthenticationPrincipal PrincipalDetails principalDetails,
                            @RequestHeader(value = "Referer", required = false) String referer,
                            RedirectAttributes redirectAttributes) {
        boolean isAdmin = principalDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!mdTestAdminService.checkOwnership(id, principalDetails.getUsername(), isAdmin)) {
            redirectAttributes.addFlashAttribute("errorMsg", "삭제 권한이 없습니다.");
            return "redirect:/test/manage/list";
        }

        try {
            mdTestAdminService.deleteTest(id);
            redirectAttributes.addFlashAttribute("successMsg", "테스트가 삭제되었습니다.");
        } catch (Exception e) {
            log.error("테스트 삭제 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "테스트 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:" + (referer != null ? referer : "/test/manage/list");
    }

    // ─── 질문 목록 ───────────────────────────────────────────

    @GetMapping("/{testId}/questions")
    public String questionList(@PathVariable("testId") Long testId, 
                              @AuthenticationPrincipal PrincipalDetails principalDetails,
                              Model model) {
        boolean isAdmin = principalDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!mdTestAdminService.checkOwnership(testId, principalDetails.getUsername(), isAdmin)) {
            return "redirect:/test/manage/list";
        }

        MdTestDTO test = mdTestAdminService.getTest(testId);
        if (test == null) return "redirect:/test/manage/list";
        model.addAttribute("test", test);
        model.addAttribute("questions", mdTestAdminService.getQuestions(testId));
        return "admin/test/questionList";
    }
}
