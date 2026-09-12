package com.moondap.service;

import com.moondap.common.exception.UserMessageException;

import com.moondap.common.FileService;
import com.moondap.dto.MdTestDTO;
import com.moondap.dto.MdTestQuestionDTO;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.mapper.MdTestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.moondap.config.CacheConfig;
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
        return mdTestMapper.selectTestList(null);
    }

    public List<MdTestDTO> getTestListByUser(String username) {
        return mdTestMapper.selectTestList(username);
    }

    /**
     * 해당 테스트에 대한 권한이 있는지 확인 (작성자 또는 관리자)
     */
    public boolean checkOwnership(Long testId, String username, boolean isAdmin) {
        if (isAdmin) return true;
        MdTestDTO test = mdTestMapper.selectTest(testId);
        return test != null && username.equals(test.getCreatedBy());
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
    @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
    public void createTest(MdTestDTO dto, MultipartFile thumbnail, List<MultipartFile> resultFiles, String createdBy) throws Exception {
        // 테스트 키 자동 생성
        String datePrefix = "T-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        Integer maxSeq = mdTestMapper.selectMaxSequenceByDatePrefix(datePrefix);
        int nextSeq = (maxSeq == null) ? 1 : maxSeq + 1;
        String testKey = datePrefix + "-" + nextSeq;
        dto.setTestKey(testKey);

        if (mdTestMapper.countByTestKey(dto.getTestKey()) > 0) {
            throw new UserMessageException("이미 사용 중인 테스트 키입니다: " + dto.getTestKey());
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
            applyResultImages(dto.getResults(), resultFiles);

            for (MdTestResultDTO r : dto.getResults()) {
                if (r.getResultTitle() == null || r.getResultTitle().isBlank()) continue;
                r.setTestId(dto.getId());
                mdTestMapper.insertResult(r);
            }
        }
    }

    /**
     * 결과 목록에 업로드된 이미지를 매칭한다.
     *
     * <p><b>resultFiles 는 결과 인덱스와 1:1 이 아니다.</b> 화면은 새 이미지를 고른
     * 결과에 대해서만 {@code formData.append('resultFiles', ...)} 를 호출하므로,
     * 이 리스트는 "새 이미지가 있는 결과들"의 순서를 따르는 압축된 목록이다.
     *
     * <p>등록 경로는 이 규약을 어기고 {@code resultFiles.get(결과인덱스)} 로 접근했다.
     * 결과가 3개인데 세 번째에만 이미지를 넣으면 리스트 크기는 1이므로,
     * 그 이미지가 <b>첫 번째 결과에 붙고</b> 나머지는 이미지가 없는 상태로 저장됐다.
     *
     * <p>제목이 빈 행은 저장하지 않지만, 화면이 그 행에 대해서도 파일을 보냈다면
     * 커서는 함께 넘겨야 이후 결과들의 짝이 어긋나지 않는다.
     */
    private void applyResultImages(List<MdTestResultDTO> results, List<MultipartFile> resultFiles) throws Exception {
        int cursor = 0;

        for (MdTestResultDTO r : results) {
            MultipartFile file = null;
            if (Boolean.TRUE.equals(r.getHasNewImage())
                    && resultFiles != null && cursor < resultFiles.size()) {
                file = resultFiles.get(cursor++);
            }

            // 저장 대상이 아니어도 위에서 커서는 이미 넘겼다.
            if (r.getResultTitle() == null || r.getResultTitle().isBlank()) {
                continue;
            }

            if (file != null && !file.isEmpty()) {
                r.setResultImage(fileService.upload(file));
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
    public void updateTest(MdTestDTO dto, MultipartFile thumbnail, List<MultipartFile> resultFiles) throws Exception {
        MdTestDTO existing = mdTestMapper.selectTest(dto.getId());
        if (existing == null) throw new UserMessageException("존재하지 않는 테스트입니다.");

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
        // [수정] 기존 이미지 목록 백업하여 교체/삭제된 파일 정리를 위해 사용
        List<MdTestResultDTO> oldResults = mdTestMapper.selectResults(dto.getId());
        java.util.Set<String> oldImageFiles = new java.util.HashSet<>();
        if (oldResults != null) {
            for (MdTestResultDTO r : oldResults) {
                if (r.getResultImage() != null && !r.getResultImage().isBlank()) {
                    oldImageFiles.add(r.getResultImage());
                }
            }
        }

        mdTestMapper.deleteResultsByTestId(dto.getId());
        java.util.Set<String> newImageFiles = new java.util.HashSet<>();

        if (dto.getResults() != null && !dto.getResults().isEmpty()) {
            // 등록 경로와 동일한 규약으로 이미지를 매칭한다.
            applyResultImages(dto.getResults(), resultFiles);

            for (MdTestResultDTO r : dto.getResults()) {
                if (r.getResultTitle() == null || r.getResultTitle().isBlank()) continue;

                if (r.getResultImage() != null && !r.getResultImage().isBlank()) {
                    newImageFiles.add(r.getResultImage());
                }

                // (새 파일이 없다면 JSON에 담겨온 기존 파일명이 그대로 유지된다)
                r.setTestId(dto.getId());
                mdTestMapper.insertResult(r);
            }
        }

        // [수정] 더 이상 사용되지 않는 파일 삭제
        for (String oldImg : oldImageFiles) {
            if (!newImageFiles.contains(oldImg)) {
                fileService.deleteFile(oldImg);
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CONTENT_LIST, allEntries = true)
    public void deleteTest(Long id) {
        MdTestDTO existing = mdTestMapper.selectTest(id);
        if (existing != null) {
            // 1. 물리 파일 삭제 (썸네일)
            fileService.deleteFile(existing.getThumbnailImage());
            
            // [수정] 2. 물리 파일 삭제 (모든 결과 이미지)
            List<MdTestResultDTO> results = mdTestMapper.selectResults(id);
            if (results != null) {
                for (MdTestResultDTO r : results) {
                    if (r.getResultImage() != null && !r.getResultImage().isBlank()) {
                        fileService.deleteFile(r.getResultImage());
                    }
                }
            }
            
            // 3. 관련 데이터(결과, 질문) 우선 삭제 (FK 제약 조건 대응)
            mdTestMapper.deleteResultsByTestId(id);
            mdTestMapper.deleteQuestionsByTestId(id);
            
            // 4. 테스트 본체 삭제
            mdTestMapper.deleteTest(id);
        }
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
            throw new UserMessageException("존재하지 않는 질문입니다.");
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

    /** 누적 참여수. 큰 숫자를 보여주는 용도라 몇 분 지연은 무방하다. */
    @Cacheable(cacheNames = CacheConfig.PARTICIPANT_COUNT, key = "'normalTestPlayCount'")
    public long getTotalPlayCount() {
        return mdTestMapper.selectTotalPlayCount();
    }
}
