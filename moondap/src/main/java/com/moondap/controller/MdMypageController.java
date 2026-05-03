package com.moondap.controller;

import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.MdUserDTO;
import com.moondap.service.BalanceGameService;
import com.moondap.service.MdTestAdminService;
import com.moondap.service.StandardMdUserService;
import com.moondap.service.StatService;
import com.moondap.service.EgenTetoService;
import com.moondap.common.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MdMypageController {

    private final StandardMdUserService mdUserService;
    private final FileService fileService;
    private final BalanceGameService balanceGameService;
    private final MdTestAdminService mdTestAdminService;
    private final StatService statService;
    private final EgenTetoService egenTetoService;

    @GetMapping("/mypage")
    public String mypage(@AuthenticationPrincipal PrincipalDetails principalDetails, Model model, HttpSession session) {
        if (principalDetails == null) {
            return "redirect:/loginView";
        }
        
        String username = principalDetails.getUsername();
        
        // 마이페이지 메인으로 오면 수정 권한 세션 초기화
        session.removeAttribute("profileVerified");
        
        // 내 콘텐츠 조회
        model.addAttribute("myBalanceGames", balanceGameService.selectBalanceGameListByUser(username));
        model.addAttribute("myTests", mdTestAdminService.getTestListByUser(username));
        
        // 관리자인 경우 전체 서비스 통계 조회
        if ("ROLE_ADMIN".equals(principalDetails.getUser().getRole())) {
            long todayVisitCount = statService.getTodayVisitCount();
            long todayParticipationCount = statService.getTodayParticipationCount();
            
            // 전체 참여자 수 (밸런스 게임 + 에겐 테토 + 일반 테스트)
            long balanceGameTotalCount = balanceGameService.getTotalParticipantCount();
            long normalTestTotalCount = mdTestAdminService.getTotalPlayCount();
            java.util.Map<String, Object> egenStats = egenTetoService.getScoreStatistics();
            long egenTetoTotalCount = ((Number) egenStats.getOrDefault("totalCount", 0L)).longValue();
            
            model.addAttribute("todayVisitCount", todayVisitCount);
            model.addAttribute("todayParticipationCount", todayParticipationCount);
            model.addAttribute("totalParticipantCount", balanceGameTotalCount + normalTestTotalCount + egenTetoTotalCount);
        }
        
        model.addAttribute("user", principalDetails.getUser());
        return "mypage/mypage";
    }

    /**
     * 비밀번호 확인 페이지 (정보 수정 진입 전)
     */
    @GetMapping("/mypage/verify")
    public String verifyPasswordView(@AuthenticationPrincipal PrincipalDetails principalDetails, Model model) {
        if (principalDetails == null) return "redirect:/loginView";
        return "mypage/passwordCheck";
    }

    /**
     * 비밀번호 확인 처리
     */
    @PostMapping("/mypage/verify")
    public String verifyPasswordProc(@AuthenticationPrincipal PrincipalDetails principalDetails,
                                     @RequestParam("password") String password,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        if (principalDetails == null) return "redirect:/loginView";
        
        if (mdUserService.checkPassword(principalDetails.getUsername(), password)) {
            session.setAttribute("profileVerified", true);
            return "redirect:/mypage/edit";
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "비밀번호가 일치하지 않습니다.");
            return "redirect:/mypage/verify";
        }
    }

    /**
     * 회원 정보 수정 페이지
     */
    @GetMapping("/mypage/edit")
    public String editProfileView(@AuthenticationPrincipal PrincipalDetails principalDetails, 
                                  HttpSession session, 
                                  Model model) {
        if (principalDetails == null) return "redirect:/loginView";
        
        // 인증 여부 체크
        Boolean isVerified = (Boolean) session.getAttribute("profileVerified");
        if (isVerified == null || !isVerified) {
            return "redirect:/mypage/verify";
        }
        
        model.addAttribute("user", principalDetails.getUser());
        return "mypage/profileEdit";
    }

    /**
     * 회원 정보 수정 처리
     */
    @PostMapping("/mypage/edit")
    public String editProfileProc(@AuthenticationPrincipal PrincipalDetails principalDetails,
                                  MdUserDTO userForm,
                                  @RequestParam(value = "profileFile", required = false) MultipartFile profileFile,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        if (principalDetails == null) return "redirect:/loginView";
        
        // 인증 여부 체크
        Boolean isVerified = (Boolean) session.getAttribute("profileVerified");
        if (isVerified == null || !isVerified) {
            return "redirect:/mypage/verify";
        }
        
        MdUserDTO currentUser = principalDetails.getUser();
        try {
            userForm.setUsername(principalDetails.getUsername());
            
            if (profileFile != null && !profileFile.isEmpty()) {
                // 기존 프로필 이미지 삭제 (기본 이미지 아닌 경우에만)
                if (currentUser.getProfileImage() != null && !currentUser.getProfileImage().equals("default-profile-img.svg")) {
                    fileService.deleteProfile(currentUser.getProfileImage());
                }
                String savedFilename = fileService.uploadProfile(profileFile);
                userForm.setProfileImage(savedFilename);
            }
            
            // 비밀번호는 여기서 처리하지 않음 (기존 비밀번호 유지)
            userForm.setPassword(null); // 서비스에서 null 체크 후 기존 비번 유지하도록 처리됨
            
            mdUserService.updateUser(userForm);
            
            // 세션 갱신
            currentUser.setNickname(userForm.getNickname());
            currentUser.setEmail(userForm.getEmail());
            currentUser.setBio(userForm.getBio());
            if (userForm.getProfileImage() != null) {
                currentUser.setProfileImage(userForm.getProfileImage());
            }
            
            redirectAttributes.addFlashAttribute("successMsg", "회원 정보가 성공적으로 수정되었습니다.");
        } catch (Exception e) {
            log.error("회원 정보 수정 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/mypage/edit";
        }
        
        return "redirect:/mypage";
    }

    /**
     * 비밀번호 변경 처리 (AJAX)
     */
    @PostMapping("/mypage/updatePassword")
    @ResponseBody
    public Map<String, Object> updatePassword(@AuthenticationPrincipal PrincipalDetails principalDetails,
                                              @RequestParam("currentPassword") String currentPassword,
                                              @RequestParam("newPassword") String newPassword) {
        Map<String, Object> result = new HashMap<>();
        if (principalDetails == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        try {
            if (!mdUserService.checkPassword(principalDetails.getUsername(), currentPassword)) {
                result.put("success", false);
                result.put("message", "현재 비밀번호가 일치하지 않습니다.");
                return result;
            }

            mdUserService.updatePassword(principalDetails.getUsername(), newPassword);
            result.put("success", true);
            result.put("message", "비밀번호가 성공적으로 변경되었습니다.");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 회원 탈퇴 처리
     */
    @PostMapping("/mypage/withdraw")
    @ResponseBody
    public Map<String, Object> withdrawProc(@AuthenticationPrincipal PrincipalDetails principalDetails,
                                            @RequestParam("password") String password,
                                            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        if (principalDetails == null) {
            result.put("success", false);
            result.put("message", "로그인이 필요합니다.");
            return result;
        }

        try {
            if (!mdUserService.checkPassword(principalDetails.getUsername(), password)) {
                result.put("success", false);
                result.put("message", "비밀번호가 일치하지 않습니다.");
                return result;
            }

            // 회원 상태를 'deleted'로 변경 (소프트 딜리트)
            mdUserService.deleteUser(principalDetails.getUsername());
            
            // 세션 무효화 (로그아웃 처리)
            session.invalidate();
            
            result.put("success", true);
            result.put("message", "회원 탈퇴가 완료되었습니다. 그동안 이용해주셔서 감사합니다.");
        } catch (Exception e) {
            log.error("회원 탈퇴 처리 중 오류", e);
            result.put("success", false);
            result.put("message", "처리 중 오류가 발생했습니다: " + e.getMessage());
        }
        return result;
    }
}
