/**
 * MoonDap 분석 모달 핸들러 (AnalysisHandler)
 * - 15초 카운트다운 및 분석 애니메이션 제어
 * - 콘텐츠 클릭 시 즉시 잠금 해제 기능
 * - 서버 검증 후 결과 페이지 이동
 */
const AnalysisHandler = {
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
            verifyUrl = '/test/verify-analysis'
        } = config;

        this.countdown = countdown;
        const analysisModal = document.getElementById("analysisModal");
        const submitBtn = document.getElementById("finalSubmitBtn");
        const analysisContainer = document.querySelector(".analysis-container");
        const statusText = document.getElementById("statusText");

        if (!analysisModal || !submitBtn || !analysisContainer || !statusText) return;

        // 모달 표시
        analysisModal.classList.add("active");
        
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

        // 2. 콘텐츠 영역 클릭 시 즉시 잠금 해제
        analysisContainer.onclick = () => {
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

        // 4. 광고 차단기(AdBlock) 감지 로직
        setTimeout(() => {
            const isBlocked = analysisContainer.offsetHeight < 10 || analysisContainer.innerHTML.trim() === "" || getComputedStyle(analysisContainer).display === "none";
            
            if (isBlocked) {
                analysisContainer.innerHTML = `
                    <div class="blocked-message p-3 text-center">
                        <i class="bi bi-shield-exclamation text-warning mb-2" style="font-size: 2.5rem;"></i>
                        <h6 class="text-white fw-bold">환경 설정 안내</h6>
                        <p class="small text-white-50 mb-0">
                            콘텐츠를 클릭하시면<br>
                            <span class="text-warning">대기 시간 없이 즉시</span> 결과를 확인할 수 있습니다.
                        </p>
                    </div>
                `;
            }
        }, 1200);
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
