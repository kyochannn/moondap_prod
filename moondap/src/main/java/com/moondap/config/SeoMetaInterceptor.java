package com.moondap.config;

import java.util.List;
import java.util.Map;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.moondap.dto.SeoMetaDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 모든 뷰 응답에 SEO 메타 정보를 채워 넣는다.
 *
 * <p>해결하는 문제는 두 가지다.
 *
 * <ol>
 *   <li><b>canonical 부재</b> — 결과 페이지는 {@code ?resultCode=&score=&answers=} 조합으로
 *       URL 이 사실상 무한히 생성된다. canonical 이 없으면 같은 테스트 하나가 수백 개의
 *       얇은 중복 페이지로 색인돼 사이트 전체가 '가치 없는 콘텐츠'로 평가된다.</li>
 *   <li><b>중간 상태 페이지의 색인</b> — 질문지·결과·로그인·가입 화면은 읽을거리가 없다.
 *       이런 페이지가 색인의 다수를 차지하면 콘텐츠 품질 점수가 떨어진다.
 *       robots.txt 로 막지 않고 noindex 를 쓰는 이유는, 이미 색인된 URL 을 빼려면
 *       크롤러가 페이지에 접근해 이 태그를 읽을 수 있어야 하기 때문이다.</li>
 * </ol>
 *
 * <p>컨트롤러가 {@code seo} 모델 속성을 미리 넣어두면 그 값이 우선하고, 비어 있는
 * 항목만 여기서 보충한다.
 */
public class SeoMetaInterceptor implements HandlerInterceptor {

    /** 모델 속성 이름. main_layout.html 이 ${seo.*} 로 참조한다. */
    public static final String MODEL_ATTRIBUTE = "seo";

    private static final String DEFAULT_TITLE = "문답: 테스트로 만나는 또 다른 나";
    private static final String DEFAULT_DESCRIPTION =
            "심리테스트, 밸런스 게임, 성격 검사를 한곳에서. 나도 몰랐던 나의 모습을 문답(moondap)에서 확인해 보세요.";
    private static final String DEFAULT_IMAGE = "/assets/img/logo.webp";

    /** 검색 결과에 노출될 이유가 없는 경로. 읽을거리가 없거나 로그인이 필요한 화면들. */
    private static final List<String> NOINDEX_PATTERNS = List.of(
            "/test/*/questions",
            "/test/*/result",
            "/test/manage/**",
            "/egenTeto/questions",
            "/egenTeto/result",
            "/egenTeto/select",
            "/egenTeto/start",
            "/balanceGame/manage/**",
            "/balanceGame/insertBalanceGameView",
            "/balanceGame/updateBalanceGameView",
            "/loginView",
            "/joinSelectView",
            "/joinView",
            "/joinCompleteView",
            "/joinViewAfterError",
            "/mypage",
            "/mypage/**",
            "/admin/**",
            "/error",
            "/error/**"
    );

    /** 구글이 실제로 표시하는 description 길이. 넘는 부분은 잘려서 의미가 없다. */
    private static final int MAX_DESCRIPTION_LENGTH = 160;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final String baseUrl;

    public SeoMetaInterceptor(String baseUrl) {
        // 뒤에 슬래시가 붙어 있으면 requestURI 와 합칠 때 "//" 가 된다.
        this.baseUrl = StringUtils.trimTrailingCharacter(baseUrl, '/');
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request,
                           @NonNull HttpServletResponse response,
                           @NonNull Object handler,
                           @Nullable ModelAndView modelAndView) {

        if (modelAndView == null) {
            return;
        }

        String viewName = modelAndView.getViewName();
        // @ResponseBody(viewName == null), 리다이렉트, AJAX 프래그먼트 응답("view :: #id")은
        // <head> 를 렌더링하지 않으므로 메타를 채울 필요가 없다.
        if (viewName == null || viewName.startsWith("redirect:") || viewName.contains("::")) {
            return;
        }

        Map<String, Object> model = modelAndView.getModel();
        Object existing = model.get(MODEL_ATTRIBUTE);
        SeoMetaDTO seo = (existing instanceof SeoMetaDTO) ? (SeoMetaDTO) existing : new SeoMetaDTO();

        String uri = request.getRequestURI();

        seo.setTitle(StringUtils.hasText(seo.getTitle()) ? squash(seo.getTitle(), 70) : DEFAULT_TITLE);
        seo.setDescription(StringUtils.hasText(seo.getDescription())
                ? squash(seo.getDescription(), MAX_DESCRIPTION_LENGTH)
                : DEFAULT_DESCRIPTION);

        // 쿼리스트링은 기본적으로 버린다. 정렬·페이지 파라미터가 달라도 같은 문서이기 때문이다.
        // 쿼리가 콘텐츠를 결정하는 페이지는 컨트롤러가 canonical 을 직접 넣는다.
        seo.setCanonical(absolutize(seo.getCanonical(), uri));
        seo.setImage(absolutize(seo.getImage(), DEFAULT_IMAGE));

        if (!StringUtils.hasText(seo.getOgType())) {
            seo.setOgType("website");
        }
        if (!StringUtils.hasText(seo.getRobots())) {
            seo.setRobots(isNoindex(uri) ? "noindex, follow" : "index, follow");
        }

        modelAndView.addObject(MODEL_ATTRIBUTE, seo);
    }

    /** 값이 없으면 fallback 경로를, 상대경로면 baseUrl 을 앞에 붙여 절대 URL 로 만든다. */
    private String absolutize(String value, String fallbackPath) {
        String path = StringUtils.hasText(value) ? value : fallbackPath;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private boolean isNoindex(String uri) {
        for (String pattern : NOINDEX_PATTERNS) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 줄바꿈·연속 공백을 한 칸으로 줄이고 길이를 제한한다.
     * 테스트 소개글은 여러 줄로 작성되는데, 그대로 meta 속성에 넣으면 태그가 깨진다.
     */
    private String squash(String text, int maxLength) {
        String squashed = text.replaceAll("\\s+", " ").trim();
        if (squashed.length() <= maxLength) {
            return squashed;
        }
        return squashed.substring(0, maxLength - 1).trim() + "…";
    }
}
