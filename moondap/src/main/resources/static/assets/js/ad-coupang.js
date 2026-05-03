/**
 * MoonDap 광고 모달 핸들러 (AdHandler)
 * - 15초 카운트다운 및 분석 애니메이션 제어
 * - 광고 클릭 시 즉시 잠금 해제 기능
 * - 서버 광고 시청 검증 후 결과 페이지 이동
 */
const AdHandler = {
    timer: null,
    countdown: 15,
    messages: [
        '<i class="bi bi-search me-2"></i>답변 데이터를 분석 중...',
        '<i class="bi bi-cpu me-2"></i>알고리즘 가동 중...',
        '<i class="bi bi-magic me-2"></i>결과지 생성 중...',
        "모든 분석이 완료되었습니다! 결과를 확인하세요."
    ],

    init: function(config) {
        const {
            formId = 'submitForm',
            countdown = 15,
            verifyUrl = '/test/verify-ad'
        } = config;

        this.countdown = countdown;
        const adModal = document.getElementById("adModal");
        const submitBtn = document.getElementById("finalSubmitBtn");
        const adContainer = document.querySelector(".ad-container");
        const statusText = document.getElementById("adStatusText");

        if (!adModal || !submitBtn || !adContainer || !statusText) return;

        // 모달 표시
        adModal.classList.add("active");
        
        let currentCountdown = this.countdown;
        let progress = 0;

        // 1. 타이머 가동
        this.timer = setInterval(() => {
            currentCountdown--;
            progress += (100 / this.countdown);
            submitBtn.style.setProperty('--progress', `${progress}%`);
            
            let currentMsg = "";
            if (currentCountdown > 10) currentMsg = this.messages[0];
            else if (currentCountdown > 5) currentMsg = this.messages[1];
            else if (currentCountdown > 0) currentMsg = this.messages[2];

            if (currentCountdown > 0) {
                const tempDiv = document.createElement("div");
                tempDiv.innerHTML = currentMsg;
                statusText.innerText = tempDiv.innerText;
                submitBtn.innerHTML = `${currentMsg} (${currentCountdown}초)`;
            } else {
                this.unlockResult(submitBtn, statusText);
            }
        }, 1000);

        // 2. 광고 영역 클릭 시 즉시 잠금 해제
        adContainer.onclick = () => {
            if (submitBtn.disabled) {
                this.unlockResult(submitBtn, statusText);
            }
        };

        // 3. 최종 제출 버튼 클릭 이벤트
        submitBtn.onclick = () => {
            submitBtn.innerHTML = '<div class="spinner-border spinner-border-sm me-2"></div>이동 중...';
            
            fetch(verifyUrl, { method: 'POST' })
                .then(() => {
                    document.getElementById(formId).submit();
                })
                .catch(() => {
                    // 오류 발생 시에도 사용자 경험을 위해 결과 페이지로 이동 시도
                    document.getElementById(formId).submit();
                });
        };
    },

    unlockResult: function(btn, status) {
        if (this.timer) clearInterval(this.timer);
        btn.style.setProperty('--progress', '100%');
        btn.disabled = false;
        btn.innerHTML = '<i class="bi bi-check-circle-fill me-2"></i>모든 분석 완료! 결과 확인하기';
        btn.classList.add("unlocked");
        status.innerText = this.messages[3];
        status.classList.add("text-info");
    }
};
