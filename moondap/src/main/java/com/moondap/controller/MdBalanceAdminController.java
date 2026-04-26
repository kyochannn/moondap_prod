package com.moondap.controller;

import com.moondap.service.BalanceGameService;
import com.moondap.dto.BalanceGameDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/admin/balance")
@RequiredArgsConstructor
public class MdBalanceAdminController {

    private final BalanceGameService balanceGameService;

    @GetMapping("/list")
    public String balanceList(Model model) {
        Map<String, String> request = new HashMap<>();
        // spicyFilter를 "1"로 설정하여 매운맛 여부에 상관없이 모두 조회 (0이 아니면 필터링 건너뜀)
        request.put("spicyFilter", "1");
        // status를 명시하지 않거나 빈 값으로 두어 모든 상태 조회 (BalanceGameMapper.xml 대응)
        request.put("status", ""); 
        
        model.addAttribute("balanceList", balanceGameService.selectBalanceGameList(request, 0, 1000));
        return "admin/balance/balanceList";
    }

    @PostMapping("/{id}/delete")
    public String balanceDelete(@PathVariable("id") String id, 
                               @RequestParam(value = "oldOption1ImagePath", required = false) String oldOption1ImagePath,
                               @RequestParam(value = "oldOption2ImagePath", required = false) String oldOption2ImagePath,
                               RedirectAttributes redirectAttributes) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("id", id);
            params.put("oldOption1ImagePath", oldOption1ImagePath);
            params.put("oldOption2ImagePath", oldOption2ImagePath);
            
            balanceGameService.deleteBalanceGame(params);
            redirectAttributes.addFlashAttribute("successMsg", "밸런스 게임이 삭제되었습니다.");
        } catch (Exception e) {
            log.error("밸런스 게임 삭제 오류", e);
            redirectAttributes.addFlashAttribute("errorMsg", "밸런스 게임 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:/admin/balance/list";
    }
}
