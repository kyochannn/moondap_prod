package com.moondap.mapper;

import com.moondap.dto.MdTestCategoryDTO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface MdTestCategoryMapper {
    List<MdTestCategoryDTO> selectActiveCategories();
    List<MdTestCategoryDTO> selectAllCategories();
    void insertCategory(MdTestCategoryDTO dto);
    void updateCategory(MdTestCategoryDTO dto);
    void deleteCategory(Long id);
}
