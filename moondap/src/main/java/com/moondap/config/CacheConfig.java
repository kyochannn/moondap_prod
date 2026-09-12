package com.moondap.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * 캐시 설정.
 *
 * <p>메인 페이지는 한 번 열릴 때 쿼리 9개를 날렸고, 그중 3개가 md_tests 와
 * balance_questions 를 통째로 UNION 한 뒤 정렬·LIMIT 하는 쿼리였다.
 * 이 형태는 인덱스를 타지 못해 데이터가 늘수록 선형으로 느려진다.
 *
 * <p>캐시마다 성격이 달라 만료 시간을 따로 준다. 스프링 기본
 * ConcurrentMapCacheManager 는 TTL 이 없어 값이 영원히 남으므로 Caffeine 을 쓴다.
 *
 * <p>목록 캐시는 콘텐츠 등록·수정·삭제 시 명시적으로 비운다(@CacheEvict).
 * 따라서 TTL 은 "투표수 변동에 따른 인기순 갱신 주기" 정도의 의미다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	/** 메인/목록의 통합 콘텐츠 조회 결과 */
	public static final String CONTENT_LIST = "contentList";

	/** 활성 카테고리 목록 */
	public static final String ACTIVE_CATEGORIES = "activeCategories";

	/** 콘텐츠별 누적 참여자 수 */
	public static final String PARTICIPANT_COUNT = "participantCount";

	/** 에겐테토 점수 통계 */
	public static final String EGEN_STATS = "egenStats";

	@Bean
	CacheManager cacheManager() {
		CaffeineCacheManager manager = new CaffeineCacheManager();

		// 콘텐츠 목록: 쓰기 시점에 명시적으로 비우므로 TTL 은 인기순 갱신 주기 역할만 한다.
		// 카테고리/정렬/타입/페이지 조합이 많아 항목 수를 넉넉히 잡았다.
		manager.registerCustomCache(CONTENT_LIST, Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(5))
				.maximumSize(500)
				.build());

		// 카테고리: 관리자가 바꿀 때만 변한다. 변경 시 비운다.
		manager.registerCustomCache(ACTIVE_CATEGORIES, Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(30))
				.maximumSize(10)
				.build());

		// 누적 참여자 수: 큰 숫자를 보여주는 용도라 몇 분 지연은 문제되지 않는다.
		manager.registerCustomCache(PARTICIPANT_COUNT, Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(5))
				.maximumSize(20)
				.build());

		// 에겐테토 통계: 전체 집계라 비싸고 거의 변하지 않는다.
		manager.registerCustomCache(EGEN_STATS, Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(30))
				.maximumSize(10)
				.build());

		return manager;
	}
}
