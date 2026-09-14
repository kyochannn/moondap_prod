package com.moondap.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 금칙어 필터.
 *
 * <p>댓글과 사용자 제작 콘텐츠에 걸리는 1차 방어선이다. 여기를 통과한 표현은 광고가
 * 붙은 페이지에 그대로 노출되므로, 흔한 우회 수단을 회귀 테스트로 고정한다.
 *
 * <p>오탐 케이스를 함께 고정한 이유는, 필터를 강화할 때마다 정상 문장이 조용히
 * 막히는 일이 반복되기 때문이다.
 */
class ProfanityUtilTest {

    @Nested
    @DisplayName("기본 동작")
    class Basics {

        @Test
        @DisplayName("빈 값은 통과시킨다")
        void allowsEmpty() {
            assertThat(ProfanityUtil.containsProfanity(null)).isFalse();
            assertThat(ProfanityUtil.containsProfanity("")).isFalse();
            assertThat(ProfanityUtil.containsProfanity("   ")).isFalse();
        }

        @Test
        @DisplayName("평범한 문장은 통과시킨다")
        void allowsNormalText() {
            assertThat(ProfanityUtil.containsProfanity("이 테스트 재밌네요")).isFalse();
            assertThat(ProfanityUtil.containsProfanity("둘 다 좋은데 고민되네")).isFalse();
            assertThat(ProfanityUtil.containsProfanity("결과가 생각보다 정확해요!")).isFalse();
        }

        @Test
        @DisplayName("있는 그대로의 욕설을 잡는다")
        void catchesPlainProfanity() {
            assertThat(ProfanityUtil.containsProfanity("씨발")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("이거 병신같네")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("존나 어렵다")).isTrue();
        }
    }

    @Nested
    @DisplayName("[회귀] 우회 수단")
    class Evasion {

        @Test
        @DisplayName("특수문자를 끼워 넣어도 잡는다")
        void catchesSpecialCharInsertion() {
            // 이전 구현은 공백만 지워서 이 형태가 전부 통과했다.
            assertThat(ProfanityUtil.containsProfanity("씨-발")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("씨.발")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("씨*발")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("병@신")).isTrue();
        }

        @Test
        @DisplayName("숫자를 끼워 넣어도 잡는다")
        void catchesDigitInsertion() {
            assertThat(ProfanityUtil.containsProfanity("씨1발")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("병123신")).isTrue();
        }

        @Test
        @DisplayName("글자를 늘려 써도 잡는다")
        void catchesRepeatedCharacters() {
            assertThat(ProfanityUtil.containsProfanity("씨이이이발")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("씨이발")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("조오오온나")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("조온나")).isTrue();
        }

        @Test
        @DisplayName("자모 축약형을 잡는다")
        void catchesJamoAbbreviation() {
            assertThat(ProfanityUtil.containsProfanity("ㅅㅂ 뭐야")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("ㅄ아")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("ㅈㄹ하네")).isTrue();
        }

        @Test
        @DisplayName("두벌식 영타 우회를 잡는다")
        void catchesQwertyTransliteration() {
            // 한글 자판을 영문으로 그대로 친 형태
            assertThat(ProfanityUtil.containsProfanity("tlqkf")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("qudtls")).isTrue();
        }

        @Test
        @DisplayName("전각 문자와 대소문자를 정규화한다")
        void normalizesWidthAndCase() {
            assertThat(ProfanityUtil.containsProfanity("TLQKF")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("ｔｌｑｋｆ")).isTrue();
        }
    }

    @Nested
    @DisplayName("[회귀] 오탐 방지")
    class FalsePositives {

        @Test
        @DisplayName("금칙어를 품은 정상 단어는 통과시킨다")
        void allowsWordsContainingProfanity() {
            // '보지' 는 금칙어지만 아래는 모두 정상적인 동사다.
            assertThat(ProfanityUtil.containsProfanity("쳐다보지 못했어요")).isFalse();
            assertThat(ProfanityUtil.containsProfanity("아직 안 보지만 궁금해요")).isFalse();
            assertThat(ProfanityUtil.containsProfanity("돌아보지 않기로 했다")).isFalse();
            assertThat(ProfanityUtil.containsProfanity("보지마세요")).isFalse();
        }

        @Test
        @DisplayName("어절 경계를 넘는 우연한 조합은 통과시킨다")
        void allowsAccidentalCombinations() {
            // 공백을 지우면 '과시발표' 안에 '시발' 이 생긴다.
            // 애매한 단어는 어절 단위로 보기 때문에 걸리지 않는다.
            assertThat(ProfanityUtil.containsProfanity("과시 발표를 했다")).isFalse();
            assertThat(ProfanityUtil.containsProfanity("전시 발표회")).isFalse();
        }

        @Test
        @DisplayName("'새끼' 가 들어간 정상 표현은 통과시킨다")
        void allowsAnimalOffspring() {
            assertThat(ProfanityUtil.containsProfanity("강아지새끼 귀엽다")).isFalse();
            assertThat(ProfanityUtil.containsProfanity("고양이 새끼를 봤어요")).isTrue(); // 어절 시작 + 조사
        }
    }

    @Nested
    @DisplayName("애매한 단어의 어절 판정")
    class AmbiguousWordBoundary {

        @Test
        @DisplayName("어절이 금칙어이거나 조사만 붙으면 잡는다")
        void catchesStandaloneAndWithJosa() {
            assertThat(ProfanityUtil.containsProfanity("보지")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("보지를 보여줘")).isTrue();
            assertThat(ProfanityUtil.containsProfanity("너 바보야")).isFalse();  // '야' 는 조사 목록에 없다
            assertThat(ProfanityUtil.containsProfanity("바보")).isTrue();
        }
    }
}
