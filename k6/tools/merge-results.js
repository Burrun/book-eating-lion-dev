#!/usr/bin/env node
// k6/results/*.json (각 시나리오 스크립트의 handleSummary가 남긴 파일)을 모아
// 엑셀에서 바로 열리는 하나의 CSV(k6/results/comparison.csv)로 합친다.
//
// k6는 실행 중 파일에 누적 append를 못 하므로(핸들서머리는 실행 종료 시 1회
// 전체 파일을 쓰는 구조), "EC2 baseline vs EKS msa" 같은 비교는 이렇게 실행이
// 끝난 뒤 별도 단계에서 모으는 2단계 구조를 쓴다.
//
// 사용법:
//   node k6/tools/merge-results.js
//   node k6/tools/merge-results.js --dir k6/results --out k6/results/comparison.csv
//
// 출력된 comparison.csv를 엑셀에서 열어 scenario/target_env/run_label 기준으로
// 피벗 테이블을 만들면 "어느 인프라가 효율이 좋은가"를 바로 비교할 수 있다.
const fs = require('fs');
const path = require('path');

// 실행 위치(cwd)에 상관없이 이 파일 기준(k6/tools/../results)으로 기본 경로를 잡는다.
// k6 스크립트의 handleSummary가 남기는 results/*.json도 "k6 run"을 k6/ 디렉터리에서
// 실행한다는 전제로 상대경로를 쓰므로, 두 경로가 어긋나지 않게 여기서 고정해둔다.
const DEFAULT_RESULTS_DIR = path.join(__dirname, '..', 'results');

function parseArgs() {
  const args = process.argv.slice(2);
  const opts = { dir: DEFAULT_RESULTS_DIR, out: null };
  for (let i = 0; i < args.length; i += 1) {
    if (args[i] === '--dir') opts.dir = args[++i];
    if (args[i] === '--out') opts.out = args[++i];
  }
  if (!opts.out) opts.out = path.join(opts.dir, 'comparison.csv');
  return opts;
}

function flatten(prefix, obj, into) {
  if (obj === null || obj === undefined) return;
  if (typeof obj !== 'object') {
    into[prefix] = obj;
    return;
  }
  for (const key of Object.keys(obj)) {
    const nextPrefix = prefix ? `${prefix}.${key}` : key;
    flatten(nextPrefix, obj[key], into);
  }
}

function csvEscape(value) {
  if (value === undefined || value === null) return '';
  const str = String(value);
  if (/[",\n]/.test(str)) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

function main() {
  const { dir, out } = parseArgs();
  if (!fs.existsSync(dir)) {
    console.error(`결과 디렉터리가 없다: ${dir}`);
    process.exit(1);
  }

  const files = fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .map((f) => path.join(dir, f));

  if (files.length === 0) {
    console.error(`${dir} 안에 *.json 결과 파일이 없다. 먼저 k6 run으로 시나리오를 실행할 것.`);
    process.exit(1);
  }

  const rows = [];
  const columnOrder = ['file', 'scenario', 'target_env', 'run_label', 'timestamp'];
  const seenColumns = new Set(columnOrder);

  for (const file of files) {
    let payload;
    try {
      payload = JSON.parse(fs.readFileSync(file, 'utf-8'));
    } catch (e) {
      console.warn(`파싱 실패, 건너뜀: ${file} (${e.message})`);
      continue;
    }

    const row = {
      file: path.basename(file),
      scenario: payload.scenario || '',
      target_env: payload.target_env || '',
      run_label: payload.run_label || '',
      timestamp: payload.timestamp || '',
    };

    // extra(시나리오별 부가 메모)와 k6 metrics를 전부 평탄화해서 컬럼으로 편입.
    // 파일마다 있는 metric이 다를 수 있어(예: 05번만 order_paid 카운터가 있음)
    // 컬럼 합집합을 나중에 계산한다 — 없는 값은 빈 칸으로 둔다.
    flatten('extra', payload.extra || {}, row);
    const metrics = (payload.k6_summary && payload.k6_summary.metrics) || {};
    for (const metricName of Object.keys(metrics)) {
      flatten(`metric.${metricName}`, metrics[metricName].values || {}, row);
    }

    for (const key of Object.keys(row)) {
      seenColumns.add(key);
    }
    rows.push(row);
  }

  const restColumns = [...seenColumns].filter((c) => !columnOrder.includes(c)).sort();
  const header = [...columnOrder, ...restColumns];

  const lines = [header.map(csvEscape).join(',')];
  for (const row of rows) {
    lines.push(header.map((col) => csvEscape(row[col])).join(','));
  }

  fs.writeFileSync(out, lines.join('\n') + '\n', 'utf-8');
  console.log(`${rows.length}개 결과 파일을 합쳐 ${out} 에 저장했다 (컬럼 ${header.length}개).`);
  console.log('엑셀에서 열어 scenario/target_env/run_label 기준으로 피벗 테이블을 만들 것.');
}

main();
