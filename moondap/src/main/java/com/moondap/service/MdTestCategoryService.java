package com.moondap.service;

import com.moondap.config.CacheConfig;
import com.moondap.dto.MdTestCategoryDTO;
import com.moondap.mapper.MdTestCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MdTestCategoryService {

    private final MdTestCategoryMapper categoryMapper;

    /**
     * 활성 카테고리 목록.
     *
     * 거의 모든 화면이 호출하지만 관리자가 바꿀 때만 변한다.
     * 변경 시에는 아래 CUD 메서드가 캐시를 비운다.
     */
    @Cacheable(cacheNames = CacheConfig.ACTIVE_CATEGORIES)
    public List<MdTestCategoryDTO> getActiveCategories() {
        return categoryMapper.selectActiveCategories();
    }

    /** 관리 화면 전용. 호출 빈도가 낮아 캐시하지 않는다. */
    public List<MdTestCategoryDTO> getAllCategories() {
        return categoryMapper.selectAllCategories();
    }

    // 카테고리가 바뀌면 카테고리 목록뿐 아니라 콘텐츠 목록의 표시 이름도 달라지므로
    // 두 캐시를 함께 비운다.

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ACTIVE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
    })
    public void addCategory(MdTestCategoryDTO dto) {
        categoryMapper.insertCategory(dto);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ACTIVE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
    })
    public void updateCategory(MdTestCategoryDTO dto) {
        categoryMapper.updateCategory(dto);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ACTIVE_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
    })
    public void deleteCategory(Long id) {
        categoryMapper.deleteCategory(id);
    }
}
