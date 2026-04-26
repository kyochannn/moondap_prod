/**
 * Admin Table Sorter (Optimized)
 * 테이블 헤더 클릭 시 해당 열을 기준으로 데이터를 정렬합니다.
 * Array.sort()를 사용하여 성능을 최적화했습니다.
 */
function sortTable(n, tableSelector = ".table-dark-custom") {
    const table = document.querySelector(tableSelector);
    if (!table || !table.tBodies[0]) return;

    const tbody = table.tBodies[0];
    const rows = Array.from(tbody.rows);
    const headers = table.querySelectorAll("th");
    const currentHeader = headers[n];
    
    // 정렬 방향 결정
    let isAsc = currentHeader.classList.contains("sort-asc");
    let dir = isAsc ? "desc" : "asc";
    
    // 모든 헤더 아이콘 초기화
    headers.forEach(h => h.classList.remove("sort-asc", "sort-desc"));

    // 정렬 수행
    rows.sort((a, b) => {
        let x = a.getElementsByTagName("TD")[n].innerText.toLowerCase().trim();
        let y = b.getElementsByTagName("TD")[n].innerText.toLowerCase().trim();

        // 1. 날짜 정렬 처리 (yyyy-MM-dd)
        const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
        if (dateRegex.test(x) && dateRegex.test(y)) {
            const xDate = new Date(x).getTime();
            const yDate = new Date(y).getTime();
            return dir === "asc" ? xDate - yDate : yDate - xDate;
        }

        // 2. 숫자 정렬 처리
        const isNumeric = (str) => {
            if (typeof str !== 'string') return false;
            const cleaned = str.replace(/,/g, '');
            return !isNaN(cleaned) && !isNaN(parseFloat(cleaned));
        };
        
        if (isNumeric(x) && isNumeric(y)) {
            let xNum = parseFloat(x.replace(/[^0-9.-]/g, '')) || 0;
            let yNum = parseFloat(y.replace(/[^0-9.-]/g, '')) || 0;
            return dir === "asc" ? xNum - yNum : yNum - xNum;
        }

        // 3. 문자열 정렬 (한글 포함)
        return dir === "asc" ? x.localeCompare(y, 'ko') : y.localeCompare(x, 'ko');
    });

    // 정렬된 행으로 테이블 재구성
    rows.forEach(row => tbody.appendChild(row));
    
    // 상태 표시 업데이트
    currentHeader.classList.add(dir === "asc" ? "sort-asc" : "sort-desc");
}
