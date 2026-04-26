package com.moondap.controller;

import com.moondap.dto.MdTestCategoryDTO;
import com.moondap.service.MdTestCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/admin/category")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class MdCategoryAdminController {

    private final MdTestCategoryService categoryService;

    @GetMapping("/list")
    public String categoryList(Model model) {
        List<MdTestCategoryDTO> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);
        return "admin/category/categoryList";
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createCategory(@ModelAttribute MdTestCategoryDTO dto) {
        try {
            categoryService.addCategory(dto);
            return ResponseEntity.ok(Map.of("success", true, "message", "카테고리가 생성되었습니다."));
        } catch (Exception e) {
            log.error("카테고리 생성 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/update")
    @ResponseBody
    public ResponseEntity<?> updateCategory(@ModelAttribute MdTestCategoryDTO dto) {
        try {
            categoryService.updateCategory(dto);
            return ResponseEntity.ok(Map.of("success", true, "message", "카테고리가 수정되었습니다."));
        } catch (Exception e) {
            log.error("카테고리 수정 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteCategory(@PathVariable("id") Long id) {
        try {
            categoryService.deleteCategory(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "카테고리가 삭제되었습니다."));
        } catch (Exception e) {
            log.error("카테고리 삭제 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
