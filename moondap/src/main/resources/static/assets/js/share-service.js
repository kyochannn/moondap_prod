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

    // 공유 집계는 이 길목 한 곳에서만 한다. 공유 버튼은 소개·결과·밸런스게임에 흩어져
    // 있지만 실제 동작은 전부 여기를 지나므로, 화면이 늘어나도 추적이 따라온다.
    // mdWithRef 로 붙인 ?ref=kakao 가 없으면 "몇 번 공유됐나"는 알아도 "그래서 몇 명이
    // 들어왔나"를 알 수 없다 — 바이럴 계수의 분모가 사라진다.
    const trackedUrl = typeof mdWithRef === 'function' ? mdWithRef(shareUrl, 'kakao') : shareUrl;
    if (typeof mdTrackShare === 'function') {
        mdTrackShare('kakao', { content_title: title });
    }

    Kakao.Share.sendDefault({
        objectType: 'feed',
        content: {
            title: title,
            description: description,
            imageUrl: imageUrl,
            link: {
                mobileWebUrl: trackedUrl,
                webUrl: trackedUrl,
            },
        },
        buttons: [
            {
                title: buttonTitle,
                link: {
                    mobileWebUrl: trackedUrl,
                    webUrl: trackedUrl,
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
    const rawUrl = url || window.location.href;

    // 카카오 공유와 같은 이유로 채널을 붙인다. 복사된 주소가 그대로 퍼지므로
    // 여기에 ref 가 없으면 '링크 복사'를 통한 유입은 direct 로 뭉뚱그려진다.
    const targetUrl = typeof mdWithRef === 'function' ? mdWithRef(rawUrl, 'link') : rawUrl;
    if (typeof mdTrackShare === 'function') {
        mdTrackShare('link');
    }
    
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
