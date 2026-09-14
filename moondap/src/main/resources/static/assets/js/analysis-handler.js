/**
 * MoonDap 분석 모달 핸들러 (AnalysisHandler)
 * - 대기 카운트다운 및 결과 잠금 해제 제어
 * - 서버 검증 후 결과 페이지 이동
 *
 * 예전에는 광고 영역을 클릭하면 대기가 즉시 풀렸고, 광고가 차단된 경우에는
 * "클릭하면 대기 시간 없이 즉시" 라고 안내까지 했다. 이건 광고 클릭에 보상을
 * 주는 구조라 구글 게시자 정책의 클릭 유도(incentivized clicks) 위반이다.
 * 이 페이지에는 자동 광고(adsbygoogle)가 붙을 수 있어 애드센스 계정 자체가
 * 걸리는 문제였다. 잠금은 오직 카운트다운으로만 풀린다. 광고를 누르든 말든
 * 결과가 열리는 시점은 같아야 한다.
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

    /**
     * @param {object} config
     *   startUnlocked  카운트다운 없이 바로 열어 둘지 여부.
     *                  광고 대기 중 이탈했다 돌아온 복원 경로에서 쓴다. 그 사람은
     *                  이미 광고를 보고 7초를 기다린 뒤 떠난 것이라, 같은 대기를
     *                  한 번 더 요구하면 결과를 눈앞에 두고 다시 이탈한다.
     *                  광고 자체는 그대로 노출한다.
     */
    init: function(config) {
        const {
            formId = 'submitForm',
            countdown = 7,
            verifyUrl = '/test/verify-analysis',
            startUnlocked = false
        } = config;

        this.countdown = countdown;
        const analysisModal = document.getElementById("analysisModal");
        const submitBtn = document.getElementById("finalSubmitBtn");
        const analysisContainer = document.querySelector(".analysis-container");
        const statusText = document.getElementById("statusText");

        /*
         * 광고 영역(.analysis-container)은 필수가 아니다.
         *
         * 예전에는 이것까지 필수로 두고 하나라도 없으면 조용히 return 했는데,
         * 모달을 새로 디자인하면서 광고 영역이 빠지자 init 이 아무 일도 하지 않고
         * 끝나 버렸다. 오류도 안 나서, 마지막 문항에 답한 사람이 그대로 멈춘 채
         * 결과로 넘어가지 못했다(심리테스트·에겐테토 양쪽 모두).
         *
         * 결과를 여는 데 꼭 필요한 것은 모달·버튼·상태 문구 셋뿐이다.
         */
        if (!analysisModal || !submitBtn || !statusText) {
            // 조용히 멈추면 원인을 찾을 수 없다. 최소한 흔적은 남긴다.
            console.error('AnalysisHandler: 결과 모달 요소를 찾지 못해 즉시 제출합니다.', {
                analysisModal: !!analysisModal, submitBtn: !!submitBtn, statusText: !!statusText
            });
            const form = document.getElementById(formId);
            if (form) form.submit();
            return;
        }

        // 모달 표시
        analysisModal.classList.add("active");
        
        let currentCountdown = this.countdown;
        let progress = 0;

        // 1. 타이머 가동 (복원 경로는 기다릴 것이 없으므로 건너뛴다)
        if (!startUnlocked) this.timer = setInterval(() => {
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

        // 2. 최종 제출 버튼 클릭 이벤트
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

        // 3. 복원 경로는 이 시점에 바로 열어 준다. 클릭 핸들러가 붙은 뒤라야
        //    사용자가 즉시 눌러도 제출이 정상적으로 이어진다.
        if (startUnlocked) this.unlockResult(submitBtn, statusText);

        // 4. 광고 차단기(AdBlock) 감지 로직.
        //    광고가 가려지면 모달 가운데가 빈 칸으로 남아 화면이 고장 난 것처럼 보인다.
        //    자리를 메우기만 하고, 광고를 보라거나 누르라는 말은 하지 않는다.
        //    광고 영역이 아예 없는 화면도 있으므로 있을 때만 검사한다.
        if (analysisContainer) setTimeout(() => {
            const isBlocked = analysisContainer.offsetHeight < 10 || analysisContainer.innerHTML.trim() === "" || getComputedStyle(analysisContainer).display === "none";
            
            if (isBlocked) {
                analysisContainer.innerHTML = `
                    <div class="blocked-message p-3 text-center">
                        <i class="bi bi-hourglass-split text-warning mb-2" style="font-size: 2.5rem;"></i>
                        <h6 class="text-white fw-bold">광고를 불러오지 못했어요</h6>
                        <p class="small text-white-50 mb-0">
                            결과를 확인하는 데는 영향이 없습니다.
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
