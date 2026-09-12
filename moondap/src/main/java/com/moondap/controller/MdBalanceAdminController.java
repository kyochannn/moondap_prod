package com.moondap.controller;

import com.moondap.service.BalanceGameService;
import com.moondap.dto.BalanceGameDTO;
import com.moondap.dto.request.BalanceGameForm;
import com.moondap.dto.request.BalanceGameSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Slf4j
@Controller
@RequestMapping("/admin/balance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class MdBalanceAdminController {

    private final BalanceGameService balanceGameService;

    @GetMapping("/list")
    public String balanceList(Model model) {
        // 관리 화면이므로 상태·매운맛을 가리지 않고 전부 조회한다.
        model.addAttribute("balanceList",
                balanceGameService.selectBalanceGameList(BalanceGameSearchRequest.manageList(null, 1000)));
        return "admin/balance/balanceList";
    }

    @PostMapping("/{id}/delete")
    public String balanceDelete(@PathVariable("id") String id, 
                               @RequestParam(value = "oldOption1ImagePath", required = false) String oldOption1ImagePath,
                               @RequestParam(value = "oldOption2ImagePath", required = false) String oldOption2ImagePath,
                               RedirectAttributes redirectAttributes) {
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
        return "redirect:/admin/balance/list";
    }
}
