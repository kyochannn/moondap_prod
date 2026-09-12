/**
 * MoonDap 분석 모달 핸들러 (AnalysisHandler)
 * - 대기 카운트다운 및 결과 잠금 해제 제어
 * - 콘텐츠 클릭 시 즉시 잠금 해제 기능
 * - 서버 검증 후 결과 페이지 이동
 */
const AnalysisHandler = {
    timer: null,
    countdown: 7,
    // 이 카운트다운 동안 실제로 계산되는 것은 없다. 결과는 아래 폼을 제출한 뒤
    // 서버가 만든다. 예전에는 "알고리즘 가동 중", "결과지 생성 중" 처럼 하지 않는 일을
    // 한다고 적어 두었는데, 매번 정확히 같은 시간에 끝나므로 사용자는 금세 알아챈다.
    // 그때 깎이는 신뢰는 광고가 아니라 테스트 결과 쪽이라 사실대로 적는다.
    messages: {
        waiting: '잠시 후 결과가 열립니다',
        done: '이제 결과를 확인할 수 있어요',
    },

    init: function(config) {
        const {
            formId = 'submitForm',
            countdown = 7,
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
            
            if (currentCountdown > 0) {
                statusText.innerText = this.messages.waiting;
                submitBtn.innerText = `결과 확인하기 (${currentCountdown}초)`;
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
            
            fetch(verifyUrl, { method: 'POST', headers: window.mdCsrf.headers() })
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
        btn.innerHTML = '<i class="bi bi-check-circle-fill me-2"></i>결과 확인하기';
        btn.classList.add("unlocked");
        status.innerText = this.messages.done;
        // text-info 는 부트스트랩 시안이라 브랜드 색과 어긋났다.
        status.classList.add("analysis-status-done");
    }
};
