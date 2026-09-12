package com.moondap.controller;

import lombok.RequiredArgsConstructor;

import com.moondap.common.exception.UserMessageException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.moondap.common.AnonymousIdentity;
import com.moondap.common.CommonUtil;
import com.moondap.common.SecurityUtil;
import com.moondap.dto.BalanceGameCommentDTO;
import com.moondap.dto.BalanceGameDTO;
import com.moondap.dto.SeoMetaDTO;
import com.moondap.dto.request.AdjacentGameRequest;
import com.moondap.dto.request.BalanceGameForm;
import com.moondap.dto.request.BalanceGameSearchRequest;
import com.moondap.dto.request.CommentDeleteRequest;
import com.moondap.dto.request.CommentLikeRequest;
import com.moondap.dto.request.GameIdRequest;
import com.moondap.dto.request.CommentRequest;
import com.moondap.dto.request.VoteRequest;

import jakarta.validation.Valid;
import com.moondap.service.BalanceGameService;

import com.moondap.service.MdTestCategoryService;
import com.moondap.service.MdTestUserService;
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
@RequiredArgsConstructor
public class BalanceGameController {

	private final BalanceGameService balanceGameService;

	private final MdTestCategoryService categoryService;

	private final MdTestUserService mdTestUserService;

	/**
	 * 밸런스 게임 리스트 화면
	 * 
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@GetMapping("/selectBalanceGameListView")
	public String selectBalanceGameListView(Model model) throws Exception {

		// [수정] 이전에는 put("isSpicy", ...) 였으나 서비스는 "spicyFilter" 를 읽어 값이 버려졌고,
		// status 를 넣지 않아 draft 가 공개 목록에 섞여 나갈 수 있었다.
		List<BalanceGameDTO> balanceGameList =
				balanceGameService.selectBalanceGameList(BalanceGameSearchRequest.publicList(10));

		model.addAttribute("balanceGameList", balanceGameList);
		model.addAttribute("categories", categoryService.getActiveCategories());
		model.addAttribute("seo", SeoMetaDTO.of(
				"밸런스 게임 모음 - 문답",
				"둘 중 하나만 고를 수 있다면? 연애, 일상, 매운맛까지 다양한 주제의 밸런스 게임을 즐기고 다른 사람들의 선택 비율을 확인해 보세요."));

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
	public ResponseEntity<List<BalanceGameDTO>> selectBalanceGameList(
			@RequestBody BalanceGameSearchRequest request) {

		log.info("조회 요청 데이터: {}", request);

		// 상태를 지정하지 않은 요청은 공개 조회로 간주해 active 만 보여준다.
		// (null 로 두면 매퍼가 상태 조건을 걸지 않아 draft 까지 나간다)
		if (request.getStatus() == null) {
			request.setStatus("active");
		}

		List<BalanceGameDTO> balanceGameList = balanceGameService.selectBalanceGameList(request);

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
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "spicyFilter", required = false) String spicyFilter,
			@RequestParam(value = "category", required = false) String category,
			HttpServletRequest request,
			Model model) throws Exception {

		String baseUrl = request.getRequestURL().toString().replace(request.getRequestURI(), "");
		model.addAttribute("baseUrl", baseUrl);
		model.addAttribute("fullUrl", request.getRequestURL().toString() + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));

		log.info("id :::::::::::{}", id);
		log.info("spicyFilter :::::::::::{}", spicyFilter);
		log.info("category :::::::::::{}", category);

		// 리스트에서 조회 시 get 방식으로 넘겨주는데, 게임시작 버튼을 눌렀을 때 어떻게 처리해야 할지 고민 후 구현부터 시작!!

		BalanceGameDTO balanceGame = balanceGameService.selectBalanceGame(id, spicyFilter, category);

		// 데이터가 없는 경우(null) 처리
		if (balanceGame == null) {
			throw new UserMessageException("해당 게임을 찾을 수 없습니다.");
		}

		model.addAttribute("balanceGame", balanceGame);
		
		// 비공개/초안 상태일 경우 관리자나 작성자만 접근 가능.
		// 대소문자를 구분하지 않는다 — 매퍼의 WHERE status = 'active' 는 MySQL 콜레이션상
		// 'ACTIVE' 도 통과시키므로, 여기서만 구분하면 목록에는 보이는데 상세만 막힌다.
		if (!"active".equalsIgnoreCase(balanceGame.getStatus())) {
			if (!balanceGameService.CheckMyTest(balanceGame.getId())) {
				throw new UserMessageException("해당 게임에 접근할 권한이 없습니다.");
			}
		}
		
		model.addAttribute("currentUserId", SecurityUtil.getCurrentUsername());
		model.addAttribute("isAnonymous", !SecurityUtil.isAuthenticated());
		model.addAttribute("categories", categoryService.getActiveCategories());

		// [이탈 방지] 투표 직후가 관여도가 가장 높은 순간인데, 이 화면에서 나갈 길이
		// '이전/다음 질문'과 '리스트로 이동' 뿐이라 사용자가 밸런스 게임 안에서만 돌았다.
		// 테스트 결과·에겐테토 화면에는 이미 있는 추천 섹션이 여기만 빠져 있었다.
		//
		// 밸런스 게임은 일부러 추천하지 않는다. 다음 질문 버튼이 이미 그 역할을 하고,
		// 여기서 또 추천하면 같은 루프를 넓히는 셈이다.
		// type='NORMAL' 은 매퍼에서 심리테스트와 에겐테토를 함께 반환한다.
		model.addAttribute("popularNormalTests",
				mdTestUserService.getAllContentList("all", "popular", "NORMAL", 0, 3));

		// 이 페이지는 id 파라미터가 콘텐츠를 결정하므로 canonical 에서 뗄 수 없다.
		// 반면 spicyFilter·category 는 "다음 게임"을 고르는 탐색용 파라미터일 뿐이라
		// 같은 게임을 가리키는 중복 URL 을 만든다. id 만 남긴다.
		SeoMetaDTO seo = SeoMetaDTO.of(
				balanceGame.getTitle() + " - 밸런스 게임 | 문답",
				balanceGame.getTitle() + " 당신의 선택은? "
						+ balanceGame.getOption1Text() + " vs " + balanceGame.getOption2Text()
						+ ". 다른 사람들의 선택 비율과 댓글을 확인해 보세요.");
		seo.setCanonical("/balanceGame/selectBalanceGameView?id="
				+ UriUtils.encodeQueryParam(balanceGame.getId(), StandardCharsets.UTF_8));
		// 업로드 이미지는 파일명만 저장돼 있고 /uploads/ 아래에서 서빙된다.
		if (balanceGame.getOption1ImagePath() != null && !balanceGame.getOption1ImagePath().isBlank()) {
			seo.setImage("/uploads/" + balanceGame.getOption1ImagePath());
		}
		seo.setOgType("article");
		model.addAttribute("seo", seo);

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
	public String nextOrPrevBalanceGameIdSelect(@Valid @RequestBody AdjacentGameRequest request) throws Exception {
		// 예외를 삼키지 않는다. GlobalExceptionHandler 가 500 JSON 으로 응답하고
		// 화면의 error 콜백이 사용자에게 알린다.
		return balanceGameService.nextOrPrevBalanceGameIdSelect(request);
	}

	/**
	 * 밸런스 게임 투표 현황 조회 (실시간 갱신용)
	 * 
	 * @param request
	 * @return
	 */
	@PostMapping("/getVoteStatus")
	@ResponseBody
	public BalanceGameDTO getVoteStatus(@Valid @RequestBody GameIdRequest request) throws Exception {
		return balanceGameService.selectBalanceGame(request.getId(), null, null);
	}

