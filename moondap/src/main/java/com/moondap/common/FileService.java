package com.moondap.common;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    // 파일을 저장할 실제 경로 (OS에 맞게 설정, 예: C:/uploads/ 또는 /home/ubuntu/uploads/)
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.profile-dir}")
    private String profileDir;

    public String upload(MultipartFile file) throws IOException {
        return uploadToDir(file, uploadDir, "default-content-img.png");
    }

    public String uploadProfile(MultipartFile file) throws IOException {
        return uploadToDir(file, profileDir, "default-profile-img.svg");
    }

    private String uploadToDir(MultipartFile file, String directory, String defaultName) throws IOException {
        if (file == null || file.isEmpty()) {
            return defaultName; 
        }

        File dir = new File(directory);
        if (!dir.exists()) dir.mkdirs();

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String savedFilename = UUID.randomUUID().toString() + extension;

        File targetFile = new File(directory + File.separator + savedFilename);
        file.transferTo(targetFile);

        return savedFilename;
    }
    
    public void deleteFile(String filename) {
        deleteFromDir(filename, uploadDir, "default-content-img.png");
    }

    public void deleteProfile(String filename) {
        deleteFromDir(filename, profileDir, "default-profile-img.svg");
    }

    private void deleteFromDir(String filename, String directory, String defaultName) {
        if (filename == null || filename.isEmpty() || defaultName.equals(filename)) {
            return;
        }

        try {
            File file = new File(directory + File.separator + filename);
            if (file.exists()) {
                if (file.delete()) {
                    System.out.println("파일 삭제 성공 (" + directory + "): " + filename);
                }
            }
        } catch (Exception e) {
            System.err.println("파일 삭제 중 오류 발생: " + e.getMessage());
        }
    }
}
