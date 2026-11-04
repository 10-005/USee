import type { ImageSourcePropType } from 'react-native';

export type FoundReport = {
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
  latitude: number;
  longitude: number;
  name: string;
  neutered: boolean;
  species: '개' | '고양이' | '기타';
  status: '진행 중' | '완료';
  weight: string;
};

const dogBed = require('../../assets/images/found-report/report-dog-bed.jpg');
const dogGolden = require('../../assets/images/found-report/report-dog-golden.png');
const dogField = require('../../assets/images/found-report/report-dog-field.jpg');
const dogPoodle = require('../../assets/images/found-report/report-dog-poodle.png');

export const foundReports: FoundReport[] = [
  {
    age: '2살 추정',
    color: '연한 갈색',
    contact: '010-1111-2222',
    date: '2026-10-19',
    description: '침대 위에서 발견했어요. 검은색 스카프를 착용하고 있습니다.',
    gender: '암컷',
    id: 'found-1',
    images: [dogBed, dogGolden, dogField],
    isMine: true,
    location: '구미시 인동동 123-123 알리바바바',
    latitude: 36.1068,
    longitude: 128.4217,
    name: '믹스견',
    neutered: true,
    species: '개',
    status: '진행 중',
    weight: '5kg 추정',
  },
  {
    age: '4살 추정',
    color: '황금색',
    contact: '010-2222-3333',
    date: '2026-10-18',
    description: '산책로 근처에서 발견했어요. 사람을 잘 따르는 아이입니다.',
    gender: '수컷',
    id: 'found-2',
    images: [dogGolden, dogBed],
    isMine: false,
    location: '구미시 진평동 산책로 인근',
    latitude: 36.1061,
    longitude: 128.4289,
    name: '리트리버',
    neutered: false,
    species: '개',
    status: '진행 중',
    weight: '24kg 추정',
  },
  {
    age: '1살 추정',
    color: '갈색·흰색',
    date: '2026-10-17',
    description: '논길 주변에서 발견했고 목줄은 없었습니다.',
    gender: '수컷',
    id: 'found-3',
    images: [dogField],
    isMine: false,
    location: '구미시 고아읍 들판 인근',
    latitude: 36.2106,
    longitude: 128.3276,
    name: '믹스견',
    neutered: false,
    species: '개',
    status: '완료',
    weight: '7kg 추정',
  },
  {
    age: '8살 추정',
    color: '갈색',
    contact: '010-4444-5555',
    date: '2026-10-16',
    description: '주택가에서 발견했어요. 사람을 좋아하고 온순합니다.',
    gender: '암컷',
    id: 'found-4',
    images: [dogPoodle, dogGolden],
    isMine: false,
    location: '구미시 인동동 주택가',
    latitude: 36.1097,
    longitude: 128.4184,
    name: '푸들',
    neutered: true,
    species: '개',
    status: '진행 중',
    weight: '4kg 추정',
  },
];

export const getFoundReport = (id: string) =>
  foundReports.find(report => report.id === id) ?? foundReports[0];