	/**
	 * 밸런스 게임 투표
	 * 
	 * @param request
	 * @return
	 */
	@PostMapping("/vote")
	@ResponseBody
	public BalanceGameDTO voteBalanceGame(@Valid @RequestBody VoteRequest request,
			HttpServletRequest servletRequest) throws Exception {
		log.info("voteBalanceGame ::::::::: ");

		// 참여수 증가는 서비스가 실제 집계된 투표에 대해서만 수행한다.
		return balanceGameService.vote(request, resolveVoterKey(servletRequest));
	}

	/**
	 * 투표자 식별자를 만든다.
	 *
	 * 로그인 사용자는 계정 기준이라 기기를 바꿔도 한 번만 투표할 수 있다.
	 * 비로그인 사용자는 IP 로 묶는다. 공유 IP(회사·학교) 환경에서는 과차단될 수 있으나,
	 * 투표수가 인기 순위를 결정하므로 과차단 쪽을 택했다.
	 */
	private String resolveVoterKey(HttpServletRequest servletRequest) {
		String username = SecurityUtil.getCurrentUsername();
		if (username != null) {
			return "u:" + username;
		}
		return "ip:" + CommonUtil.getClientIp(servletRequest);
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
	public List<BalanceGameCommentDTO> selectBalanceGameComment(@Valid @RequestBody GameIdRequest request,
			HttpServletRequest servletRequest) throws Exception {

		log.info("selectBalanceGameComment ::::::::::: {}", request.getId());

		// 요청자 기준으로 likedByMe / deletable 을 채워 내려준다.
		return balanceGameService.selectBalanceGameComment(request.getId(), resolveVoterKey(servletRequest));
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
	public BalanceGameCommentDTO insertBalanceGameComment(@Valid @RequestBody CommentRequest request,
			HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws Exception {
		log.info("insertBalanceGameMessage ::::::::: ");

		// [보안] 작성자는 서버가 결정한다. CommentRequest 에 userId 를 바인딩하지 않으므로
		// 요청 본문에 userId 를 실어 보내도 무시된다.
		String currentUsername = SecurityUtil.getCurrentUsername();
		if (currentUsername != null) {
			request.setUserId(currentUsername);
			request.setNickname(currentUsername);
			request.setAnonId(null);
		} else {
			// 익명 댓글: 계정은 없지만 "같은 브라우저"는 식별할 수 있어야
			// 본인이 자기 댓글을 지울 수 있다. 작성 시점에만 토큰을 발급한다.
			request.setUserId(null);
			request.setAnonId(AnonymousIdentity.getOrCreate(servletResponse));
		}

		// 금칙어 위반은 UserMessageException 으로 올라가 400 과 함께
		// 사용자에게 보여줄 메시지가 전달된다.
		return balanceGameService.insertBalanceGameComment(request, resolveVoterKey(servletRequest));
	}

	@PostMapping("/updateBalanceGameCommentLikeCount")
	@ResponseBody
	public BalanceGameCommentDTO updateBalanceGameCommentLikeCount(
			@Valid @RequestBody CommentLikeRequest request,
			HttpServletRequest servletRequest) throws Exception {
		log.info("updateBalanceGameCommentLikeCount ::::::::: ");

		// 누를지 취소할지는 서버의 기록이 결정한다. 요청의 setting 값은 쓰지 않는다.
		return balanceGameService.toggleCommentLike(request, resolveVoterKey(servletRequest));
	}

	@PostMapping("/deleteBalanceGameComment")
	@ResponseBody
	public Map<String, Object> deleteBalanceGameComment(@Valid @RequestBody CommentDeleteRequest request)
			throws Exception {
		log.info("deleteBalanceGameComment ::::::::: ");

		Map<String, Object> rtnMap = new HashMap<>();
		// 권한 판정(관리자 · 로그인 작성자 · 익명 토큰 소유자)은 서비스가 한다.
		String result = balanceGameService.deleteSingleComment(request.getNo());
		rtnMap.put("flag", result.toLowerCase());
		return rtnMap;
	}

	/**
	 * 밸런스 게임 등록 화면
	 * 
	 * @return
	 */
	@GetMapping("/insertBalanceGameView")
	public String insertBalanceGameView(Model model) {
		// [보안] 로그인하지 않은 사용자는 로그인 페이지로 리다이렉트
		if (!SecurityUtil.isAuthenticated()) {
			return "redirect:/loginView";
		}
		model.addAttribute("categories", categoryService.getActiveCategories());
		return "balanceGame/insertBalanceGame";
	}

	/**
	 * 밸런스 게임 등록
	 * 
	 * @return
	 */
	@PostMapping("/insertBalanceGame")
	@ResponseBody
	public Map<String, Object> insertBalanceGame(@Valid @ModelAttribute BalanceGameForm form,
			@RequestParam("option1Image") MultipartFile option1Image,
			@RequestParam("option2Image") MultipartFile option2Image) throws Exception {

		Map<String, Object> rtnMap = new HashMap<String, Object>();

		// [보안] 로그인하지 않은 사용자는 저장 불가
		if (!SecurityUtil.isAuthenticated()) {
			rtnMap.put("flag", "fail");
			rtnMap.put("message", "로그인 후 이용 가능합니다.");
			return rtnMap;
		}

		// 검증 실패(금칙어·길이 초과 등)는 UserMessageException 으로 올라가
		// 400 과 함께 구체적인 사유가 전달된다. 여기서 삼키면 사유가 사라진다.
		String balanceGameId = balanceGameService.insertBalanceGame(form, option1Image, option2Image);

		rtnMap.put("flag", CommonUtil.isNotNull(balanceGameId) ? "success" : "fail");
		rtnMap.put("balanceGameId", balanceGameId);
		return rtnMap;
	}

	/**
	 * 밸런스 게임 수정 화면
	 * 
	 * @return
	 */
	@GetMapping("/updateBalanceGameView")
	public String updateBalanceGameView(@RequestParam("id") String id, Model model) throws Exception {
		log.info("updateBalanceGameView ID :::::::::::{}", id);
		
		// [보안] 권한 확인
		if (!balanceGameService.CheckMyTest(id)) {
			// 이전에는 "redirect:/history.back()" 을 반환해 존재하지 않는 경로로 보냈다.
			// 사용자는 404 를 보게 되고 실제 원인(권한 없음)은 알 수 없었다.
			throw new UserMessageException("해당 게임을 수정할 권한이 없습니다.");
		}

		BalanceGameDTO balanceGame = balanceGameService.selectBalanceGame(id, null, null);
		// 데이터가 없는 경우(null) 처리
		if (balanceGame == null) {
			throw new UserMessageException("수정할 게임을 찾을 수 없습니다.");
		}

		model.addAttribute("balanceGame", balanceGame);
		model.addAttribute("categories", categoryService.getActiveCategories());
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
	public Map<String, Object> updateBalanceGame(@Valid @ModelAttribute BalanceGameForm form,
			@RequestParam(value = "option1Image", required = false) MultipartFile option1Image,
			@RequestParam(value = "option2Image", required = false) MultipartFile option2Image) throws Exception {
		log.info("updateBalanceGame controller :::::::::::");

		Map<String, Object> rtnMap = new HashMap<String, Object>();

		// 권한 없음·검증 실패는 UserMessageException 으로 올라간다.
		String balanceGameId = balanceGameService.updateBalanceGame(form, option1Image, option2Image);

		rtnMap.put("flag", CommonUtil.isNotNull(balanceGameId) ? "success" : "fail");
		rtnMap.put("balanceGameId", balanceGameId);
		return rtnMap;
	}

	/**
	 * 밸런스 게임 삭제
	 * 
	 * @param params
	 * @return
	 */
	@PostMapping("/deleteBalanceGame")
	@ResponseBody
	public Map<String, Object> deleteBalanceGame(@ModelAttribute BalanceGameForm form) throws Exception {
		log.info("deleteBalanceGame controller :::::::::::");

		Map<String, Object> rtnMap = new HashMap<String, Object>();

		String balanceGameId = balanceGameService.deleteBalanceGame(form);
		log.info("balanceGame: {}", balanceGameId);

		rtnMap.put("flag", CommonUtil.isNotNull(balanceGameId) ? "success" : "fail");
		rtnMap.put("balanceGameId", balanceGameId);
		return rtnMap;
	}

}
