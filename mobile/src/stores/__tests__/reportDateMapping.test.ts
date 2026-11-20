import { mapFoundDetail, mapFoundSummary } from '../foundReportStore';
import { mapMissingDetail, mapMissingSummary } from '../missingReportStore';

const commonSummary = {
  age: null,
  address: '서울특별시 강남구',
  classification: null,
  districtCode: null,
  gender: 'UNKNOWN' as const,
  isNeutered: null,
  occurredDttm: '2026-11-14T09:10:00+09:00',
  regDttm: '2026-11-16T15:20:00+09:00',
  representativeImageUrl: null,
  shelterSeq: null,
  status: 'ACTIVE' as const,
  weight: null,
};

describe('제보·실종 게시글 날짜 매핑', () => {
  it('목록에 발생일이 아닌 게시글 등록일을 표시한다', () => {
    expect(
      mapFoundSummary({ ...commonSummary, foundReportSeq: 1 }, false).date,
    ).toBe('2026-11-16');
    expect(
      mapMissingSummary({ ...commonSummary, missingReportSeq: 2 }, false).date,
    ).toBe('2026-11-16');
  });

  it('상세에 게시글 등록 시간을 표시한다', () => {
    const detailFields = {
      commentCount: 0,
      description: null,
      images: [],
      isBookmarked: false,
      isPhonePublic: false,
      latitude: 37.5,
      longitude: 127,
      microchipNumber: null,
      modDttm: null,
      user: null,
    };

    expect(
      mapFoundDetail(
        {
          ...commonSummary,
          ...detailFields,
          closedDttm: null,
          closedReason: null,
          foundReportSeq: 1,
          landmarkDescription: null,
          reporter: null,
          reporterPhone: null,
          shelter: null,
        },
        false,
      ).date,
    ).toBe('2026-11-16 오후 3:20');
    expect(
      mapMissingDetail(
        {
          ...commonSummary,
          ...detailFields,
          contactPhone: null,
          missingReportSeq: 2,
        },
        false,
      ).date,
    ).toBe('2026-11-16 오후 3:20');
  });

  it('이전 데이터에 등록 시간이 없으면 발생 시간을 사용한다', () => {
    expect(
      mapFoundSummary({ ...commonSummary, foundReportSeq: 1, regDttm: null }, false).date,
    ).toBe('2026-11-14');
    expect(
      mapMissingSummary({ ...commonSummary, missingReportSeq: 2, regDttm: null }, false).date,
    ).toBe('2026-11-14');
  });
});
