package com.moondap.service;

import com.moondap.common.FileService;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.mapper.MdTestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MdTestAdminService {

    private final MdTestMapper mdTestMapper;
    private final FileService fileService;

    // ─── 테스트 CRUD ───────────────────────────────────────────

    public List<MdTestDTO> getTestList() {
        return mdTestMapper.selectTestList();
    }

    public MdTestDTO getTest(Long id) {
        MdTestDTO dto = mdTestMapper.selectTest(id);
        if (dto != null) {
            // dto.setQuestions(mdTestMapper.selectQuestions(id));
        }
        return dto;
    }

    @Transactional
    public void createTest(MdTestDTO dto, MultipartFile thumbnail, String createdBy) throws Exception {
        // 테스트 키 자동 생성: T-yyMMdd-seq
        String datePrefix = "T-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        Integer maxSeq = mdTestMapper.selectMaxSequenceByDatePrefix(datePrefix);
        int nextSeq = (maxSeq == null) ? 1 : maxSeq + 1;
        String testKey = datePrefix + "-" + nextSeq;
        
        dto.setTestKey(testKey);

        if (mdTestMapper.countByTestKey(dto.getTestKey()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 테스트 키입니다: " + dto.getTestKey());
        }
        
        String savedFilename = fileService.upload(thumbnail);
        dto.setThumbnailImage(savedFilename);
        dto.setCreatedBy(createdBy);
        
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            dto.setStatus("draft");
        }
        
        // 1. 테스트 본체 저장
        mdTestMapper.insertTest(dto);
    }

    @Transactional
    public void updateTest(MdTestDTO dto, MultipartFile thumbnail) throws Exception {
        MdTestDTO existing = mdTestMapper.selectTest(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("존재하지 않는 테스트입니다.");
        }

        if (thumbnail != null && !thumbnail.isEmpty()) {
            fileService.deleteFile(existing.getThumbnailImage());
            String savedFilename = fileService.upload(thumbnail);
            dto.setThumbnailImage(savedFilename);
        } else {
            dto.setThumbnailImage(existing.getThumbnailImage());
        }

        // 1. 테스트 본체 수정
        mdTestMapper.updateTest(dto);
    }

    public void deleteTest(Long id) {
        MdTestDTO existing = mdTestMapper.selectTest(id);
        if (existing != null) {
            fileService.deleteFile(existing.getThumbnailImage());
        }
        mdTestMapper.deleteTest(id);
    }

    // ─── 질문 CRUD ───────────────────────────────────────────

    public List<MdTestQuestionDTO> getQuestions(Long testId) {
        return mdTestMapper.selectQuestions(testId);
    }

    public MdTestQuestionDTO getQuestion(Long id) {
        return mdTestMapper.selectQuestion(id);
    }

    public void createQuestion(MdTestQuestionDTO dto) {
        int nextOrder = mdTestMapper.countQuestionsByTestId(dto.getTestId()) + 1;
        dto.setQuestionOrder(nextOrder);
        
        // Null 체크: 체크박스 선택 안 할 경우 대응
        if (dto.getReverse() == null) dto.setReverse(false);
        if (dto.getActive() == null) dto.setActive(true); 
        
        mdTestMapper.insertQuestion(dto);
    }

    public void updateQuestion(MdTestQuestionDTO dto) {
        MdTestQuestionDTO existing = mdTestMapper.selectQuestion(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("존재하지 않는 질문입니다.");
        }
        
        // Null 체크: 체크박스 선택 안 할 경우 대응
        if (dto.getReverse() == null) dto.setReverse(false);
        if (dto.getActive() == null) dto.setActive(existing.getActive());
        
        // 순서는 수정 불가 — 기존 값 유지
        dto.setQuestionOrder(existing.getQuestionOrder());
        mdTestMapper.updateQuestion(dto);
    }

    public void deleteQuestion(Long id) {
        mdTestMapper.softDeleteQuestion(id);
    }
}
