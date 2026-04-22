package com.moondap.controller;

import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.service.MdTestAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/test")
@RequiredArgsConstructor
public class MdTestAdminController {

    private final MdTestAdminService mdTestAdminService;

    // ─── 테스트 목록 ───────────────────────────────────────────

    @GetMapping("/list")
    public String testList(Model model) {
        model.addAttribute("testList", mdTestAdminService.getTestList());
        return "admin/test/testList";
    }

    // ─── 테스트 생성 ───────────────────────────────────────────

    @GetMapping("/new")
    public String testNewForm(Model model) {
        model.addAttribute("test", new MdTestDTO());
        return "admin/test/testForm";
    }

    @PostMapping("/create")
    public String testCreate(
            @ModelAttribute MdTestDTO dto,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnail,
            RedirectAttributes redirectAttributes) {
        try {
            mdTestAdminService.createTest(dto, thumbnail, getCurrentUsername());
            redirectAttributes.addFlashAttribute("successMsg", "테스트가 생성되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/admin/test/new";
        } catch (Exception e) {
            log.error("테스트 생성 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "테스트 생성 중 오류가 발생했습니다.");
            return "redirect:/admin/test/new";
        }
        return "redirect:/admin/test/list";
    }

    // ─── 테스트 수정 ───────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String testEditForm(@PathVariable("id") Long id, Model model) {
        MdTestDTO test = mdTestAdminService.getTest(id);
        if (test == null) return "redirect:/admin/test/list";
        model.addAttribute("test", test);
        return "admin/test/testForm";
    }

    @PostMapping("/{id}/update")
    public String testUpdate(
            @PathVariable("id") Long id,
            @ModelAttribute MdTestDTO dto,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnail,
            RedirectAttributes redirectAttributes) {
        dto.setId(id);
        try {
            mdTestAdminService.updateTest(dto, thumbnail);
            redirectAttributes.addFlashAttribute("successMsg", "테스트가 수정되었습니다.");
        } catch (Exception e) {
            log.error("테스트 수정 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "테스트 수정 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/test/list";
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

    // ─── 질문 생성 ───────────────────────────────────────────

    @GetMapping("/{testId}/questions/new")
    public String questionNewForm(@PathVariable("testId") Long testId, Model model) {
        MdTestDTO test = mdTestAdminService.getTest(testId);
        if (test == null) return "redirect:/admin/test/list";
        MdTestQuestionDTO question = new MdTestQuestionDTO();
        question.setTestId(testId);
        model.addAttribute("test", test);
        model.addAttribute("question", question);
        return "admin/test/questionForm";
    }

    @PostMapping("/{testId}/questions/create")
    public String questionCreate(
            @PathVariable("testId") Long testId,
            @ModelAttribute MdTestQuestionDTO dto,
            RedirectAttributes redirectAttributes) {
        dto.setTestId(testId);
        try {
            mdTestAdminService.createQuestion(dto);
            redirectAttributes.addFlashAttribute("successMsg", "질문이 추가되었습니다.");
        } catch (Exception e) {
            log.error("질문 생성 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "질문 추가 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/test/" + testId + "/questions";
    }

    // ─── 질문 수정 ───────────────────────────────────────────

    @GetMapping("/{testId}/questions/{qId}/edit")
    public String questionEditForm(@PathVariable("testId") Long testId, @PathVariable("qId") Long qId, Model model) {
        MdTestDTO test = mdTestAdminService.getTest(testId);
        MdTestQuestionDTO question = mdTestAdminService.getQuestion(qId);
        if (test == null || question == null) return "redirect:/admin/test/" + testId + "/questions";
        model.addAttribute("test", test);
        model.addAttribute("question", question);
        return "admin/test/questionForm";
    }

    @PostMapping("/{testId}/questions/{qId}/update")
    public String questionUpdate(
            @PathVariable("testId") Long testId,
            @PathVariable("qId") Long qId,
            @ModelAttribute MdTestQuestionDTO dto,
            RedirectAttributes redirectAttributes) {
        dto.setId(qId);
        dto.setTestId(testId);
        try {
            mdTestAdminService.updateQuestion(dto);
            redirectAttributes.addFlashAttribute("successMsg", "질문이 수정되었습니다.");
        } catch (Exception e) {
            log.error("질문 수정 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "질문 수정 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/test/" + testId + "/questions";
    }

    // ─── 질문 삭제 ───────────────────────────────────────────

    @PostMapping("/{testId}/questions/{qId}/delete")
    public String questionDelete(
            @PathVariable("testId") Long testId,
            @PathVariable("qId") Long qId,
            RedirectAttributes redirectAttributes) {
        try {
            mdTestAdminService.deleteQuestion(qId);
            redirectAttributes.addFlashAttribute("successMsg", "질문이 삭제되었습니다.");
        } catch (Exception e) {
            log.error("질문 삭제 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "질문 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/test/" + testId + "/questions";
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
