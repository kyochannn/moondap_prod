package com.moondap.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 업로드 검증 테스트.
 *
 * 핵심 계약은 "확장자가 아니라 내용으로 판별한다" 이다.
 * 업로드 파일은 서비스와 같은 오리진(/uploads/**, /profile/**)에서 서빙되므로
 * SVG 나 HTML 이 통과하면 곧바로 저장형 XSS 가 된다.
 */
class FileServiceTest {

    @TempDir
    Path tempDir;

    private FileService fileService;

    /** 각 형식의 최소 유효 시그니처 (판별에 필요한 12바이트를 채운다) */
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };
    private static final byte[] JPEG_HEADER = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1 };
    private static final byte[] GIF_HEADER = {
            'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, (byte) 0x80, 0 };
    private static final byte[] WEBP_HEADER = {
            'R', 'I', 'F', 'F', 0x1A, 0, 0, 0, 'W', 'E', 'B', 'P' };

    @BeforeEach
    void setUp() {
        fileService = new FileService();
        ReflectionTestUtils.setField(fileService, "uploadDir", tempDir.resolve("contents").toString());
        ReflectionTestUtils.setField(fileService, "profileDir", tempDir.resolve("profiles").toString());
    }

    private MockMultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, null, content);
    }

    private MockMultipartFile file(String filename, String content) {
        return file(filename, content.getBytes(StandardCharsets.UTF_8));
    }

    // ── 허용되는 형식 ──────────────────────────────────────────

    @Test
    @DisplayName("정상 이미지 4종은 업로드된다")
    void acceptsValidImages() throws IOException {
        assertThat(fileService.upload(file("a.png", PNG_HEADER))).endsWith(".png");
        assertThat(fileService.upload(file("b.jpg", JPEG_HEADER))).endsWith(".jpg");
        assertThat(fileService.upload(file("c.gif", GIF_HEADER))).endsWith(".gif");
        assertThat(fileService.upload(file("d.webp", WEBP_HEADER))).endsWith(".webp");
    }

    @Test
    @DisplayName("webp 은 ImageIO 로 못 읽으므로 시그니처 판별이 필요하다")
    void acceptsWebp() throws IOException {
        // Java 17 기본 배포판에는 WEBP 디코더가 없다. ImageIO.read() 로 검증했다면
        // 운영 중인 정상 webp 업로드가 전부 거부됐을 것이다.
        assertThat(fileService.upload(file("photo.webp", WEBP_HEADER))).endsWith(".webp");
    }

    // ── 차단되어야 하는 것 ─────────────────────────────────────

    @Test
    @DisplayName("SVG 는 확장자가 정직해도 거부한다")
    void rejectsSvg() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";

        assertThatThrownBy(() -> fileService.upload(file("evil.svg", svg)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("이미지 파일만");
    }

    @Test
    @DisplayName("SVG 내용을 .png 로 위장해도 거부한다")
    void rejectsSvgDisguisedAsPng() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";

        // 확장자만 검사하던 이전 구현은 이 파일을 그대로 통과시켰다.
        assertThatThrownBy(() -> fileService.upload(file("evil.png", svg)))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("HTML 을 .jpg 로 위장해도 거부한다")
    void rejectsHtmlDisguisedAsJpg() {
        assertThatThrownBy(() -> fileService.upload(file("x.jpg", "<html><script>alert(1)</script></html>")))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("시그니처 길이에 못 미치는 파일은 거부한다")
    void rejectsTooShortFile() {
        assertThatThrownBy(() -> fileService.upload(file("tiny.png", new byte[] { (byte) 0x89, 'P' })))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("PDF 는 거부한다")
    void rejectsPdf() {
        assertThatThrownBy(() -> fileService.upload(file("doc.pdf", "%PDF-1.4\n%âãÏÓ")))
                .isInstanceOf(IOException.class);
    }

    // ── 저장 파일명 규칙 ───────────────────────────────────────

    @Test
    @DisplayName("저장 확장자는 파일명이 아니라 실제 내용을 따른다")
    void extensionFollowsContent() throws IOException {
        // 내용은 PNG 인데 이름만 .jpg 로 올린 경우
        String saved = fileService.upload(file("mislabeled.jpg", PNG_HEADER));

        assertThat(saved).endsWith(".png");
    }

    @Test
    @DisplayName("원본 파일명은 저장 경로에 반영되지 않는다")
    void originalFilenameIsNotUsedInPath() throws IOException {
        String saved = fileService.upload(file("../../../etc/passwd.png", PNG_HEADER));

        assertThat(saved).doesNotContain("..").doesNotContain("/").doesNotContain("passwd");
        assertThat(tempDir.resolve("contents").resolve(saved)).exists();
    }

    // ── 빈 파일 처리 ───────────────────────────────────────────

    @Test
    @DisplayName("파일이 없으면 기본 이미지 이름을 돌려준다")
    void emptyFileFallsBackToDefault() throws IOException {
        assertThat(fileService.upload(file("none", new byte[0])))
                .isEqualTo("default-content-img.png");
        assertThat(fileService.uploadProfile(file("none", new byte[0])))
                .isEqualTo("default-profile-img.svg");
    }

    @Test
    @DisplayName("기본 프로필 SVG 는 업로드물이 아니므로 삭제 대상이 아니다")
    void defaultProfileIsNotDeleted() {
        // 정적 리소스(/assets/img/default-img/)를 가리키는 이름이라 파일 삭제를 시도하면 안 된다.
        fileService.deleteProfile("default-profile-img.svg");
        fileService.deleteFile("default-content-img.png");
    }

    @Test
    @DisplayName("경로 구분자가 섞인 파일명 삭제 요청은 무시한다")
    void rejectsTraversalOnDelete() {
        fileService.deleteFile("../../../etc/passwd");
        fileService.deleteProfile("..\\..\\windows\\system32");
    }
}
