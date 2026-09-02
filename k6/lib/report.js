// 결과 리포트 공통 포맷.
//
// k6는 실행 중인 JS 샌드박스에서 임의 파일에 append를 못 한다(handleSummary가
// 반환하는 객체를 k6가 실행 종료 시 한 번에 파일로 쓰는 구조). 그래서 "여러 번 실행한
// 결과를 하나의 표로 비교"하려면 ①매 실행마다 파일을 하나씩 따로 남기고
// ②실행이 다 끝난 뒤 tools/merge-results.js 로 한꺼번에 모으는 2단계 구조를 쓴다.
//
// 파일명 자체에 비교축(scenario/target_env/run_label)을 넣어두면 merge 스크립트가
// 파일을 열어보지 않고 파일명만으로도 그룹을 나눌 수 있어 더 안전하다 — 그래도
// merge-results.js는 파일명과 내용(JSON) 둘 다 신뢰하지 않고 내용에서 직접 뽑는다.
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';
import { TARGET_ENV, RUN_LABEL } from './config.js';

export function buildReport(scenarioName, data, extra) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const filenameBase =
    `results/${scenarioName}__${TARGET_ENV}__${RUN_LABEL}__${timestamp}`;

  const payload = {
    scenario: scenarioName,
    target_env: TARGET_ENV,
    run_label: RUN_LABEL,
    timestamp,
    extra: extra || {},
    k6_summary: data,
  };

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }) + '\n',
    [`${filenameBase}.json`]: JSON.stringify(payload, null, 2),
  };
}
