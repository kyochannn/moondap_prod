package com.moondap.service;

import com.moondap.common.FileService;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.dto.MdTestResultDTO;
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
            dto.setQuestions(mdTestMapper.selectQuestions(id));
            dto.setResults(mdTestMapper.selectResults(id));
        }
        return dto;
    }

    @Transactional
    public void createTest(MdTestDTO dto, MultipartFile thumbnail, List<MultipartFile> resultFiles, String createdBy) throws Exception {
        // 테스트 키 자동 생성
        String datePrefix = "T-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        Integer maxSeq = mdTestMapper.selectMaxSequenceByDatePrefix(datePrefix);
        int nextSeq = (maxSeq == null) ? 1 : maxSeq + 1;
        String testKey = datePrefix + "-" + nextSeq;
        dto.setTestKey(testKey);

        if (mdTestMapper.countByTestKey(dto.getTestKey()) > 0) {
            throw new IllegalArgumentException("이미 사용 중인 테스트 키입니다: " + dto.getTestKey());
        }

        // 대표 썸네일 업로드
        String savedThumbnail = fileService.upload(thumbnail);
        dto.setThumbnailImage(savedThumbnail);
        dto.setCreatedBy(createdBy);

        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            dto.setStatus("draft");
        }

        // 1. 테스트 본체 저장
        mdTestMapper.insertTest(dto);

        // 2. 질문들 저장
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            int order = 1;
            for (MdTestQuestionDTO q : dto.getQuestions()) {
                if (q.getQuestionText() == null || q.getQuestionText().isBlank()) continue;
                q.setTestId(dto.getId());
                q.setQuestionOrder(order++);

                // Null 체크 및 기본값 설정
                if (q.getReverse() == null) q.setReverse(false);
                if (q.getActive() == null) q.setActive(true);

                mdTestMapper.insertQuestion(q);
            }
        }

        // 3. 결과들 저장 (다중 이미지 포함)
        if (dto.getResults() != null && !dto.getResults().isEmpty()) {
            for (int i = 0; i < dto.getResults().size(); i++) {
                MdTestResultDTO r = dto.getResults().get(i);
                if (r.getResultTitle() == null || r.getResultTitle().isBlank()) continue;

                // 해당 순서의 파일이 전달되었다면 업로드 수행
                if (resultFiles != null && i < resultFiles.size()) {
                    MultipartFile file = resultFiles.get(i);
                    if (file != null && !file.isEmpty()) {
                        String savedFilename = fileService.upload(file);
                        r.setResultImage(savedFilename);
                    }
                }
                r.setTestId(dto.getId());
                mdTestMapper.insertResult(r);
            }
        }
    }

    @Transactional
    public void updateTest(MdTestDTO dto, MultipartFile thumbnail, List<MultipartFile> resultFiles) throws Exception {
        MdTestDTO existing = mdTestMapper.selectTest(dto.getId());
        if (existing == null) throw new IllegalArgumentException("존재하지 않는 테스트입니다.");

        // 썸네일 교체 로직
        if (thumbnail != null && !thumbnail.isEmpty()) {
            fileService.deleteFile(existing.getThumbnailImage());
            String savedThumbnail = fileService.upload(thumbnail);
            dto.setThumbnailImage(savedThumbnail);
        } else {
            dto.setThumbnailImage(existing.getThumbnailImage());
        }

        // 1. 본체 수정
        mdTestMapper.updateTest(dto);

        // 2. 질문 동기화
        mdTestMapper.deleteQuestionsByTestId(dto.getId());
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            int order = 1;
            for (MdTestQuestionDTO q : dto.getQuestions()) {
                if (q.getQuestionText() == null || q.getQuestionText().isBlank()) continue;
                q.setTestId(dto.getId());
                q.setQuestionOrder(order++);

                // Null 체크 및 기본값 설정
                if (q.getReverse() == null) q.setReverse(false);
                if (q.getActive() == null) q.setActive(true);

                mdTestMapper.insertQuestion(q);
            }
        }

        // 3. 결과 동기화 (기존 이미지 유지 혹은 교체)
        mdTestMapper.deleteResultsByTestId(dto.getId());
        if (dto.getResults() != null && !dto.getResults().isEmpty()) {
            int fileIdx = 0;
            for (MdTestResultDTO r : dto.getResults()) {
                if (r.getResultTitle() == null || r.getResultTitle().isBlank()) continue;

                // 새로운 이미지 업로드 여부 체크
                if (Boolean.TRUE.equals(r.getHasNewImage()) && resultFiles != null && fileIdx < resultFiles.size()) {
                    MultipartFile file = resultFiles.get(fileIdx++);
                    if (file != null && !file.isEmpty()) {
                        String savedFilename = fileService.upload(file);
                        r.setResultImage(savedFilename);
                    }
                }
                // (파일이 없다면 JSON에 담겨온 기존 파일명 유지됨)
                r.setTestId(dto.getId());
                mdTestMapper.insertResult(r);
            }
        }
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
