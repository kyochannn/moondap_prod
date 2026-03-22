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

    public String upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return "default-img.png"; // 파일이 없으면 기본 이미지 반환
        }

        // 1. 디렉토리가 없으면 생성
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        // 2. 파일명 중복 방지를 위한 UUID 생성
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String savedFilename = UUID.randomUUID().toString() + extension;

        // 3. 파일 저장
        File targetFile = new File(uploadDir + File.separator + savedFilename);
        file.transferTo(targetFile);

        return savedFilename; // DB에는 저장된 파일명만 기록
    }
    
    public void deleteFile(String filename) {
        // 1. 파일명이 없거나 기본 이미지인 경우 삭제하지 않음
        if (filename == null || filename.isEmpty() || "default-img.png".equals(filename)) {
            return;
        }

        try {
            File file = new File(uploadDir + File.separator + filename);
            
            // 2. 파일이 실제로 존재하면 삭제
            if (file.exists()) {
                if (file.delete()) {
                    System.out.println("파일 삭제 성공: " + filename);
                } else {
                    System.err.println("파일 삭제 실패 (권한 문제 등): " + filename);
                }
            }
        } catch (Exception e) {
            System.err.println("파일 삭제 중 오류 발생: " + e.getMessage());
        }
    }
}
