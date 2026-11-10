import type { ImageSourcePropType } from 'react-native';

export type LostReport = {
  age: string;
  color: string;
  contact?: string;
  date: string;
  description: string;
  gender: '수컷' | '암컷' | '모름';
  id: string;
  images: ImageSourcePropType[];
  isMine: boolean;
  location: string;
  name: string;
  neutered: boolean | null;
  species: '개' | '고양이' | '기타';
  status: '실종' | '종료';
  weight: string;
};

const dogBed = require('../../assets/images/found-report/report-dog-bed.jpg');
const dogGolden = require('../../assets/images/found-report/report-dog-golden.png');
const dogField = require('../../assets/images/found-report/report-dog-field.jpg');
const dogPoodle = require('../../assets/images/found-report/report-dog-poodle.png');

export const lostReports: LostReport[] = [
  {
    age: '2살',
    color: '연한 갈색',
    contact: '010-1234-5678',
    date: '2026-10-19',
    description: '검은색 스카프를 착용하고 있어요. 겁이 많지만 사람을 잘 따릅니다.',
    gender: '암컷',
    id: 'lost-1',
    images: [dogBed, dogGolden, dogField],
    isMine: true,
    location: '구미시 인동동 123-123 알리바바바',
    name: '강강아쥐',
    neutered: true,
    species: '개',
    status: '실종',
    weight: '5kg',
  },
  {
    age: '4살',
    color: '황금색',
    date: '2026-10-18',
    description: '빨간색 목줄을 하고 있어요. 보시면 가까이 가지 말고 연락 부탁드립니다.',
    gender: '수컷',
    id: 'lost-2',
    images: [dogGolden, dogBed],
    isMine: false,
    location: '구미시 진평동 산책로 인근',
    name: '봄이',
    neutered: false,
    species: '개',
    status: '실종',
    weight: '24kg',
  },
  {
    age: '1살',
    color: '갈색·흰색',
    contact: '010-3456-7890',
    date: '2026-10-17',
    description: '파란색 하네스를 착용하고 있습니다.',
    gender: '수컷',
    id: 'lost-3',
    images: [dogField],
    isMine: false,
    location: '구미시 고아읍 들판 인근',
    name: '보리',
    neutered: false,
    species: '개',
    status: '종료',
    weight: '7kg',
  },
  {
    age: '8살',
    color: '갈색',
    contact: '010-4567-8901',
    date: '2026-10-16',
    description: '귀가 길고 꼬리가 짧아요. 이름을 부르면 반응합니다.',
    gender: '암컷',
    id: 'lost-4',
    images: [dogPoodle, dogGolden],
    isMine: false,
    location: '구미시 인동동 주택가',
    name: '콩이',
    neutered: true,
    species: '개',
    status: '실종',
    weight: '4kg',
  },
  {
    age: '3살',
    color: '크림색',
    contact: '010-5678-9012',
    date: '2026-10-15',
    description: '파란색 목줄에 은색 인식표를 달고 있습니다.',
    gender: '수컷',
    id: 'lost-5',
    images: [dogGolden, dogField],
    isMine: false,
    location: '서울시 마포구 월드컵공원 인근',
    name: '해피',
    neutered: true,
    species: '개',
    status: '실종',
    weight: '9kg',
  },
  {
    age: '5살',
    color: '흰색',
    date: '2026-10-14',
    description: '분홍색 옷을 입고 있으며 이름을 부르면 바로 반응합니다.',
    gender: '암컷',
    id: 'lost-6',
    images: [dogPoodle, dogBed],
    isMine: false,
    location: '대전시 유성구 봉명동 주택가',
    name: '별이',
    neutered: false,
    species: '개',
    status: '종료',
    weight: '5kg',
  },
  {
    age: '1살',
    color: '갈색',
    contact: '010-6789-0123',
    date: '2026-10-13',
    description: '빨간색 하네스를 착용했고 낯선 사람을 무서워합니다.',
    gender: '모름',
    id: 'lost-7',
    images: [dogField, dogGolden],
    isMine: false,
    location: '부산시 해운대구 우동 아파트 단지',
    name: '초코',
    neutered: false,
    species: '개',
    status: '실종',
    weight: '6kg',
  },
  {
    age: '6살',
    color: '황금색',
    date: '2026-10-12',
    description: '초록색 목줄을 하고 있고 오른쪽 귀 끝에 작은 흉터가 있습니다.',
    gender: '수컷',
    id: 'lost-8',
    images: [dogGolden],
    isMine: false,
    location: '전주시 덕진구 전주천 산책로',
    name: '태양이',
    neutered: true,
    species: '개',
    status: '실종',
    weight: '26kg',
  },
  {
    age: '2살',
    color: '갈색·흰색',
    date: '2026-10-11',
    description: '노란색 스카프를 착용하고 있으며 꼬리 끝이 흰색입니다.',
    gender: '암컷',
    id: 'lost-9',
    images: [dogBed, dogField],
    isMine: false,
    location: '인천시 남동구 구월동 중앙공원',
    name: '두부',
    neutered: false,
    species: '개',
    status: '종료',
    weight: '8kg',
  },
  {
    age: '4살',
    color: '진한 갈색',
    contact: '010-7890-1234',
    date: '2026-10-10',
    description: '초록색 목줄을 하고 있고 털을 짧게 미용한 상태입니다.',
    gender: '수컷',
    id: 'lost-10',
    images: [dogPoodle, dogGolden],
    isMine: false,
    location: '수원시 영통구 이의동 학교 인근',
    name: '모카',
    neutered: true,
    species: '개',
    status: '실종',
    weight: '6kg',
  },
];

export const getLostReport = (id: string) =>
  lostReports.find(report => report.id === id) ?? lostReports[0];
