package com.moondap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.moondap.common.FileService;
import com.moondap.dto.MdTestResultDTO;
import com.moondap.mapper.MdTestMapper;

/**
 * 테스트 결과 이미지 매칭 테스트.
 *
 * <p>화면은 새 이미지를 고른 결과에 대해서만 resultFiles 에 파일을 추가한다.
 * 즉 이 리스트는 결과 인덱스가 아니라 "새 이미지가 있는 결과들"의 순서를 따른다.
 *
 * <p>등록 경로는 이 규약을 어기고 {@code resultFiles.get(결과인덱스)} 로 접근해서,
 * 일부 결과에만 이미지를 넣으면 <b>엉뚱한 결과에 이미지가 붙었다.</b>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultImageMappingTest {

    @Mock
    private MdTestMapper mdTestMapper;

    @Mock
    private FileService fileService;

    @InjectMocks
    private MdTestAdminService service;

    private MdTestResultDTO result(String title, boolean hasNewImage) {
        MdTestResultDTO r = new MdTestResultDTO();
        r.setResultTitle(title);
        r.setHasNewImage(hasNewImage);
        return r;
    }

    private MultipartFile file(String name) {
        return new MockMultipartFile("resultFiles", name, null, new byte[] { 1 });
    }

    /** 업로드하면 원본 파일명을 그대로 저장 파일명으로 돌려주도록 흉내 낸다 */
    private void stubUpload() throws Exception {
        when(fileService.upload(any(MultipartFile.class)))
                .thenAnswer(inv -> ((MultipartFile) inv.getArgument(0)).getOriginalFilename());
    }

    private void apply(List<MdTestResultDTO> results, List<MultipartFile> files) {
        ReflectionTestUtils.invokeMethod(service, "applyResultImages", results, files);
    }

    @Test
    @DisplayName("[회귀] 세 번째 결과에만 이미지를 넣으면 그 결과에 붙는다")
    void assignsImageToCorrectResult() throws Exception {
        stubUpload();

        List<MdTestResultDTO> results = List.of(
                result("결과A", false),
                result("결과B", false),
                result("결과C", true));

        // 화면은 이미지가 있는 결과에 대해서만 append 하므로 리스트 크기는 1이다.
        apply(results, List.of(file("c.png")));

        // 이전 구현은 resultFiles.get(0) 을 결과A 에 붙이고 결과C 는 비워 뒀다.
        assertThat(results.get(0).getResultImage()).isNull();
        assertThat(results.get(1).getResultImage()).isNull();
        assertThat(results.get(2).getResultImage()).isEqualTo("c.png");
    }

    @Test
    @DisplayName("여러 결과에 이미지를 넣으면 순서대로 짝지어진다")
    void assignsMultipleImagesInOrder() throws Exception {
        stubUpload();

        List<MdTestResultDTO> results = List.of(
                result("결과A", true),
                result("결과B", false),
                result("결과C", true));

        apply(results, List.of(file("a.png"), file("c.png")));

        assertThat(results.get(0).getResultImage()).isEqualTo("a.png");
        assertThat(results.get(1).getResultImage()).isNull();
        assertThat(results.get(2).getResultImage()).isEqualTo("c.png");
    }

    @Test
    @DisplayName("이미지가 하나도 없으면 아무것도 붙지 않는다")
    void assignsNothingWhenNoFiles() {
        List<MdTestResultDTO> results = List.of(
                result("결과A", false),
                result("결과B", false));

        apply(results, null);

        assertThat(results).allSatisfy(r -> assertThat(r.getResultImage()).isNull());
    }

    @Test
    @DisplayName("제목이 빈 행을 건너뛰어도 이후 결과의 짝이 어긋나지 않는다")
    void keepsAlignmentWhenBlankTitleSkipped() throws Exception {
        stubUpload();

        List<MdTestResultDTO> results = new ArrayList<>(List.of(
                result("  ", true),      // 저장 대상 아님. 하지만 화면은 파일을 보냈다.
                result("결과B", true)));

        apply(results, List.of(file("skipped.png"), file("b.png")));

        assertThat(results.get(0).getResultImage()).isNull();
        assertThat(results.get(1).getResultImage())
                .as("건너뛴 행의 파일을 소비하지 않으면 결과B 가 skipped.png 를 받는다")
                .isEqualTo("b.png");
    }

    @Test
    @DisplayName("hasNewImage 가 false 면 기존 이미지를 유지한다")
    void keepsExistingImageWhenNoNewUpload() throws Exception {
        stubUpload();

        MdTestResultDTO existing = result("결과A", false);
        existing.setResultImage("old.png"); // 수정 화면이 JSON 으로 보내온 기존 파일명

        apply(List.of(existing), List.of(file("unused.png")));

        assertThat(existing.getResultImage()).isEqualTo("old.png");
    }

    @Test
    @DisplayName("파일 개수가 모자라도 예외 없이 넘어간다")
    void toleratesFewerFilesThanFlags() throws Exception {
        stubUpload();

        List<MdTestResultDTO> results = List.of(
                result("결과A", true),
                result("결과B", true));

        apply(results, List.of(file("a.png")));

        assertThat(results.get(0).getResultImage()).isEqualTo("a.png");
        assertThat(results.get(1).getResultImage()).isNull();
    }
}
