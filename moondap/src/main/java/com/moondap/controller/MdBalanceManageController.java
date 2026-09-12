package com.moondap.controller;

import com.moondap.service.BalanceGameService;
import com.moondap.common.SecurityUtil;
import com.moondap.config.auth.PrincipalDetails;
import com.moondap.dto.BalanceGameDTO;
import com.moondap.dto.request.BalanceGameForm;
import com.moondap.dto.request.BalanceGameSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Slf4j
@Controller
@RequestMapping("/balanceGame/manage")
@RequiredArgsConstructor
public class MdBalanceManageController {

    private final BalanceGameService balanceGameService;

    @GetMapping("/list")
    public String balanceList(@AuthenticationPrincipal PrincipalDetails principalDetails, Model model) {
        boolean isAdmin = SecurityUtil.isAdmin();
        
        if (isAdmin) {
            model.addAttribute("balanceList",
                    balanceGameService.selectBalanceGameList(BalanceGameSearchRequest.manageList(null, 1000)));
            model.addAttribute("manageTitle", "전체 밸런스 게임 관리");
        } else {
            model.addAttribute("balanceList", balanceGameService.selectBalanceGameListByUser(principalDetails.getUsername()));
            model.addAttribute("manageTitle", "내가 만든 밸런스 게임");
        }
        
        return "admin/balance/balanceList";
    }

    @PostMapping("/{id}/delete")
    public String balanceDelete(@PathVariable("id") String id, 
                               @AuthenticationPrincipal PrincipalDetails principalDetails,
                               @RequestParam(value = "oldOption1ImagePath", required = false) String oldOption1ImagePath,
                               @RequestParam(value = "oldOption2ImagePath", required = false) String oldOption2ImagePath,
                               @RequestHeader(value = "Referer", required = false) String referer,
                               RedirectAttributes redirectAttributes) {
        
        // [보안] 권한 확인
        if (!balanceGameService.CheckMyTest(id)) {
            redirectAttributes.addFlashAttribute("errorMsg", "삭제 권한이 없습니다.");
            return "redirect:/balanceGame/manage/list";
        }

        try {
            BalanceGameForm form = new BalanceGameForm();
            form.setId(id);
            form.setOldOption1ImagePath(oldOption1ImagePath);
            form.setOldOption2ImagePath(oldOption2ImagePath);

            balanceGameService.deleteBalanceGame(form);
            redirectAttributes.addFlashAttribute("successMsg", "밸런스 게임이 삭제되었습니다.");
        } catch (Exception e) {
            log.error("밸런스 게임 삭제 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "밸런스 게임 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:" + (referer != null ? referer : "/balanceGame/manage/list");
    }
}
