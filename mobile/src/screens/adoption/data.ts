import type { ImageSourcePropType } from 'react-native';

export type AdoptionAnimal = {
  id: string;
  image: ImageSourcePropType;
  name: string;
  neutered: boolean;
  species: string;
  location: string;
  date: string;
  summary: string;
  description: string;
  status: '보호' | '종료';
};

export const adoptionAnimals: AdoptionAnimal[] = [
  {
    id: 'animal-1',
    image: require('../../assets/images/adoption/animal-cat.png'),
    name: '한국고양이',
    neutered: true,
    species: '고양이',
    location: '구미시 인동동 123-123 알리바바바',
    date: '2026-10-19',
    summary: '0살 | 암컷 | 갈색 | 3kg',
    description: '사람을 좋아하고 온순한 아이예요.',
    status: '보호',
  },
  {
    id: 'animal-2',
    image: require('../../assets/images/adoption/animal-dog-leaves.png'),
    name: '강강아쥐',
    neutered: false,
    species: '개',
    location: '구미시 인동동 123-123 알리바바바',
    date: '2026-10-19',
    summary: '0살 | 암컷 | 흰색 | 3kg',
    description: '활발하고 산책을 좋아해요.',
    status: '보호',
  },
  {
    id: 'animal-3',
    image: require('../../assets/images/adoption/animal-dog-white.png'),
    name: '개냥이',
    neutered: true,
    species: '개',
    location: '구미시 인동동 123-123 알리바바바',
    date: '2026-10-19',
    summary: '1살 | 수컷 | 흰색 | 4kg',
    description: '새 가족을 기다리고 있습니다.',
    status: '종료',
  },
  {
    id: 'animal-4',
    image: require('../../assets/images/adoption/animal-cat-black.png'),
    name: '개냥이',
    neutered: false,
    species: '고양이',
    location: '구미시 인동동 123-123 알리바바바',
    date: '2026-10-19',
    summary: '2살 | 암컷 | 검정·흰색 | 4kg',
    description: '호기심이 많고 사람 손길을 좋아해요.',
    status: '보호',
  },
];
