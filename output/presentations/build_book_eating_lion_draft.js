const pptxgen = require('pptxgenjs');

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = 'Book Eating Lion Team 3';
pptx.subject = '최종 프로젝트 발표 초안';
pptx.title = '책 먹는 사자 - 프로젝트 발표 초안';
pptx.company = '멋쟁이사자처럼 KDT2 3팀';
pptx.lang = 'ko-KR';
pptx.theme = {
  headFontFace: 'Apple SD Gothic Neo',
  bodyFontFace: 'Apple SD Gothic Neo',
  lang: 'ko-KR'
};
pptx.defineSlideMaster({
  title: 'MASTER',
  background: { color: 'F6F2E9' },
  objects: [
    { line: { x: 0.55, y: 7.14, w: 12.25, h: 0, line: { color: 'D7D0C3', width: 0.7 } } },
    { text: { text: 'BOOK EATING LION', options: { x: 0.62, y: 7.18, w: 2.4, h: 0.18, fontFace: 'Arial', fontSize: 6.5, color: '8A8175', charSpacing: 1.8, margin: 0 } } },
    { text: { text: '3 TEAM', options: { x: 11.85, y: 7.18, w: 0.85, h: 0.18, fontFace: 'Arial', fontSize: 6.5, color: '8A8175', align: 'right', charSpacing: 1.4, margin: 0 } } }
  ],
  slideNumber: { x: 12.76, y: 7.15, color: '8A8175', fontFace: 'Arial', fontSize: 7 }
});

const C = {
  ink: '182126', muted: '657075', cream: 'F6F2E9', paper: 'FFFCF6', orange: 'E8793E',
  teal: '2B7A78', green: '6D8B55', red: 'C94C4C', line: 'D7D0C3', pale: 'EDE7DC', white: 'FFFFFF'
};

function slide(title, kicker) {
  const s = pptx.addSlide('MASTER');
  if (kicker) s.addText(kicker.toUpperCase(), { x: 0.65, y: 0.4, w: 2.6, h: 0.22, fontFace: 'Arial', fontSize: 8, bold: true, color: C.orange, charSpacing: 2, margin: 0 });
  if (title) s.addText(title, { x: 0.65, y: 0.68, w: 11.9, h: 0.72, fontSize: 27, bold: true, color: C.ink, margin: 0, breakLine: false, fit: 'shrink' });
  return s;
}
function txt(s, text, x, y, w, h, size=16, color=C.ink, opts={}) {
  s.addText(text, { x, y, w, h, fontSize: size, color, margin: 0, breakLine: false, fit: 'shrink', valign: opts.valign || 'mid', align: opts.align || 'left', bold: !!opts.bold, fontFace: opts.fontFace || 'Apple SD Gothic Neo', bullet: opts.bullet, paraSpaceAfterPt: opts.paraSpaceAfterPt || 0, lineSpacingMultiple: 1.0 });
}
function box(s, x, y, w, h, fill=C.paper, line=C.line, radius=0.12) {
  s.addShape(pptx.ShapeType.roundRect, { x, y, w, h, rectRadius: radius, fill: { color: fill }, line: { color: line, width: 1 } });
}
function pill(s, text, x, y, w, color=C.teal) {
  s.addShape(pptx.ShapeType.roundRect, { x, y, w, h: 0.38, rectRadius: 0.18, fill: { color }, line: { color } });
  txt(s, text, x, y+0.01, w, 0.34, 10, C.white, { align: 'center', bold: true });
}
function arrow(s, x, y, w, color=C.orange) {
  s.addShape(pptx.ShapeType.chevron, { x, y, w, h: 0.34, fill: { color }, line: { color } });
}
function circleLabel(s, n, label, x, y, color=C.orange) {
  s.addShape(pptx.ShapeType.ellipse, { x, y, w: 0.55, h: 0.55, fill: { color }, line: { color } });
  txt(s, String(n), x, y+0.01, 0.55, 0.5, 15, C.white, { align: 'center', bold: true });
  txt(s, label, x+0.72, y-0.03, 3.2, 0.62, 16, C.ink, { bold: true });
}
function section(title, subtitle, n, color) {
  const s = pptx.addSlide();
  s.background = { color };
  txt(s, `0${n}`, 0.8, 0.68, 1.3, 0.5, 18, C.white, { bold: true, fontFace: 'Arial' });
  txt(s, title, 0.8, 2.25, 11.7, 1.0, 36, C.white, { bold: true });
  txt(s, subtitle, 0.82, 3.42, 10.8, 0.7, 17, 'F6F2E9');
  s.addShape(pptx.ShapeType.arc, { x: 10.0, y: -0.4, w: 4.4, h: 4.4, adjustPoint: 0.35, rotate: 28, fill: { color: 'FFFFFF', transparency: 86 }, line: { color: 'FFFFFF', transparency: 100 } });
  return s;
}

