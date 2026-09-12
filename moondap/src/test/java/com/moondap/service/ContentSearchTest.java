package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색어의 LIKE 와일드카드 무력화.
 *
 * <p>이 처리가 없으면 "%" 한 글자만 넣어도 조건이 사실상 사라져 전체 목록이 끌려오고,
 * "_" 는 아무 글자나 매칭한다. 사용자가 친 글자는 글자 그대로 찾아야 한다.
 */
class ContentSearchTest {

    @Test
    @DisplayName("LIKE 와일드카드는 일반 문자로 취급된다")
    void escapesWildcards() {
        assertThat(MdTestUserService.escapeLike("100%")).isEqualTo("100!%");
        assertThat(MdTestUserService.escapeLike("a_b")).isEqualTo("a!_b");
    }

    @Test
    @DisplayName("이스케이프 문자 자체도 escape 된다")
    void escapesTheEscapeCharacter() {
        // 먼저 ! 를 처리하지 않으면 "!%" 입력이 와일드카드 이스케이프로 해석된다.
        assertThat(MdTestUserService.escapeLike("!")).isEqualTo("!!");
        assertThat(MdTestUserService.escapeLike("!%")).isEqualTo("!!!%");
    }

    @Test
    @DisplayName("평범한 검색어는 그대로 둔다")
    void leavesPlainWordsAlone() {
        assertThat(MdTestUserService.escapeLike("연애 성향")).isEqualTo("연애 성향");
        assertThat(MdTestUserService.escapeLike(null)).isNull();
    }
}
