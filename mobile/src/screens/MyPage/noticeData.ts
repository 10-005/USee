export type Notice = {
  content: string;
  date: string;
  id: string;
  title: string;
};

export const notices: Notice[] = [
  {
    content: `안녕하세요. USEE 운영팀입니다.

더욱 안정적인 서비스 제공을 위해 아래와 같이 정기 점검을 진행할 예정입니다.

■ 점검 일시
2026년 10월 29일 02:00 ~ 04:00

■ 점검 내용
서비스 안정화 및 시스템 개선

점검 시간 동안 일부 서비스 이용이 제한될 수 있습니다.

이용에 불편을 드려 죄송합니다.
보다 나은 서비스로 보답하겠습니다.

감사합니다.
USEE 운영팀 드림`,
    date: '2026.10.26',
    id: 'maintenance-20261026',
    title: 'USEE 서비스 정기 점검 안내',
  },
  {
    content: `안녕하세요. USEE 운영팀입니다.

실종·발견 동물의 특징을 비교하는 AI 매칭 기능이 업데이트되었습니다.

더 정확하고 편리한 서비스를 제공할 수 있도록 지속적으로 개선하겠습니다.`,
    date: '2026.10.23',
    id: 'ai-matching-update',
    title: 'AI 매칭 기능 업데이트 안내',
  },
  {
    content: `모의입양 서비스를 통해 반려동물과 함께하는 생활을 미리 경험해 보세요.

돌봄 활동과 시뮬레이션 결과는 마이페이지에서 확인할 수 있습니다.`,
    date: '2026.10.20',
    id: 'mock-adoption-guide',
    title: '모의입양 서비스 이용 안내',
  },
  {
    content: `안녕하세요. USEE 운영팀입니다.

유기동물의 새로운 가족을 연결하는 USEE 서비스를 시작합니다.

많은 관심과 이용 부탁드립니다.`,
    date: '2026.10.15',
    id: 'service-open',
    title: 'USEE 서비스 오픈 안내',
  },
];