// 1. Cover
{
  const s = pptx.addSlide(); s.background = { color: C.ink };
  s.addShape(pptx.ShapeType.ellipse, { x: 8.8, y: 0.0, w: 4.5, h: 4.5, fill: { color: C.orange }, line: { color: C.orange } });
  s.addShape(pptx.ShapeType.ellipse, { x: 9.8, y: 3.7, w: 3.2, h: 3.2, fill: { color: C.teal }, line: { color: C.teal } });
  txt(s, '책을 읽고,\nAI로 다시 꺼내다', 0.82, 1.42, 8.0, 1.72, 38, C.white, { bold: true });
  txt(s, 'BOOK EATING LION', 0.85, 3.46, 5.2, 0.36, 12, 'F2C6AA', { bold: true, fontFace: 'Arial' });
  txt(s, '모놀리스에서 MSA로, 기능 구현에서 운영 가능한 서비스로', 0.85, 4.04, 7.7, 0.6, 18, 'E6E1D8');
  txt(s, '멋쟁이사자처럼 KDT2 · 3팀 · 발표 초안', 0.85, 6.62, 5.4, 0.3, 10, 'AAB3B5');
}

// 2. Hook
{
  const s = slide('우리는 두 가지 문제에서 시작했습니다', 'WHY');
  box(s, 0.72, 1.72, 5.75, 4.55, 'FFF4ED', 'F0C3A8');
  txt(s, '01', 1.08, 2.05, 0.8, 0.5, 24, C.orange, { bold: true, fontFace: 'Arial' });
  txt(s, '한정 수량 도서에\n주문이 몰린다면?', 1.08, 2.75, 4.6, 1.25, 27, C.ink, { bold: true });
  txt(s, '결제 실패 · 초과 판매 · 전체 서비스 장애', 1.08, 4.48, 4.75, 0.48, 16, C.red, { bold: true });
  box(s, 6.82, 1.72, 5.75, 4.55, 'EEF6F4', 'A8CFCA');
  txt(s, '02', 7.18, 2.05, 0.8, 0.5, 24, C.teal, { bold: true, fontFace: 'Arial' });
  txt(s, '읽은 책의 지식을\n다시 활용할 수 있다면?', 7.18, 2.75, 4.75, 1.25, 27, C.ink, { bold: true });
  txt(s, '구매한 책 안에서 근거를 찾아 답변', 7.18, 4.48, 4.75, 0.48, 16, C.teal, { bold: true });
}

