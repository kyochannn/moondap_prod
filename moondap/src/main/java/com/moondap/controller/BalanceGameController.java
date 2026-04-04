package com.moondap.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import com.moondap.common.CommonUtil;
import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;
import com.moondap.service.BalanceGameService;

import lombok.extern.slf4j.Slf4j;

/**
 * /balanceGame 경로로 요청이 왔을 때 처리
 * [GET] - 밸런스 게임 목록 화면
 * [GET] - 밸런스 게임 등록 화면
 * [POST] - 밸런스 게임 등록 처리
 * [GET] - 밸런스 게임 조회 화면
 * [GET] - 밸런스 게임 수정 화면
 * [POST] - 밸런스 게임 수정 처리
 * [POST] - 밸런스 게임 삭제 처리
 * 
 * 예외는 GlobalExceptionHandler에서 처리
 */
@Slf4j
@Controller
@RequestMapping("/balanceGame")
public class BalanceGameController {

	@Autowired
	private BalanceGameService balanceGameService;

	/**
	 * 밸런스 게임 리스트 화면
	 * 
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@GetMapping("/selectBalanceGameListView")
	public String selectBalanceGameListView(Model model) throws Exception {

		Map<String, String> request = new HashMap<String, String>();
		request.put("isSpicy", "0");
		List<BalanceGameDTO> balanceGameList = balanceGameService.selectBalanceGameList(request, 0, 10);

		model.addAttribute("balanceGameList", balanceGameList);

		return "/balanceGame/selectBalanceGameList";
	}

	/**
	 * 밸런스 게임 리스트 조회
	 * 
	 * @param request
	 * @return
	 * @throws Exception
	 */
	@PostMapping("/selectBalanceGameList")
	public ResponseEntity<List<BalanceGameDTO>> selectBalanceGameList(@RequestBody Map<String, String> request,
			Model model) {

		log.info("조회 요청 데이터: {}", request);

		int offset = Integer.parseInt(request.getOrDefault("offset", "0"));
		int limit = Integer.parseInt(request.getOrDefault("limit", "10"));

		List<BalanceGameDTO> balanceGameList = balanceGameService.selectBalanceGameList(request, offset, limit);

		// 데이터가 없을 경우 204 No Content 혹은 빈 리스트 반환
		if (balanceGameList.isEmpty()) {
			return ResponseEntity.noContent().build();
		}

		return ResponseEntity.ok(balanceGameList);
	}

