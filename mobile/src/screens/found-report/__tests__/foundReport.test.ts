import type { AdoptionFilters } from '../../Home/AdoptionFilterSheet';
import { foundReports, getFoundReport } from '../data';
import { getContactDisplayValue } from '../FoundReportDetailScreen';
import { filterFoundReports, filterReports } from '../FoundReportListContent';

const filters: AdoptionFilters = {
  gender: '전체',
  neutered: '전체',
  period: '전체|2026.11.03',
  region: '전체',
  sort: '최신순',
  species: '전체',
  status: '전체',
};

describe('found report data', () => {
  it('특징 검색어와 상태 필터를 함께 적용한다', () => {
    const result = filterFoundReports('논길', { ...filters, status: '완료' });

    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('found-3');
  });

  it('오래된 순으로 정렬한다', () => {
    const result = filterFoundReports('', { ...filters, sort: '오래된 순' });

    expect(result[0].date).toBe('2026-10-10');
    expect(result.at(-1)?.date).toBe('2026-10-19');
  });

  it('대한민국 접두어가 있는 주소도 시도 필터에 포함한다', () => {
    const report = { ...foundReports[0], location: '대한민국 경상북도 구미시 인동동' };

    const result = filterReports([report], '', { ...filters, region: '경상북도' });

    expect(result).toEqual([report]);
  });

  it('상세 조회 시 여러 이미지와 연락처 공개 여부를 유지한다', () => {
    expect(getFoundReport('found-1').images).toHaveLength(3);
    expect(getFoundReport('found-3').contact).toBeUndefined();
    expect(foundReports).toHaveLength(10);
  });

  it('비공개 연락처도 상세 화면에서 안내 문구로 표시한다', () => {
    expect(getContactDisplayValue(undefined, true)).toBe('비공개');
    expect(getContactDisplayValue('010-1111-2222', false)).toBe('비공개');
    expect(getContactDisplayValue('010-1111-2222', undefined)).toBe('비공개');
    expect(getContactDisplayValue('010-1111-2222', true)).toBe('010-1111-2222');
  });
});