// 3. User journey
{
  const s = slide('발견부터 AI 질문까지, 하나의 독서 경험으로 연결했습니다', 'USER JOURNEY');
  const labels = [['발견','검색·추천'],['선택','스와이프'],['구매','결제·배송'],['읽기','EPUB'],['성장','책 먹이기'],['질문','RAG 답변']];
  labels.forEach((v,i)=>{
    const x=0.72+i*2.05;
    s.addShape(pptx.ShapeType.ellipse,{x,y:2.05,w:1.05,h:1.05,fill:{color:i<3?C.orange:C.teal},line:{color:i<3?C.orange:C.teal}});
    txt(s, String(i+1).padStart(2,'0'), x,2.29,1.05,0.42,18,C.white,{align:'center',bold:true,fontFace:'Arial'});
    txt(s,v[0],x-0.25,3.35,1.55,0.38,17,C.ink,{align:'center',bold:true});
    txt(s,v[1],x-0.35,3.82,1.75,0.32,11,C.muted,{align:'center'});
    if(i<5) arrow(s,x+1.38,2.39,0.34,i<2?C.orange:C.line);
  });
  box(s,2.15,5.15,9.0,0.82,'FFF9ED','E5D39B');
  txt(s,'핵심 차별점  |  “책을 먹인 사자에게, 구매한 책의 내용을 다시 묻는다”',2.38,5.34,8.55,0.4,16,C.ink,{align:'center',bold:true});
}

// 4. RAG flow
{
  const s = slide('사자는 책의 원문이 아니라 “의미”를 기억합니다', 'CORE FEATURE');
  const xs=[0.78,3.25,5.72,8.19,10.66];
  const titles=['EPUB 업로드','본문 분할','임베딩 생성','S3 Vectors','근거 기반 답변'];
  const subs=['S3 저장','청크 구성','Amazon Bedrock','벡터 검색','출처 포함'];
  xs.forEach((x,i)=>{
    box(s,x,2.0,1.75,2.05,i===4?'EAF4F2':C.paper,i===4?'9BC9C3':C.line);
    pill(s,String(i+1),x+0.58,2.28,0.58,i<3?C.orange:C.teal);
    txt(s,titles[i],x+0.12,3.02,1.51,0.43,15,C.ink,{align:'center',bold:true});
    txt(s,subs[i],x+0.12,3.53,1.51,0.3,10,C.muted,{align:'center'});
    if(i<4) arrow(s,x+1.87,2.85,0.34,C.line);
  });
  txt(s,'구매 권한이 있는 책의 본문 청크만 검색',2.18,4.82,4.3,0.48,17,C.teal,{bold:true});
  txt(s,'→ 저작권 보호와 답변 근거를 동시에 확보',6.04,4.82,5.0,0.48,17,C.ink,{bold:true});
}

section('왜 모놀리스를 분리했는가', '기술을 먼저 고르지 않고, 부하와 장애의 성격부터 나눴습니다.', 2, C.orange);

// 6. Monolith pain
{
  const s = slide('하나의 애플리케이션에서는 서로 다른 문제가 함께 흔들렸습니다', 'MONOLITH');
  box(s,4.45,1.78,4.25,3.98,'FFF9F3','E6B38F');
  txt(s,'MONOLITH',5.43,2.08,2.3,0.42,15,C.orange,{align:'center',bold:true,fontFace:'Arial'});
  const items=[['상품 조회 폭주',1.0,2.15],['결제·재고 락',1.0,4.32],['AI 응답 지연',9.22,2.15],['회원 인증',9.22,4.32]];
  items.forEach(([t,x,y],i)=>{ box(s,x,y,2.55,0.92,i===2?'EEF6F4':C.paper,C.line); txt(s,t,x+0.15,y+0.23,2.25,0.42,16,C.ink,{align:'center',bold:true}); s.addShape(pptx.ShapeType.line,{x:i<2?x+2.55:8.7,y:y+0.46,w:i<2?1.9:0.52,h:0,line:{color:C.red,width:2,beginArrowType:i<2?'none':'triangle',endArrowType:i<2?'triangle':'none'}}); });
  txt(s,'조회 부하',5.08,2.94,1.2,0.34,13,C.muted,{align:'center'});
  txt(s,'결제 정합성',6.38,2.94,1.3,0.34,13,C.muted,{align:'center'});
  txt(s,'LLM 지연',5.08,3.76,1.2,0.34,13,C.muted,{align:'center'});
  txt(s,'인증',6.38,3.76,1.3,0.34,13,C.muted,{align:'center'});
  txt(s,'한 영역의 장애와 확장이 전체 배포 단위에 영향을 줌',3.14,6.18,7.1,0.5,18,C.red,{align:'center',bold:true});
}

