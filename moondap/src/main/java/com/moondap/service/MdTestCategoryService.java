package com.moondap.service;

import com.moondap.dto.MdTestCategoryDTO;
import com.moondap.mapper.MdTestCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MdTestCategoryService {

    private final MdTestCategoryMapper categoryMapper;

    public List<MdTestCategoryDTO> getActiveCategories() {
        return categoryMapper.selectActiveCategories();
    }

    public List<MdTestCategoryDTO> getAllCategories() {
        return categoryMapper.selectAllCategories();
    }

    public void addCategory(MdTestCategoryDTO dto) {
        categoryMapper.insertCategory(dto);
    }

    public void updateCategory(MdTestCategoryDTO dto) {
        categoryMapper.updateCategory(dto);
    }

    public void deleteCategory(Long id) {
        categoryMapper.deleteCategory(id);
    }
}
