package com.moondap.common;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 금칙어 필터링 유틸리티.
 *
 * <p>이전 구현은 공백만 제거한 뒤 {@code contains} 로 비교해서, 조금만 변형해도 그대로
 * 통과했다. {@code 씨-발}, {@code 씨1발}, {@code 씨이이이발}, {@code ㅅㅂ}, {@code tlqkf}
 * 가 모두 걸리지 않았다. 광고가 붙은 페이지에 혐오·성적 표현이 노출되면 애드센스
 * 정책 위반이므로 우회 경로를 좁힌다.
 *
 * <p><b>단어를 두 갈래로 나눈 이유</b>
 *
 * <p>우회를 막으려면 공백·특수문자를 모두 지우고 비교해야 한다. 그런데 그렇게 하면
 * 어절 경계가 사라져서 {@code 쳐다보지}, {@code 과시 발표} 같은 정상 표현이 걸린다.
 * 그래서 단어를 성격에 따라 나눠 다르게 다룬다.
 *
 * <ul>
 *   <li>{@link #STRONG_WORDS} — 다른 뜻으로 쓰일 일이 없는 말. 공백까지 지운 형태에서
 *       찾는다. 우회에 강하지만 오탐 위험을 감수한다.</li>
 *   <li>{@link #AMBIGUOUS_WORDS} — 정상적인 단어의 일부가 될 수 있는 말
 *       ({@code 보지} ⊂ {@code 쳐다보지}). 공백을 남긴 형태에서 어절 단위로 보고,
 *       뒤에 조사만 붙은 경우에만 금칙어로 판정한다.</li>
 * </ul>
 *
 * <p>완전한 차단은 불가능하다. 이 필터는 1차 방어일 뿐이고, 통과한 것은 사용자 신고로
 * 잡는다({@link com.moondap.service.MdReportService}).
 */
public class ProfanityUtil {

    // ── 정규화 패턴 ────────────────────────────────────────────

    /**
     * 남길 문자: 한글 음절·자모·영문·숫자.
     *
     * <p>조합용 자모(U+1100–U+11FF)를 반드시 포함해야 한다. NFKC 는 호환 자모
     * {@code ㅅ}(U+3145)을 조합용 자모 {@code ᄉ}(U+1109)로 바꾸는데, 이 범위를 빼면
     * 자모 축약형이 통째로 지워져 {@code ㅅㅂ} 가 검출되지 않는다.
     */
    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile("[^가-힣ㄱ-ㅎㅏ-ㅣ\\u1100-\\u11FFa-z0-9]");

    /** 위와 같지만 공백은 어절 경계로 남긴다. */
    private static final Pattern NON_ALPHANUMERIC_KEEP_SPACE =
            Pattern.compile("[^가-힣ㄱ-ㅎㅏ-ㅣ\\u1100-\\u11FFa-z0-9 ]");

    private static final Pattern DIGITS = Pattern.compile("[0-9]");

    /** 같은 문자가 3회 이상 이어지면 1회로 줄인다. */
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{2,}");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // ── 한글 음절 분해 상수 ────────────────────────────────────

    private static final char HANGUL_BASE = 0xAC00;
    private static final char HANGUL_LAST = 0xD7A3;
    private static final int JUNG_COUNT = 21;
    private static final int JONG_COUNT = 28;
    /** 초성 'ㅇ'(음가 없음)의 인덱스. 모음 늘임 판정에 쓴다. */
    private static final int CHO_IEUNG = 11;

    // ── 단어 목록 ──────────────────────────────────────────────

    /**
     * 뒤에 붙어도 같은 뜻인 조사.
     *
     * <p>{@code 보지를} 은 금칙어지만 {@code 보지만}("보지만 ~하다")·{@code 보지마} 는
     * 동사다. 그래서 {@code 도}·{@code 만} 처럼 어미와 겹치는 것은 넣지 않는다.
     */
    private static final List<String> JOSA = Arrays.asList(
        "", "은", "는", "이", "가", "을", "를", "의", "에", "와", "과", "랑"
    );

    /**
     * 다른 뜻으로 쓰일 일이 거의 없는 말. 공백·특수문자를 모두 지운 형태에서 찾는다.
     *
     * <p>자모 축약형과 두벌식 영타 우회형을 함께 등록한다.
     */
    private static final List<String> STRONG_WORDS = Arrays.asList(
        // ── 욕설 ─────────────────────────────────────────────
        "씨발", "시벌", "씨벌", "시팔", "씨팔", "싀발", "슈발", "쉬발", "씌발",
        "ㅅㅂ", "ㅆㅂ", "tlqkf",
        "병신", "븅신", "빙신", "ㅂㅅ", "ㅄ", "qudtls",
        "개새끼", "개색끼", "개세끼", "쉐끼", "쌔끼", "ㅆㄲ",
        "존나", "존내", "졸라", "ㅈㄴ", "whssk",
        "지랄", "지럴", "ㅈㄹ", "wlfkf",
        "등신", "머저리", "멍청이", "미친놈", "미친년", "미친새끼",
        "호로", "쌍놈", "상놈", "놈팽이", "찐따", "빠가",
        "입닥쳐", "뒈져", "뒤져라", "좆", "좃", "개소리",

        // ── 성적 표현 ────────────────────────────────────────
        "섹스", "쎅스", "정액", "콘돔", "야동", "야설", "자위행위",
        "딸딸이", "빠구리", "성괴", "창녀", "창놈", "매춘", "포르노",

        // ── 혐오 표현 ────────────────────────────────────────
        "일베", "메갈", "워마드", "김치녀", "김치남", "한남충", "한녀충",
        "남페미", "여페미", "틀딱", "급식충", "맘충",
        "짱깨", "쪽발이", "깜둥이"
    );

    /**
     * 정상적인 단어의 일부가 될 수 있는 말. 어절 단위로 보고 조사까지만 허용한다.
     *
     * <p>예: {@code 보지} 는 {@code 쳐다보지}·{@code 보지 마세요} 의 일부이기도 하다.
     */
    private static final List<String> AMBIGUOUS_WORDS = Arrays.asList(
        "시발", "새끼", "보지", "자지", "걸레", "꺼져", "닥쳐", "호구",
        "바보", "미친", "한남", "엿먹어", "떡치"
    );

    /**
     * 비교 전에 목록도 같은 정규화를 거친다.
     *
     * <p>이걸 빠뜨리면 입력은 NFKC 로 바뀌었는데 목록은 원본 그대로라 영원히 어긋난다.
     * 실제로 {@code ㅅㅂ} 가 검출되지 않던 원인이 이것이었다.
     */
    private static final List<String> STRONG_NORMALIZED =
            STRONG_WORDS.stream().map(w -> squash(w, NON_ALPHANUMERIC)).toList();

    private static final List<String> AMBIGUOUS_NORMALIZED =
            AMBIGUOUS_WORDS.stream().map(w -> squash(w, NON_ALPHANUMERIC)).toList();

    private ProfanityUtil() {
    }

    /**
     * 입력된 텍스트에 금칙어가 포함되어 있는지 확인한다.
     *
     * @param text 검사할 텍스트
     * @return 금칙어 포함 여부 (true: 포함, false: 미포함)
     */
    public static boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return containsStrongWord(text) || containsAmbiguousWord(text);
    }

    /** 공백까지 지운 형태에서 찾는다. 우회에 강한 대신 어절 경계를 보지 않는다. */
    private static boolean containsStrongWord(String text) {
        String squashed = squash(text, NON_ALPHANUMERIC);
        if (squashed.isEmpty()) {
            return false;
        }
        for (String word : STRONG_NORMALIZED) {
            if (!word.isEmpty() && squashed.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 어절이 금칙어로 시작하고 나머지가 조사뿐일 때만 걸러낸다.
     *
     * <p>{@code 보지를} 은 걸리고 {@code 쳐다보지}(어절 시작이 아님)와
     * {@code 보지마}(남는 부분이 조사가 아님)는 통과한다.
     */
    private static boolean containsAmbiguousWord(String text) {
        String spaced = squash(text, NON_ALPHANUMERIC_KEEP_SPACE);
        for (String token : WHITESPACE.split(spaced)) {
            if (token.isEmpty()) {
                continue;
            }
            for (String word : AMBIGUOUS_NORMALIZED) {
                if (!word.isEmpty() && token.startsWith(word)
                        && JOSA.contains(token.substring(word.length()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 비교용 정규화. 우회 수단을 걷어내 금칙어와 같은 지면에 올린다.
     *
     * <ol>
     *   <li>NFKC — 전각·호환 문자를 표준형으로</li>
     *   <li>소문자화</li>
     *   <li>{@code keep} 이 허용하지 않는 문자 제거 (특수문자·이모지·제로폭 문자)</li>
     *   <li>숫자 제거 — {@code 씨1발} 같은 삽입형 우회</li>
     *   <li>모음 늘임 축약 — {@code 씨이이이발} → {@code 씨발}</li>
     *   <li>같은 문자 3회 이상 반복을 1회로 축약</li>
     * </ol>
     */
    private static String squash(String text, Pattern keep) {
        String result = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase();
        result = keep.matcher(result).replaceAll("");
        result = DIGITS.matcher(result).replaceAll("");
        result = collapseVowelStretch(result);
        result = REPEATED.matcher(result).replaceAll("$1");
        return result.trim();
    }

    /**
     * 모음을 늘여 쓴 우회를 되돌린다.
     *
     * <p>{@code 씨이이이발}, {@code 조오오온나} 처럼 앞 글자의 모음을 'ㅇ+모음' 음절로
     * 늘여 쓰는 방식은 한글에서 가장 흔한 우회다. 단순 반복 축약으로는 잡히지 않는다
     * ({@code 씨이이이발} → {@code 씨이발}). 음절을 분해해 직접 걷어낸다.
     *
     * <ul>
     *   <li>앞 글자와 중성이 같고 받침 없는 'ㅇ' 음절 → 버린다 ({@code 씨}+{@code 이})</li>
     *   <li>받침만 있는 경우 → 앞 글자에 받침을 합친다 ({@code 조}+{@code 온} → {@code 존})</li>
     * </ul>
     */
    private static String collapseVowelStretch(String text) {
        StringBuilder out = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (isSyllable(current) && out.length() > 0 && isSyllable(out.charAt(out.length() - 1))) {
                char previous = out.charAt(out.length() - 1);
                int prevCode = previous - HANGUL_BASE;
                int prevJung = (prevCode / JONG_COUNT) % JUNG_COUNT;
                int prevJong = prevCode % JONG_COUNT;

                int code = current - HANGUL_BASE;
                int cho = code / (JONG_COUNT * JUNG_COUNT);
                int jung = (code / JONG_COUNT) % JUNG_COUNT;
                int jong = code % JONG_COUNT;

                if (cho == CHO_IEUNG && jung == prevJung) {
                    if (jong == 0) {
                        continue;                       // 순수한 늘임. 버린다.
                    }
                    if (prevJong == 0) {
                        // 받침이 뒤 음절로 밀려난 형태. 앞 글자에 되돌린다.
                        out.setCharAt(out.length() - 1, (char) (previous + jong));
                        continue;
                    }
                }
            }

            out.append(current);
        }

        return out.toString();
    }

    private static boolean isSyllable(char c) {
        return c >= HANGUL_BASE && c <= HANGUL_LAST;
    }
}