// 7. MSA split
{
  const s = slide('부하와 책임이 다른 4개 도메인으로 분리했습니다', 'MSA MIGRATION');
  const cards=[
    ['CATALOG','조회 95%','캐시·검색','E7F2EF',C.teal],
    ['ORDER','정합성','결제·재고','FFF0E7',C.orange],
    ['MEMBER','신뢰 경계','인증·회원','F1EEE8',C.green],
    ['AI','고지연 I/O','RAG·상담','EAF0F4','4C718C']
  ];
  cards.forEach((v,i)=>{const x=0.72+i*3.08; box(s,x,1.78,2.72,3.72,v[3],v[4]); pill(s,v[0],x+0.36,2.12,2.0,v[4]); txt(s,v[1],x+0.2,3.08,2.32,0.55,23,C.ink,{align:'center',bold:true}); txt(s,v[2],x+0.2,3.86,2.32,0.4,15,C.muted,{align:'center'}); txt(s,'독립 배포 · 독립 확장',x+0.24,4.78,2.24,0.3,10,v[4],{align:'center',bold:true});});
  txt(s,'서비스 경계를 넘는 DB 조인·FK를 제거하고, 값과 이벤트로 연결',1.78,6.1,9.75,0.48,17,C.ink,{align:'center',bold:true});
}

// 8. Event flow
{
  const s = slide('동기 호출은 즉시 확인에, 이벤트는 느슨한 연결에 사용했습니다', 'SERVICE COMMUNICATION');
  txt(s,'결제 경로',0.8,1.7,2.0,0.4,18,C.orange,{bold:true});
  const sync=['Order','Member 카드','Catalog 재고','결제 완료'];
  sync.forEach((t,i)=>{const x=0.82+i*3.0; box(s,x,2.2,2.25,0.88,C.paper,C.line); txt(s,t,x+0.12,2.42,2.01,0.4,15,C.ink,{align:'center',bold:true}); if(i<3) arrow(s,x+2.45,2.47,0.34,C.orange);});
  txt(s,'OpenFeign · 트랜잭션 경계 · 분산 락',0.83,3.28,5.2,0.34,11,C.muted);
  txt(s,'비동기 경로',0.8,4.05,2.0,0.4,18,C.teal,{bold:true});
  const asyncs=['구매 확정','SQS / Redis','AI 권한·인제스트','재시도 / DLQ'];
  asyncs.forEach((t,i)=>{const x=0.82+i*3.0; box(s,x,4.52,2.25,0.88,i===1?'EAF4F2':C.paper,i===1?C.teal:C.line); txt(s,t,x+0.12,4.74,2.01,0.4,15,C.ink,{align:'center',bold:true}); if(i<3) arrow(s,x+2.45,4.79,0.34,C.teal);});
  txt(s,'핵심 원칙  |  DB 커밋이 끝난 뒤에만 구매 확정 이벤트 발행',3.11,6.12,7.2,0.44,16,C.red,{align:'center',bold:true});
}

section('서비스를 운영 가능한 구조로', 'EKS, Terraform, GitHub Actions를 하나의 배포 흐름으로 연결했습니다.', 3, C.teal);

// 10. Infra
{
  const s = slide('요청은 엣지에서 보호되고, 서비스는 EKS 안에서 독립적으로 확장됩니다', 'AWS ARCHITECTURE');
  const layers=[
    [0.7,1.7,2.0,4.7,'EDGE','CloudFront\n+ WAF\nRoute 53','FFF0E7',C.orange],
    [3.0,1.7,2.0,4.7,'ENTRY','ALB / Ingress\nHost·Path 라우팅','EEF6F4',C.teal],
    [5.3,1.7,4.45,4.7,'EKS','Catalog   Order\nMember     AI\n\nHPA · Probe\nRolling Update','FFFFFF',C.ink],
    [10.05,1.7,2.55,4.7,'DATA','PostgreSQL\nValkey\nSQS · S3\nS3 Vectors','F1EEE8',C.green]
  ];
  layers.forEach((v,i)=>{box(s,v[0],v[1],v[2],v[3],v[6],v[7]); pill(s,v[4],v[0]+0.25,v[1]+0.28,v[2]-0.5,v[7]); txt(s,v[5],v[0]+0.22,v[1]+1.25,v[2]-0.44,v[3]-1.5,i===2?16:15,C.ink,{align:'center',bold:true}); if(i<3) arrow(s,v[0]+v[2]+0.08,3.82,0.25,C.line);});
}

