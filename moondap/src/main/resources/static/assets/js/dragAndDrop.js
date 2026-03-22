// 해당 js에는 이미지 유효성 검사, 파일 압축, 이미지명 uuid 변경, drag and drop 기능이 포함되어 있다.


// [0] 압축 cdn 필요 - script.html에 삽입해 둠.
// [1] 유효성 검사 함수
function validateImage(file) {
    const maxSize = 5 * 1024 * 1024; // 압축 전 체크는 여유 있게 5MB로 설정
    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
    if (!file) return false;
    if (file.size > maxSize) {
        alert("파일 크기가 너무 큽니다! (최대 5MB)");
        return false;
    }
    if (!allowedTypes.includes(file.type)) {
        alert("지원하지 않는 파일 형식입니다. (JPG, PNG, WEBP, GIF 가능)");
        return false;
    }
    return true;
}

// [2] 이미지 압축 함수
async function compressImage(file) {
    const options = {
        maxSizeMB: 0.3,             // 최종 용량을 0.3MB로 압축
        maxWidthOrHeight: 800,  	// 가로세로 최대 800px
        useWebWorker: true,
        alwaysKeepResolution: false
    };
    try {
        return await imageCompression(file, options);
    } catch (error) {
        console.error("압축 에러:", error);
       	alert("사진 압축을 실패하였습니다.");
        return file; // 에러 시 원본 반환
    }
}

function generateUUID() {
    return ([1e7]+-1e3+-4e3+-8e3+-1e11).replace(/[018]/g, c =>
        (c ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> c / 4).toString(16)
    );
}

// [3] 미리보기 함수 (콘솔 확인 로직 추가)
async function previewImage(inputOrFile, targetId) {
    const preview = document.getElementById(targetId);
    const dropZone = preview.parentElement;
    
    let file = (inputOrFile instanceof File) ? inputOrFile : inputOrFile.files[0];
    if (!file) return null;

    // [콘솔] 처리 시작 알림
    console.group(`📷 이미지 처리 시작: ${file.name}`);
    console.log(`- 원본 파일명: ${file.name}`);
    console.log(`- 원본 용량: ${(file.size / 1024 / 1024).toFixed(2)} MB`);

    // 유효성 체크
    if (!validateImage(file)) {
        console.warn("- [실패] 유효성 검사 통과 못함");
        if (!(inputOrFile instanceof File)) inputOrFile.value = ""; 
        console.groupEnd();
        return null;
    }

    // 압축 진행
    dropZone.style.opacity = "0.6"; 
    console.log("- 압축 프로세스 진행 중...");
    let compressedFile = await compressImage(file);
    dropZone.style.opacity = "1";
    
    // 파일 이름 UUID로 변경
    const extension = file.name.split('.').pop();
    const newFileName = `${generateUUID()}.${extension}`;
    
    // 새로운 File 객체 생성
    const finalFile = new File([compressedFile], newFileName, { type: compressedFile.type });

    // [콘솔] 압축 결과 출력
    const ratio = ((1 - (finalFile.size / file.size)) * 100).toFixed(1);
    console.log(`- 새 파일명: ${finalFile.name}`);
    console.log(`- 압축 후 용량: ${(finalFile.size / 1024 / 1024).toFixed(2)} MB`);
    console.log(`- 압축률: ${ratio}% 감소`);

    // 미리보기 및 input 동기화
    const reader = new FileReader();
    reader.onload = function(e) {
        preview.src = e.target.result;
        preview.style.display = 'block';
        dropZone.classList.add('has-image');
        console.log("- 미리보기 이미지 로드 완료");
    };
    reader.readAsDataURL(finalFile);

    // input 요소에 압축된 파일 담기
    if (!(inputOrFile instanceof File)) {
        const dataTransfer = new DataTransfer();
        dataTransfer.items.add(finalFile);
        inputOrFile.files = dataTransfer.files;
        console.log("- Input 요소 데이터 동기화 완료");
    }

    console.groupEnd(); // 콘솔 그룹 종료
    return finalFile; 
}

// [4] 이벤트 리스너
// [4] 이벤트 리스너 (수정됨)
// [4] 이벤트 리스너
document.addEventListener('DOMContentLoaded', function() {
    // 모든 .drop-zone 요소를 찾아서 각각 이벤트를 붙임
    document.querySelectorAll('.drop-zone').forEach(zone => {
        
        // 1. 드래그 중 효과
        zone.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.stopPropagation();
            zone.classList.add('drag-over');
        });

        // 2. 드래그 나갈 때 효과
        ['dragleave', 'dragend'].forEach(type => {
            zone.addEventListener(type, (e) => {
                e.preventDefault();
                e.stopPropagation();
                zone.classList.remove('drag-over');
            });
        });

        // 3. 파일 드롭 시 (핵심 수정 부분)
        zone.addEventListener('drop', async (e) => {
            e.preventDefault();
            e.stopPropagation();
            zone.classList.remove('drag-over');

            const files = e.dataTransfer.files;
            if (files && files.length > 0) {
                // zone 변수는 이 블록 안에서만 유효합니다.
                const previewImg = zone.querySelector('img');
                const fileInput = zone.querySelector('input[type="file"]');
                
                // [수정 포인트] previewImage를 호출하고 압축된 파일을 리턴받음
                const compressedFile = await previewImage(files[0], previewImg.id);
                
                // [수정 포인트] 리턴받은 압축 파일을 input에 강제로 삽입
                if (compressedFile && fileInput) {
                    const dataTransfer = new DataTransfer();
                    dataTransfer.items.add(compressedFile);
                    fileInput.files = dataTransfer.files;
                    
                    // 값이 변경되었음을 알리는 이벤트 발생 (필요 시)
                    fileInput.dispatchEvent(new Event('change', { bubbles: true }));
                }
            }
        });
    });

    // 브라우저 기본 드롭 방지 (파일이 브라우저에서 바로 열리는 현상 막기)
    window.addEventListener("dragover", e => e.preventDefault());
    window.addEventListener("drop", e => e.preventDefault());
});