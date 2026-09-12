package com.moondap.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileService {

    /**
     * 허용 이미지 형식.
     *
     * <p><b>SVG 는 의도적으로 제외한다.</b> SVG 는 내부에 &lt;script&gt; 를 담을 수 있고,
     * 업로드 파일은 /uploads/**, /profile/** 로 서비스와 같은 오리진에서 서빙되므로
     * 저장형 XSS 경로가 된다. 기본 프로필 이미지(default-profile-img.svg)는
     * 업로드물이 아니라 /assets/img/default-img/ 아래 정적 리소스이므로 영향이 없다.
     */
    private enum ImageType {
        JPEG(".jpg"),
        PNG(".png"),
        GIF(".gif"),
        WEBP(".webp");

        private final String extension;

        ImageType(String extension) {
            this.extension = extension;
        }
    }

    /** 시그니처 판별에 필요한 최소 바이트 수 (WEBP 가 12바이트로 가장 길다) */
    private static final int SIGNATURE_LENGTH = 12;

    private static final String DEFAULT_CONTENT_IMAGE = "default-content-img.png";
    private static final String DEFAULT_PROFILE_IMAGE = "default-profile-img.svg";

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.profile-dir}")
    private String profileDir;

    public String upload(MultipartFile file) throws IOException {
        return uploadToDir(file, uploadDir, DEFAULT_CONTENT_IMAGE);
    }

    public String uploadProfile(MultipartFile file) throws IOException {
        return uploadToDir(file, profileDir, DEFAULT_PROFILE_IMAGE);
    }

    private String uploadToDir(MultipartFile file, String directory, String defaultName) throws IOException {
        if (file == null || file.isEmpty()) {
            return defaultName;
        }

        // 확장자가 아니라 파일 내용으로 형식을 판별한다.
        // 확장자만 검사하면 evil.svg 를 evil.png 로 바꿔 올리는 것을 막지 못한다.
        ImageType type = detectImageType(file);
        if (type == null) {
            log.warn("허용되지 않는 업로드 차단: originalFilename={}, size={}",
                    file.getOriginalFilename(), file.getSize());
            throw new IOException("이미지 파일만 업로드할 수 있습니다. (jpg, png, gif, webp)");
        }

        File dir = new File(directory);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("업로드 디렉터리를 생성할 수 없습니다: " + directory);
        }

        // 저장 파일명은 UUID + '실제 내용에 맞는' 확장자로 고정한다.
        // 사용자가 보낸 파일명은 어떤 형태로도 경로에 반영하지 않는다(경로 조작 차단).
        String savedFilename = UUID.randomUUID() + type.extension;

        File targetFile = new File(dir, savedFilename);
        file.transferTo(targetFile);

        // 트랜잭션 안에서 업로드된 경우, 롤백되면 이 파일을 지운다.
        registerRollbackCleanup(directory, savedFilename);

        return savedFilename;
    }

    /**
     * 현재 트랜잭션이 롤백되면 방금 저장한 파일을 삭제하도록 등록한다.
     *
     * <p>업로드는 파일시스템 작업이라 DB 트랜잭션과 함께 되돌아가지 않는다.
     * 예를 들어 밸런스 게임 등록 중 INSERT 가 실패하면 트랜잭션은 롤백되지만
     * 디스크에는 아무도 참조하지 않는 이미지가 남는다. 이런 파일이 쌓이면
     * 어느 것이 살아 있는 파일인지 구분할 방법이 없어진다.
     *
     * <p>트랜잭션 밖에서 호출된 경우에는 아무것도 하지 않는다.
     */
    private void registerRollbackCleanup(String directory, String filename) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                File orphan = new File(directory, filename);
                if (orphan.exists() && orphan.delete()) {
                    log.info("트랜잭션 롤백으로 업로드 파일 정리: {}", filename);
                } else if (orphan.exists()) {
                    log.warn("롤백 후 업로드 파일 삭제 실패: {}", orphan.getAbsolutePath());
                }
            }
        });
    }

    /**
     * 파일 선두의 매직 바이트로 이미지 형식을 판별한다.
     * 판별 불가면 null 을 반환한다.
     *
     * ImageIO 를 쓰지 않는 이유: Java 17 기본 배포판은 WEBP 디코더가 없어
     * 정상적인 webp 업로드가 거부된다(실제로 운영 중 webp 파일이 존재한다).
     */
    private ImageType detectImageType(MultipartFile file) throws IOException {
        byte[] header = new byte[SIGNATURE_LENGTH];

        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, SIGNATURE_LENGTH);
            if (read < SIGNATURE_LENGTH) {
                return null;
            }
        }

        // JPEG : FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return ImageType.JPEG;
        }
        // PNG : 89 50 4E 47 0D 0A 1A 0A
        if (header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return ImageType.PNG;
        }
        // GIF : "GIF87a" 또는 "GIF89a"
        if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return ImageType.GIF;
        }
        // WEBP : "RIFF" ....(파일 크기 4바이트).... "WEBP"
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ImageType.WEBP;
        }

        return null;
    }

    public void deleteFile(String filename) {
        deleteFromDir(filename, uploadDir, DEFAULT_CONTENT_IMAGE);
    }

    public void deleteProfile(String filename) {
        deleteFromDir(filename, profileDir, DEFAULT_PROFILE_IMAGE);
    }

    private void deleteFromDir(String filename, String directory, String defaultName) {
        if (filename == null || filename.isEmpty() || defaultName.equals(filename)) {
            return;
        }

        // DB 에 저장된 값이라도 경로 구분자가 섞여 있으면 디렉터리 밖 파일을 지울 수 있다.
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            log.warn("비정상적인 파일명 삭제 요청 차단: {}", filename);
            return;
        }

        try {
            File file = new File(directory, filename);
            if (file.exists() && file.delete()) {
                log.info("파일 삭제 성공 ({}): {}", directory, filename);
            }
        } catch (Exception e) {
            log.error("파일 삭제 중 오류 발생: {}", e.getMessage());
        }
    }
}