// 11. Split vs Integrated
{
  const s = slide('같은 코드로 “격리”와 “비용 절감” 두 운영 모드를 지원합니다', 'TERRAFORM MODES');
  box(s,0.72,1.65,5.82,4.86,'FFF9F3','E8B68F');
  pill(s,'SPLIT MODE',1.08,1.98,2.0,C.orange);
  txt(s,'dev EKS',1.12,2.84,1.65,0.42,18,C.ink,{align:'center',bold:true});
  txt(s,'prod EKS',4.1,2.84,1.65,0.42,18,C.ink,{align:'center',bold:true});
  box(s,1.08,3.42,1.75,1.34,C.paper,C.line); box(s,4.05,3.42,1.75,1.34,C.paper,C.line);
  txt(s,'dev 서비스\nEC2 PostgreSQL',1.2,3.62,1.51,0.84,13,C.muted,{align:'center',bold:true});
  txt(s,'prod 서비스\nAurora + Proxy',4.17,3.62,1.51,0.84,13,C.muted,{align:'center',bold:true});
  txt(s,'환경별 완전 격리 · 높은 비용',1.1,5.42,4.95,0.38,14,C.orange,{align:'center',bold:true});
  box(s,6.82,1.65,5.82,4.86,'EEF6F4','9DCBC5');
  pill(s,'INTEGRATED MODE',7.18,1.98,2.4,C.teal);
  txt(s,'하나의 EKS 클러스터',7.85,2.82,3.7,0.42,18,C.ink,{align:'center',bold:true});
  box(s,7.28,3.42,2.18,1.34,C.paper,C.line); box(s,10.02,3.42,2.18,1.34,C.paper,C.line);
  txt(s,'dev namespace\ndev.ajttk.com',7.42,3.62,1.9,0.84,13,C.muted,{align:'center',bold:true});
  txt(s,'prod namespace\nbook.ajttk.com',10.16,3.62,1.9,0.84,13,C.muted,{align:'center',bold:true});
  txt(s,'네임스페이스 격리 · 공용 EC2 DB · 비용 절감',7.18,5.42,5.1,0.38,14,C.teal,{align:'center',bold:true});
}

// 12. Terraform layers
{
  const s = slide('인프라를 세 계층으로 나눠 생성과 삭제 순서를 통제했습니다', 'TERRAFORM LAYERS');
  const rows=[
    ['03 RUNTIME','EKS · ALB · Kubernetes · 애플리케이션','EAF4F2',C.teal],
    ['02 DATA','PostgreSQL/Aurora · Redis · SQS · S3 Vectors','FFF5E9',C.orange],
    ['01 BASE','VPC · Subnet · IAM · DNS · S3 · ECR','F1EEE8',C.green]
  ];
  rows.forEach((v,i)=>{const y=1.7+i*1.45; box(s,1.12,y,8.2,1.05,v[2],v[3]); pill(s,v[0],1.42,y+0.33,1.75,v[3]); txt(s,v[1],3.48,y+0.28,5.44,0.48,16,C.ink,{bold:true});});
  box(s,9.92,1.7,2.45,2.0,'FFF9F3','E8B68F'); txt(s,'생성',10.18,1.96,1.93,0.38,17,C.orange,{align:'center',bold:true}); txt(s,'BASE\n↓\nDATA\n↓\nRUNTIME',10.18,2.38,1.93,1.08,13,C.ink,{align:'center',bold:true});
  box(s,9.92,4.02,2.45,2.0,'EEF6F4','9DCBC5'); txt(s,'삭제',10.18,4.28,1.93,0.38,17,C.teal,{align:'center',bold:true}); txt(s,'RUNTIME\n↓\nDATA\n↓\nBASE',10.18,4.7,1.93,1.08,13,C.ink,{align:'center',bold:true});
  txt(s,'의존성 순서를 코드와 실행 절차에 함께 반영',2.25,6.28,7.9,0.4,16,C.ink,{align:'center',bold:true});
}

