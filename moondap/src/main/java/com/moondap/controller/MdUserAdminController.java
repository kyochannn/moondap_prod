package com.moondap.controller;

import com.moondap.dto.MdUserDTO;
import com.moondap.service.StandardMdUserService;
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
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class MdUserAdminController {

    private final StandardMdUserService userService;

    @GetMapping("/list")
    public String userList(Model model) {
        List<MdUserDTO> users = userService.getAllUsers();
        model.addAttribute("users", users);
        return "admin/user/userList";
    }

    private final com.moondap.common.FileService fileService;

    @PostMapping("/update")
    @ResponseBody
    public ResponseEntity<?> updateUser(@ModelAttribute MdUserDTO dto,
                                       @RequestParam(value = "profileFile", required = false) org.springframework.web.multipart.MultipartFile profileFile) {
        try {
            if (profileFile != null && !profileFile.isEmpty()) {
                String savedFilename = fileService.uploadProfile(profileFile);
                dto.setProfileImage(savedFilename);
            }
            userService.updateUser(dto);
            return ResponseEntity.ok(Map.of("success", true, "message", "사용자 정보가 수정되었습니다."));
        } catch (Exception e) {
            log.error("사용자 수정 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/delete/{username}")
    @ResponseBody
    public ResponseEntity<?> deleteUser(@PathVariable("username") String username) {
        try {
            userService.deleteUser(username);
            return ResponseEntity.ok(Map.of("success", true, "message", "사용자 처리가 완료되었습니다."));
        } catch (Exception e) {
            log.error("사용자 삭제 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/suspend/{username}")
    @ResponseBody
    public ResponseEntity<?> toggleSuspend(@PathVariable("username") String username) {
        try {
            userService.toggleSuspend(username);
            return ResponseEntity.ok(Map.of("success", true, "message", "사용자 상태가 변경되었습니다."));
        } catch (Exception e) {
            log.error("사용자 정지 오류", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
