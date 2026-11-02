import type { ShelterAnimal } from './model';

const commonColors = ['흰색', '검정색', '갈색', '회색', '혼합색', '기타'];

export const breedColors: Record<ShelterAnimal['species'], Record<string, string[]>> = {
  dog: {
    푸들: ['흰색', '갈색', '살구색', '검정색', '회색', '기타'],
    말티즈: ['흰색', '크림색', '기타'],
    포메라니안: ['갈색', '흰색', '크림색', '검정색', '혼합색', '기타'],
    진돗개: ['흰색', '황색', '검정색', '혼합색', '기타'],
    믹스견: commonColors,
    기타: commonColors,
  },
  cat: {
    코리안숏헤어: commonColors,
    러시안블루: ['회색', '기타'],
    샴: ['크림색', '혼합색', '기타'],
    페르시안: commonColors,
    믹스묘: commonColors,
    기타: commonColors,
  },
  other: {
    토끼: commonColors,
    햄스터: ['갈색', '흰색', '회색', '혼합색', '기타'],
    기타: commonColors,
  },
};