// 13. CI/CD
{
  const s = slide('변경된 서비스만 빌드하고, 환경별 배포 대상은 SSM에서 읽습니다', 'CI/CD');
  const stages=[['Commit','변경 감지'],['Test','모듈 검증'],['Build','Docker'],['ECR','이미지 Push'],['SSM','대상 조회'],['EKS/S3','환경별 CD']];
  stages.forEach((v,i)=>{const x=0.62+i*2.08; box(s,x,2.05,1.72,1.6,i===4?'EAF4F2':C.paper,i===4?C.teal:C.line); txt(s,v[0],x+0.12,2.38,1.48,0.4,16,i===4?C.teal:C.ink,{align:'center',bold:true,fontFace:i===0?'Arial':'Apple SD Gothic Neo'}); txt(s,v[1],x+0.12,3.02,1.48,0.28,10,C.muted,{align:'center'}); if(i<5) arrow(s,x+1.8,2.67,0.23,i===3?C.teal:C.line);});
  box(s,1.65,4.75,10.0,0.96,'FFF9ED','E7D39B');
  txt(s,'Terraform output → SSM Parameter Store → GitHub Actions',2.02,4.92,9.25,0.36,18,C.ink,{align:'center',bold:true});
  txt(s,'CloudFront ID와 S3 대상의 수동 복사를 제거',3.7,5.43,5.85,0.32,12,C.orange,{align:'center',bold:true});
}

section('실패를 운영 규칙으로 바꾸다', 'Terraform apply 성공 뒤에도 남아 있던 연결·보안·권한 문제를 해결했습니다.', 4, C.ink);

// 15. Troubleshooting 1
{
  const s = slide('Terraform Apply 성공이 서비스 성공을 의미하지 않았습니다', 'TROUBLESHOOTING 01');
  const cols=[
    ['증상','dev 배포인데\nbook.ajttk.com 노출\n\ndev 도메인은 NXDOMAIN',C.red],
    ['원인','도메인·CloudFront·SSM\n환경 매핑 불일치\n\nCD는 잘못된 대상을 조회',C.orange],
    ['개선','환경별 SSM 경로 분리\n빈 Distribution ID 조기 검증\n\ndev/prod 엣지 자원 독립',C.teal]
  ];
  cols.forEach((v,i)=>{const x=0.75+i*4.14; box(s,x,1.72,3.72,4.6,i===2?'EEF6F4':C.paper,v[2]); pill(s,v[0],x+0.35,2.06,1.1,v[2]); txt(s,v[1],x+0.34,2.85,3.04,2.44,18,C.ink,{align:'center',bold:true}); if(i<2) arrow(s,x+3.82,3.8,0.28,C.line);});
  txt(s,'교훈  |  인프라 생성 · 구성 전달 · 애플리케이션 배포를 끝까지 검증해야 한다',1.52,6.5,10.3,0.42,16,C.ink,{align:'center',bold:true});
}

