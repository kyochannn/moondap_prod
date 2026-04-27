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

// [3] 미리보기 함수 (크롭 로직 통합)
async function previewImage(inputOrFile, targetId) {
    const preview = document.getElementById(targetId);
    const dropZone = preview.parentElement;
    
    let file = (inputOrFile instanceof File) ? inputOrFile : inputOrFile.files[0];
    if (!file) return null;

    console.group(`📷 이미지 처리 시작: ${file.name}`);

    // 유효성 체크
    if (!validateImage(file)) {
        if (!(inputOrFile instanceof File)) inputOrFile.value = ""; 
        console.groupEnd();
        return null;
    }

    // [추가] 크롭 프로세스 시작
    try {
        const croppedBlob = await startCropper(file);
        if (!croppedBlob) {
            console.log("- 크롭 취소됨");
            console.groupEnd();
            return null;
        }
        file = croppedBlob; // 크롭된 데이터로 파일 교체
    } catch (e) {
        console.error("- 크롭 에러:", e);
        console.groupEnd();
        return null;
    }

    // [로딩 시작]
    showLoading(0, '이미지를 최적화하고 있습니다', '품질을 유지하며 압축 중입니다...');

    try {
        // 압축 진행
        dropZone.style.opacity = "0.6"; 
        let compressedFile = await compressImage(file);
        dropZone.style.opacity = "1";
        
        // 파일 이름 UUID로 변경 (크롭 결과물은 blob이므로 원본 확장자 유지 시도)
        const extension = (inputOrFile instanceof File) ? 'png' : inputOrFile.files[0].name.split('.').pop();
        const newFileName = `${generateUUID()}.${extension}`;
        
        // 새로운 File 객체 생성
        const finalFile = new File([compressedFile], newFileName, { type: compressedFile.type });

        // 미리보기 및 input 동기화
        const reader = new FileReader();
        reader.onload = function(e) {
            preview.src = e.target.result;
            preview.style.display = 'block';
            dropZone.classList.add('has-image');
            hideLoading();
        };
        reader.readAsDataURL(finalFile);

        // input 요소에 압축된 파일 담기
        if (!(inputOrFile instanceof File)) {
            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(finalFile);
            inputOrFile.files = dataTransfer.files;
            console.log("- Input 요소 데이터 동기화 완료");
        }

        console.groupEnd();
        return finalFile; 
        
    } catch (error) {
        console.error("- [실패] 이미지 처리 중 에러 발생:", error);
        hideLoading();
        console.groupEnd();
        return null;
    }
}

// [3-1] Cropper.js 실행 함수
let cropperInstance = null;
function startCropper(file) {
    return new Promise((resolve, reject) => {
        const modalEl = document.getElementById('cropperModal');
        if (!modalEl) {
            console.warn("크롭 모달이 없습니다. 크롭 과정을 건너뜁니다.");
            return resolve(file);
        }

        const modal = new bootstrap.Modal(modalEl);
        const image = document.getElementById('cropperImage');
        const cropButton = document.getElementById('cropButton');
        const reader = new FileReader();

        reader.onload = function(e) {
            image.src = e.target.result;
            modal.show();
        };
        reader.readAsDataURL(file);

        modalEl.addEventListener('shown.bs.modal', function () {
            if (cropperInstance) cropperInstance.destroy();
            cropperInstance = new Cropper(image, {
                aspectRatio: 1, // 정사각형 고정
                viewMode: 1,
                autoCropArea: 1,
                responsive: true
            });
        }, { once: true });

        // 적용 버튼 클릭
        const handleCrop = () => {
            const canvas = cropperInstance.getCroppedCanvas({
                width: 800,
                height: 800
            });
            canvas.toBlob((blob) => {
                cropButton.blur(); // 포커스 해제로 aria-hidden 경고 방지
                modal.hide();
                resolve(blob);
            }, 'image/jpeg', 0.9);
            cropButton.removeEventListener('click', handleCrop);
        };

        cropButton.addEventListener('click', handleCrop);

        // 모달 닫힐 때 처리
        modalEl.addEventListener('hidden.bs.modal', function () {
            if (cropperInstance) {
                cropperInstance.destroy();
                cropperInstance = null;
            }
            resolve(null); // 아무것도 반환하지 않고 종료
        }, { once: true });
    });
}

// [4] 개별 요소 드래그 앤 드롭 초기화 함수
function initDragAndDrop(zone) {
    if (!zone) return;
    
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

    // 3. 파일 드롭 시
    zone.addEventListener('drop', async (e) => {
        e.preventDefault();
        e.stopPropagation();
        zone.classList.remove('drag-over');

        const files = e.dataTransfer.files;
        if (files && files.length > 0) {
            const previewImg = zone.querySelector('img');
            const fileInput = zone.querySelector('input[type="file"]');
            
            // previewImage를 호출하고 압축된 파일을 리턴받음
            const compressedFile = await previewImage(files[0], previewImg.id);
            
            if (compressedFile && fileInput) {
                const dataTransfer = new DataTransfer();
                dataTransfer.items.add(compressedFile);
                fileInput.files = dataTransfer.files;
                
                // 값이 변경되었음을 알리는 이벤트 발생
                fileInput.dispatchEvent(new Event('change', { bubbles: true }));
            }
        }
    });
}

// [5] 이벤트 리스너
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.drop-zone').forEach(zone => {
        initDragAndDrop(zone);
    });

    // 브라우저 기본 드롭 방지 (파일이 브라우저에서 바로 열리는 현상 막기)
    window.addEventListener("dragover", e => e.preventDefault());
    window.addEventListener("drop", e => e.preventDefault());
});