// 리포트 API 연동 전 화면 확인용 예시 데이터입니다.
export type MockAdoptionReport = {
  id: string;
  petId: string;
  petName: string;
  createdAt: string;
  summary: string;
  isNew: boolean;
};

export const mockAdoptionReports: MockAdoptionReport[] = [
  {
    id: 'sample-cheese',
    petId: 'cat-1',
    petName: '치즈',
    createdAt: '2026.10.20',
    summary: '치즈와 함께한 일주일, 서로에게 조금 더 가까워졌어요.',
    isNew: true,
  },
  {
    id: 'sample-coco',
    petId: 'dog-5',
    petName: '코코',
    createdAt: '2026.10.15',
    summary:
      '코코는 매우 친화적이지만 초기에는 경계심이 있습니다. 꾸준한 교감과 훈련이 필요해 보여요.',
    isNew: true,
  },
];