// 16. Troubleshooting 2
{
  const s = slide('WAF를 붙였지만, 원본 주소가 열려 있으면 우회할 수 있었습니다', 'TROUBLESHOOTING 02');
  txt(s,'정상 경로',0.82,1.65,1.5,0.34,16,C.teal,{bold:true});
  box(s,0.82,2.1,2.0,0.82,C.paper,C.line); txt(s,'사용자',0.95,2.3,1.74,0.36,16,C.ink,{align:'center',bold:true});
  arrow(s,3.03,2.34,0.38,C.teal); box(s,3.62,2.1,2.25,0.82,'EEF6F4',C.teal); txt(s,'CloudFront + WAF',3.77,2.3,1.95,0.36,15,C.teal,{align:'center',bold:true});
  arrow(s,6.08,2.34,0.38,C.teal); box(s,6.67,2.1,2.0,0.82,C.paper,C.line); txt(s,'ALB / Ingress',6.82,2.3,1.7,0.36,15,C.ink,{align:'center',bold:true});
  arrow(s,8.88,2.34,0.38,C.teal); box(s,9.47,2.1,2.0,0.82,C.paper,C.line); txt(s,'EKS',9.62,2.3,1.7,0.36,16,C.ink,{align:'center',bold:true});
  txt(s,'우회 경로',0.82,3.9,1.5,0.34,16,C.red,{bold:true});
  box(s,0.82,4.35,2.0,0.82,C.paper,C.line); txt(s,'사용자',0.95,4.55,1.74,0.36,16,C.ink,{align:'center',bold:true});
  s.addShape(pptx.ShapeType.line,{x:3.03,y:4.76,w:6.12,h:0,line:{color:C.red,width:3,dash:'dash',endArrowType:'triangle'}});
  txt(s,'NLB DNS 직접 접근',4.48,4.3,3.2,0.38,15,C.red,{align:'center',bold:true});
  txt(s,'×',8.15,4.33,0.68,0.65,30,C.red,{align:'center',bold:true});
  box(s,2.0,5.78,9.25,0.62,'FFF0ED','E9A8A0'); txt(s,'개선  |  원본 직접 접근 차단 + CloudFront 중심 진입 경로 + WAF IP 규칙',2.18,5.9,8.9,0.34,15,C.ink,{align:'center',bold:true});
}

// 17. Troubleshooting map
{
  const s = slide('시행착오를 재현 가능한 운영 규칙으로 바꿨습니다', 'TERRAFORM LESSONS');
  const data=[
    ['Aurora 미생성','서울 리전 지원 엔진 버전 검증'],
    ['EKS 접근 불가','IAM 사용자별 Access Entry 선언'],
    ['삭제 순서 충돌','Runtime → Data → Base'],
    ['CloudFront ID 누락','SSM 기반 환경별 전달'],
    ['Split/Integrated 전환','코드 수정 대신 입력 변수로 선택']
  ];
  data.forEach((v,i)=>{const y=1.55+i*0.96; circleLabel(s,i+1,v[0],0.82,y,i<2?C.orange:C.teal); txt(s,'→',5.35,y+0.07,0.6,0.4,18,C.muted,{align:'center',bold:true}); box(s,6.06,y-0.04,5.8,0.68,i%2?'EEF6F4':C.paper,C.line); txt(s,v[1],6.34,y+0.08,5.25,0.36,15,C.ink,{bold:true});});
}

// 18. Load test
{
  const s = slide('개선 효과는 평균이 아니라 “실패 조건”으로 검증합니다', 'LOAD TEST');
  const tests=[
    ['01','트래픽 급증','P95 · 오류율 · HPA'],
    ['02','동시 결제','초과 판매 0건'],
    ['03','AI Pod 장애','주문 성공률 유지'],
    ['04','Rolling Update','배포 중 5xx 0건'],
    ['05','캐시·DB 연결','DB 부하 · 커넥션']
  ];
  tests.forEach((v,i)=>{const x=0.66+i*2.52; box(s,x,1.75,2.22,3.22,i===1?'FFF0E7':i===2?'EEF6F4':C.paper,i===1?C.orange:i===2?C.teal:C.line); txt(s,v[0],x+0.18,2.06,0.55,0.35,16,i===1?C.orange:C.teal,{bold:true,fontFace:'Arial'}); txt(s,v[1],x+0.18,2.74,1.86,0.58,18,C.ink,{align:'center',bold:true}); s.addShape(pptx.ShapeType.line,{x:x+0.38,y:3.58,w:1.46,h:0,line:{color:C.line,width:1}}); txt(s,v[2],x+0.18,3.9,1.86,0.66,12,C.muted,{align:'center',bold:true});});
  box(s,1.26,5.45,10.8,0.82,'FFF9ED','E7D39B');
  txt(s,'[실측 후 교체]  EC2 기준선  ↔  EKS Split  ↔  EKS Integrated',1.55,5.64,10.2,0.38,17,C.ink,{align:'center',bold:true});
}

