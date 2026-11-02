export type PetCategory = 'dog' | 'cat';

import type { ImageSourcePropType } from 'react-native';

export const mockAdoptionPets: Array<{
  id: string;
  name: string;
  category: PetCategory;
  image: ImageSourcePropType;
}> = [
  {
    id: 'dog-1',
    name: '비숑 프리제',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_bichon.png'),
  },
  {
    id: 'dog-2',
    name: '도베르만',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_doberman.png'),
  },
  {
    id: 'dog-3',
    name: '푸들',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_poodle.png'),
  },
  {
    id: 'dog-4',
    name: '골든 리트리버',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_retriever.png'),
  },
  {
    id: 'dog-5',
    name: '시바 이누',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_shiba_inu.png'),
  },
  {
    id: 'dog-6',
    name: '시추',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_shih_tzu.png'),
  },
  {
    id: 'cat-1',
    name: '코리안 쇼트헤어',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_korean_shorthair.png'),
  },
  {
    id: 'cat-2',
    name: '러시안 블루',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_russian_blue.png'),
  },
  {
    id: 'cat-3',
    name: '랙돌',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_ragdoll.png'),
  },
  {
    id: 'cat-4',
    name: '페르시안',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_persian.png'),
  },
  {
    id: 'cat-5',
    name: '스핑크스',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_sphynx.png'),
  },
  {
    id: 'cat-6',
    name: '샴',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_siamese.png'),
  },
];
