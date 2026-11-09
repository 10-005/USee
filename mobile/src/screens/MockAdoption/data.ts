export type PetCategory = 'dog' | 'cat';

import type { ImageSourcePropType } from 'react-native';
import type { CostGroup } from './initialCosts';

export const mockAdoptionPets: Array<{
  id: string;
  name: string;
  breedCode: string;
  category: PetCategory;
  costGroup: CostGroup;
  image: ImageSourcePropType;
}> = [
  {
    id: 'dog-1',
    name: '비숑 프리제',
    breedCode: '000018',
    costGroup: 'small',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_bichon.png'),
  },
  {
    id: 'dog-2',
    name: '도베르만',
    breedCode: '000041',
    costGroup: 'large',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_doberman.png'),
  },
  {
    id: 'dog-3',
    name: '푸들',
    breedCode: '000128',
    costGroup: 'small',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_poodle.png'),
  },
  {
    id: 'dog-4',
    name: '골든 리트리버',
    breedCode: '000054',
    costGroup: 'large',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_retriever.png'),
  },
  {
    id: 'dog-5',
    name: '시바',
    breedCode: '000100',
    costGroup: 'medium',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_shiba_inu.png'),
  },
  {
    id: 'dog-6',
    name: '시츄',
    breedCode: '000101',
    costGroup: 'small',
    category: 'dog',
    image: require('../../assets/images/mock-adoption/img_dog_shih_tzu.png'),
  },
  {
    id: 'cat-1',
    name: '브리티시 쇼트헤어',
    breedCode: '000181',
    costGroup: 'cat',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_korean_shorthair.png'),
  },
  {
    id: 'cat-2',
    name: '러시안 블루',
    breedCode: '000172',
    costGroup: 'cat',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_russian_blue.png'),
  },
  {
    id: 'cat-3',
    name: '사바나캣',
    breedCode: '000182',
    costGroup: 'cat',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_ragdoll.png'),
  },
  {
    id: 'cat-4',
    name: '페르시안',
    breedCode: '00214',
    costGroup: 'cat',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_persian.png'),
  },
  {
    id: 'cat-5',
    name: '스핑크스',
    breedCode: '000189',
    costGroup: 'cat',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_sphynx.png'),
  },
  {
    id: 'cat-6',
    name: '샴',
    breedCode: '000184',
    costGroup: 'cat',
    category: 'cat',
    image: require('../../assets/images/mock-adoption/img_cat_siamese.png'),
  },
];