// 19. Results placeholder
{
  const s = slide('결과 슬라이드는 실제 k6 수치로 완성합니다', 'MEASURED RESULTS');
  const metrics=[['P95 응답시간','___ ms'],['오류율','___ %'],['처리량','___ req/s'],['초과 판매','___ 건']];
  metrics.forEach((v,i)=>{const x=0.72+i*3.08; box(s,x,1.65,2.72,1.55,i===3?'FFF0E7':C.paper,i===3?C.orange:C.line); txt(s,v[0],x+0.18,1.93,2.36,0.34,13,C.muted,{align:'center',bold:true}); txt(s,v[1],x+0.18,2.4,2.36,0.48,23,i===3?C.orange:C.ink,{align:'center',bold:true,fontFace:'Arial'});});
  const baseY=5.75;
  s.addShape(pptx.ShapeType.line,{x:1.2,y:baseY,w:10.6,h:0,line:{color:C.line,width:1.2}});
  [['EC2',2.15,1.25,C.line],['Integrated',5.0,2.25,C.teal],['Split',7.85,3.2,C.orange]].forEach(([label,x,h,color])=>{s.addShape(pptx.ShapeType.rect,{x,y:baseY-h,w:1.55,h,fill:{color},line:{color}}); txt(s,label,x-0.15,baseY+0.16,1.85,0.34,12,C.ink,{align:'center',bold:true});});
  txt(s,'예시 차트 자리 — 수치 없는 “개선” 주장은 발표에서 사용하지 않음',3.2,6.48,7.0,0.3,11,C.red,{align:'center',bold:true});
}

// 20. Demo
{
  const s = slide('시연은 세 장면으로 끝냅니다', 'DEMO');
  const scenes=[
    ['01','발견','추천 대기열에서\n책 선택'],
    ['02','구매·읽기','결제 후 EPUB\n열람'],
    ['03','다시 활용','사자에게 먹이고\n본문 기반 질문']
  ];
  scenes.forEach((v,i)=>{const x=0.9+i*4.14; box(s,x,1.72,3.54,4.3,i===2?'EEF6F4':C.paper,i===2?C.teal:C.line); txt(s,v[0],x+0.3,2.08,0.75,0.45,20,i===2?C.teal:C.orange,{bold:true,fontFace:'Arial'}); txt(s,v[1],x+0.3,2.82,2.94,0.62,24,C.ink,{align:'center',bold:true}); txt(s,v[2],x+0.3,4.0,2.94,0.88,17,C.muted,{align:'center',bold:true});});
  txt(s,'권장 영상 길이  1분 30초 ~ 2분',4.28,6.35,4.8,0.38,15,C.orange,{align:'center',bold:true});
}

// 21. Closing
{
  const s = pptx.addSlide(); s.background={color:C.ink};
  txt(s,'기능을 만든 것에서 끝나지 않고,',0.85,1.28,9.5,0.62,25,'D8DDDD',{bold:true});
  txt(s,'장애와 트래픽을 견디는\n독서 경험을 설계했습니다.',0.85,2.08,10.5,1.62,38,C.white,{bold:true});
  const items=[['사용자','발견→구매→읽기→RAG'],['개발자','MSA→EKS→자동화'],['검증','정합성→장애 격리→부하테스트']];
  items.forEach((v,i)=>{const x=0.9+i*4.03; pill(s,v[0],x,5.02,1.05,i===1?C.orange:C.teal); txt(s,v[1],x,5.58,3.45,0.45,14,'D8DDDD',{bold:true});});
  txt(s,'THANK YOU',0.88,6.65,2.0,0.3,9,'839093',{bold:true,fontFace:'Arial'});
}

pptx.writeFile({ fileName: 'output/presentations/book-eating-lion-presentation-draft.pptx' });