	/**
	 * 밸런스 게임 조회 화면
	 * /balanceGame/selectBalanceGameView?id={}
	 * 
	 * @param id
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@GetMapping("/selectBalanceGameView")
	public String selectBalanceGameView(
			@RequestParam(required = false) String id,
			@RequestParam(required = false) String spicyFilter,
			@RequestParam(required = false) String category,
			HttpServletRequest request,
			Model model) throws Exception {

		String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), "");
		model.addAttribute("baseUrl", baseUrl);
		model.addAttribute("fullUrl", request.getRequestURL().toString() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));

		System.out.println("id :::::::::::" + id);
		System.out.println("spicyFilter :::::::::::" + spicyFilter);
		System.out.println("category :::::::::::" + category);

		// 리스트에서 조회 시 get 방식으로 넘겨주는데, 게임시작 버튼을 눌렀을 때 어떻게 처리해야 할지 고민 후 구현부터 시작!!

		BalanceGameDTO balanceGame = balanceGameService.selectBalanceGame(id, spicyFilter, category);

		// 데이터가 없는 경우(null) 처리
		if (balanceGame == null) {
			throw new RuntimeException("해당 게임을 찾을 수 없습니다.");
		}

		model.addAttribute("balanceGame", balanceGame);
		return "balanceGame/selectBalanceGame";
	}

	/**
	 * 다음 또는 이전 밸런스 게임 아이디 조회
	 * 
	 * @param request
	 * @return
	 */
	@PostMapping("/nextOrPrevBalanceGameIdSelect")
	@ResponseBody
	public String nextOrPrevBalanceGameIdSelect(@RequestBody Map<String, String> request) {

		try {
			String balanceGameId = balanceGameService.nextOrPrevBalanceGameIdSelect(request);

			return balanceGameId;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 밸런스 게임 투표 현황 조회 (실시간 갱신용)
	 * 
	 * @param request
	 * @return
	 */
	@PostMapping("/getVoteStatus")
	@ResponseBody
	public BalanceGameDTO getVoteStatus(@RequestBody Map<String, String> request) {
		try {
			String id = request.get("id");
			BalanceGameDTO balanceGame = balanceGameService.selectBalanceGame(id, null, null);
			return balanceGame;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 밸런스 게임 투표
	 * 
	 * @param request
	 * @return
	 */
	@PostMapping("/vote")
	@ResponseBody
	public BalanceGameDTO voteBalanceGame(@RequestBody Map<String, String> request) {
		System.out.println("voteBalanceGame ::::::::: ");

		try {
			BalanceGameDTO balanceGame = balanceGameService.vote(request);

			return balanceGame;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 
	 * 밸런스 게임 댓글 조회
	 * 
	 * @param id
	 * @return
	 */
	@PostMapping("/selectBalanceGameComment")
	@ResponseBody
	public List<BalanceGameCommentDTO> selectBalanceGameComment(@RequestBody Map<String, String> request) {

		String id = request.get("id");
		System.out.println("selectBalanceGameComment ::::::::::: " + id);

		try {
			List<BalanceGameCommentDTO> balanceGameCommentList = balanceGameService.selectBalanceGameComment(id);

			return balanceGameCommentList;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 
	 * 밸런스 게임 댓글 등록
	 * 
	 * @param request
	 * @return
	 */
	@PostMapping("/insertBalanceGameComment")
	@ResponseBody
	public List<BalanceGameCommentDTO> insertBalanceGameComment(@RequestBody Map<String, String> request) {
		System.out.println("insertBalanceGameMessage ::::::::: ");

		try {
			List<BalanceGameCommentDTO> balanceGameCommenList = balanceGameService.insertBalanceGameComment(request);

			return balanceGameCommenList;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	@PostMapping("/updateBalanceGameCommentLikeCount")
	@ResponseBody
	public List<BalanceGameCommentDTO> updateBalanceGameCommentLikeCount(@RequestBody Map<String, String> request) {
		System.out.println("updateBalanceGameCommentLikeCount ::::::::: ");

		try {
			List<BalanceGameCommentDTO> balanceGameCommenList = balanceGameService
					.updateBalanceGameCommentLikeCount(request);

			return balanceGameCommenList;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 밸런스 게임 등록 화면
	 * 
	 * @return
	 */
	@GetMapping("/insertBalanceGameView")
	public String insertBalanceGameView() {
		return "balanceGame/insertBalanceGame";
	}

	/**
	 * 밸런스 게임 등록
	 * 
	 * @return
	 */
	@PostMapping("/insertBalanceGame")
	@ResponseBody
	public Map<String, Object> insertBalanceGame(@RequestParam Map<String, String> params,
			@RequestParam MultipartFile option1Image, @RequestParam MultipartFile option2Image) {

		String flag = "success";
		Map<String, Object> rtnMap = new HashMap<String, Object>();

		try {
			String balanceGameId = balanceGameService.insertBalanceGame(params, option1Image, option2Image);

			if (CommonUtil.isNotNull(balanceGameId)) {
				rtnMap.put("balanceGameId", balanceGameId);
			} else {
				flag = "fail";
			}
			rtnMap.put("flag", flag);

			return rtnMap;
		} catch (Exception e) {
			e.printStackTrace();

			flag = "fail";
			rtnMap.put("flag", flag);

			return rtnMap;
		}
	}

	/**
	 * 밸런스 게임 수정 화면
	 * 
	 * @return
	 */
	@GetMapping("/updateBalanceGameView")
	public String updateBalanceGameView(@RequestParam String id, Model model) throws Exception {
		System.out.println("updateBalanceGameView ID :::::::::::" + id);
		BalanceGameDTO balanceGame = balanceGameService.selectBalanceGame(id, null, null);
		// 데이터가 없는 경우(null) 처리
		if (balanceGame == null) {
			throw new RuntimeException("수정할 게임을 찾을 수 없습니다.");
		}

		model.addAttribute("balanceGame", balanceGame);
		return "balanceGame/updateBalanceGame";
	}

	/**
	 * 밸런스 게임 수정
	 * 
	 * @param params
	 * @param option1Image
	 * @param option2Image
	 * @return
	 */
	@PostMapping("/updateBalanceGame")
	@ResponseBody
	public Map<String, Object> updateBalanceGame(@RequestParam Map<String, String> params,
			@RequestParam(required = false) MultipartFile option1Image,
			@RequestParam(required = false) MultipartFile option2Image) {
		System.out.println("updateBalanceGame controller :::::::::::");

		String flag = "success";
		Map<String, Object> rtnMap = new HashMap<String, Object>();

		try {
			String balanceGameId = balanceGameService.updateBalanceGame(params, option1Image, option2Image);

			if (CommonUtil.isNotNull(balanceGameId)) {
				rtnMap.put("balanceGameId", balanceGameId);
			} else {
				flag = "fail";
			}
			rtnMap.put("flag", flag);

			return rtnMap;
		} catch (Exception e) {
			e.printStackTrace();

			flag = "fail";
			rtnMap.put("flag", flag);

			return rtnMap;
		}
	}

	/**
	 * 밸런스 게임 삭제
	 * 
	 * @param params
	 * @return
	 */
	@PostMapping("/deleteBalanceGame")
	@ResponseBody
	public Map<String, Object> deleteBalanceGame(@RequestParam Map<String, String> params) {
		System.out.println("deleteBalanceGame controller :::::::::::");

		String flag = "success";
		Map<String, Object> rtnMap = new HashMap<String, Object>();

		try {
			String balanceGameId = balanceGameService.deleteBalanceGame(params);
			String balanceGameCommentId = balanceGameService.deleteBalanceGameComment(params.get("id"));

			System.out.println("balanceGame: " + balanceGameId +
					", balanceGameCommentId: " + balanceGameCommentId);

			if (CommonUtil.isNotNull(balanceGameId) && CommonUtil.isNotNull(balanceGameCommentId)) {
				rtnMap.put("balanceGameId", balanceGameId);
			} else {
				flag = "fail";
			}
			rtnMap.put("flag", flag);

			return rtnMap;
		} catch (Exception e) {
			e.printStackTrace();

			flag = "fail";
			rtnMap.put("flag", flag);

			return rtnMap;
		}
	}

	/**
	 * 여기부터 개발 시작!
	 * 
	 */

	// 오류 페이지 TEST

	/**
	 * 401 TEST
	 * 
	 * @return
	 */
	@GetMapping("/401")
	public String get401() {
		return "error/401";
	}

	/**
	 * 403 TEST
	 * 
	 * @return
	 */
	@GetMapping("/403")
	public String get403() {
		return "error/403";
	}

	/**
	 * 404 TEST
	 * 
	 * @return
	 */
	@GetMapping("/404")
	public String get404() {
		return "error/404";
	}

	/**
	 * 500 TEST
	 * 
	 * @return
	 */
	@GetMapping("/500")
	public String get500() {
		return "error/500";
	}
}
