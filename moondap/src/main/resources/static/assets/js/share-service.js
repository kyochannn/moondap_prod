/**
 * MoonDap Common Share Service
 * 카카오톡 공유 및 링크 복사 등 공유 관련 통합 로직
 */

const KAKAO_JS_KEY = 'b1e87a2f9dbdbece155c0cdb52a64d52';

/**
 * 카카오 SDK 초기화 확인
 */
function initKakao() {
    if (window.Kakao && !Kakao.isInitialized()) {
        Kakao.init(KAKAO_JS_KEY);
    }
}

/**
 * 카카오톡 피드 공유 공통 함수
 * @param {Object} data - 공유할 데이터 객체
 * @param {string} data.title - 제목
 * @param {string} data.description - 설명
 * @param {string} data.imageUrl - 썸네일 이미지 URL
 * @param {string} data.shareUrl - 이동할 페이지 URL
 * @param {string} [data.buttonTitle] - 버튼 문구 (기본값: '확인하러 가기')
 */
function shareKakaoCommon(data) {
    if (!window.Kakao) {
        console.error('Kakao SDK가 로드되지 않았습니다.');
        return;
    }

    initKakao();

    const { title, description, imageUrl, shareUrl, buttonTitle = '확인하러 가기' } = data;

    Kakao.Share.sendDefault({
        objectType: 'feed',
        content: {
            title: title,
            description: description,
            imageUrl: imageUrl,
            link: {
                mobileWebUrl: shareUrl,
                webUrl: shareUrl,
            },
        },
        buttons: [
            {
                title: buttonTitle,
                link: {
                    mobileWebUrl: shareUrl,
                    webUrl: shareUrl,
                },
            },
        ],
    });
}

/**
 * 링크 복사 공통 함수
 * @param {string} url - 복사할 URL (미지정 시 현재 페이지)
 * @param {Function} callback - 복사 성공 후 실행할 콜백
 */
function copyLinkCommon(url, callback) {
    const targetUrl = url || window.location.href;
    
    if (typeof copyToClipboard === 'function') {
        copyToClipboard(targetUrl, callback);
    } else {
        // Fallback
        const textArea = document.createElement("textarea");
        textArea.value = targetUrl;
        document.body.appendChild(textArea);
        textArea.select();
        try {
            document.execCommand('copy');
            if (callback) callback();
        } catch (err) {
            console.error('링크 복사 실패:', err);
        }
        document.body.removeChild(textArea);
    }
}
