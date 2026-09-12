package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.moondap.config.CacheConfig;
import com.moondap.dto.MdContentItemDTO;
import com.moondap.dto.MdTestCategoryDTO;
import com.moondap.mapper.MdTestCategoryMapper;
import com.moondap.mapper.MdTestMapper;

/**
 * 캐시 배선 검증.
 *
 * 단위 테스트로는 @Cacheable 프록시가 걸리는지 알 수 없어 실제 컨텍스트를 띄운다.
 * 캐시가 안 걸리면 성능만 나빠지고 끝이지만, 무효화가 안 걸리면 새로 등록한
 * 콘텐츠가 최대 5분간 목록에 안 보인다. 후자가 더 위험해서 함께 고정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContentCacheTest {

    @MockitoBean
    private MdTestMapper mdTestMapper;

    @MockitoBean
    private MdTestCategoryMapper mdTestCategoryMapper;

    @Autowired
    private MdTestUserService mdTestUserService;

    @Autowired
    private MdTestCategoryService mdTestCategoryService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames()
                .forEach(name -> cacheManager.getCache(name).clear());

        when(mdTestMapper.selectAllContentList(anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new MdContentItemDTO()));
        when(mdTestCategoryMapper.selectActiveCategories())
                .thenReturn(List.of(new MdTestCategoryDTO()));
    }

    // ── 캐시 적중 ──────────────────────────────────────────────

    @Test
    @DisplayName("같은 조건의 콘텐츠 목록 조회는 한 번만 DB 를 친다")
    void contentListIsCached() {
        mdTestUserService.getAllContentList("all", "popular", "all", 0, 6);
        mdTestUserService.getAllContentList("all", "popular", "all", 0, 6);
        mdTestUserService.getAllContentList("all", "popular", "all", 0, 6);

        verify(mdTestMapper, times(1)).selectAllContentList("all", "popular", "all", 0, 6);
    }

    @Test
    @DisplayName("조건이 다르면 별개로 캐시된다")
    void differentArgumentsAreCachedSeparately() {
        // 메인 페이지는 정렬 조건만 다른 조회를 3번 한다. 하나로 뭉뚱그려지면 안 된다.
        mdTestUserService.getAllContentList("all", "popular", "NORMAL", 0, 6);
        mdTestUserService.getAllContentList("all", "popular", "BALANCE", 0, 6);
        mdTestUserService.getAllContentList("all", "latest", "NORMAL", 0, 6);

        verify(mdTestMapper, times(1)).selectAllContentList("all", "popular", "NORMAL", 0, 6);
        verify(mdTestMapper, times(1)).selectAllContentList("all", "popular", "BALANCE", 0, 6);
        verify(mdTestMapper, times(1)).selectAllContentList("all", "latest", "NORMAL", 0, 6);
    }

    @Test
    @DisplayName("활성 카테고리 목록도 캐시된다")
    void activeCategoriesAreCached() {
        mdTestCategoryService.getActiveCategories();
        mdTestCategoryService.getActiveCategories();

        verify(mdTestCategoryMapper, times(1)).selectActiveCategories();
    }

    // ── 캐시 무효화 ────────────────────────────────────────────

    @Test
    @DisplayName("카테고리를 수정하면 카테고리 캐시가 비워진다")
    void updatingCategoryEvictsCategoryCache() {
        mdTestCategoryService.getActiveCategories();

        mdTestCategoryService.updateCategory(new MdTestCategoryDTO());

        mdTestCategoryService.getActiveCategories();
        verify(mdTestCategoryMapper, times(2)).selectActiveCategories();
    }

    @Test
    @DisplayName("카테고리를 수정하면 콘텐츠 목록 캐시도 함께 비워진다")
    void updatingCategoryEvictsContentList() {
        // 목록에 카테고리 표시 이름이 들어가므로 같이 비워야 한다.
        mdTestUserService.getAllContentList("all", "popular", "all", 0, 6);

        mdTestCategoryService.updateCategory(new MdTestCategoryDTO());

        mdTestUserService.getAllContentList("all", "popular", "all", 0, 6);
        verify(mdTestMapper, times(2)).selectAllContentList("all", "popular", "all", 0, 6);
    }

    @Test
    @DisplayName("카테고리를 삭제해도 캐시가 비워진다")
    void deletingCategoryEvictsCaches() {
        mdTestCategoryService.getActiveCategories();

        mdTestCategoryService.deleteCategory(1L);

        mdTestCategoryService.getActiveCategories();
        verify(mdTestCategoryMapper, times(2)).selectActiveCategories();
    }

    // ── 캐시 구성 ──────────────────────────────────────────────

    @Test
    @DisplayName("설정한 캐시가 모두 등록되어 있다")
    void allCachesAreRegistered() {
        assertThat(cacheManager.getCache(CacheConfig.CONTENT_LIST)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.ACTIVE_CATEGORIES)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.PARTICIPANT_COUNT)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.EGEN_STATS)).isNotNull();
    }

    @Test
    @DisplayName("관리 화면용 전체 카테고리 조회는 캐시하지 않는다")
    void allCategoriesIsNotCached() {
        when(mdTestCategoryMapper.selectAllCategories()).thenReturn(List.of(new MdTestCategoryDTO()));

        mdTestCategoryService.getAllCategories();
        mdTestCategoryService.getAllCategories();

        // 관리자가 수정 직후 목록을 봐야 하므로 캐시하면 안 된다.
        verify(mdTestCategoryMapper, times(2)).selectAllCategories();
    }
}
