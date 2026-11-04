export type GeocodeCandidate = {
  jibunAddress: string;
  latitude: number;
  longitude: number;
  roadAddress: string;
};

export function getCandidateAddress(candidate: GeocodeCandidate) {
  return candidate.roadAddress || candidate.jibunAddress;
}

const mockCandidates: Array<GeocodeCandidate & { searchTerms?: string[] }> = [
  {
    jibunAddress: '경상북도 구미시 인의동 387-2',
    latitude: 36.10649558,
    longitude: 128.4225449,
    roadAddress: '경상북도 구미시 여헌로3길 34',
    searchTerms: ['인동초', '인동초등학교'],
  },
  {
    jibunAddress: '대구광역시 중구 동성로2가 1',
    latitude: 35.8714,
    longitude: 128.6014,
    roadAddress: '대구광역시 중구 동성로 1',
  },
  {
    jibunAddress: '경상북도 구미시 인의동 367-3',
    latitude: 36.1068,
    longitude: 128.4202,
    roadAddress: '경상북도 구미시 인동중앙로 15',
  },
  {
    jibunAddress: '경상북도 구미시 원평동 1008-1',
    latitude: 36.1281,
    longitude: 128.3338,
    roadAddress: '경상북도 구미시 구미중앙로 76',
  },
];

export async function searchGeocodeCandidates(query: string): Promise<GeocodeCandidate[]> {
  const normalized = query.trim().replace(/\s+/g, ' ').toLowerCase();
  if (!normalized || normalized.length > 100) {
    return [];
  }
  await Promise.resolve();
  return mockCandidates
    .filter(candidate =>
      `${candidate.roadAddress} ${candidate.jibunAddress} ${candidate.searchTerms?.join(' ') ?? ''}`
        .toLowerCase()
        .includes(normalized),
    )
    .map(candidate => ({
      jibunAddress: candidate.jibunAddress,
      latitude: candidate.latitude,
      longitude: candidate.longitude,
      roadAddress: candidate.roadAddress,
    }));
}
