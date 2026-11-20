import type { AdoptionFilters } from '../../Home/AdoptionFilterSheet';
import { filterReports } from '../../found-report/FoundReportListContent';
import { getLostReport, lostReports } from '../data';

const filters: AdoptionFilters = {
  gender: '전체',
  neutered: '전체',
  period: '전체|2026.11.04',
  region: '전체',
  sort: '최신순',
  species: '전체',
  status: '전체',
};

describe('lost report data', () => {
  it('실종 상태와 특징 검색어를 함께 적용한다', () => {
    const result = filterReports(lostReports, '스카프', { ...filters, status: '실종' });

    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('lost-1');
  });

  it('종료된 신고와 비공개 연락처를 표현할 수 있다', () => {
    expect(getLostReport('lost-3').status).toBe('종료');
    expect(getLostReport('lost-2').contact).toBeUndefined();
  });

  it('성별과 중성화 필터에 모름 값을 함께 포함한다', () => {
    const unknownReport = {
      ...lostReports[0],
      gender: '모름' as const,
      id: 'lost-unknown',
      neutered: null,
    };

    expect(
      filterReports([unknownReport], '', {
        ...filters,
        gender: '수컷',
        neutered: '중성화 O',
      }),
    ).toEqual([unknownReport]);
    expect(
      filterReports([unknownReport], '', {
        ...filters,
        gender: '암컷',
        neutered: '중성화 X',
      }),
    ).toEqual([unknownReport]);
  });
});
