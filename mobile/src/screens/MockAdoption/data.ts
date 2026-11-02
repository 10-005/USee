export type PetCategory = 'dog' | 'cat';

export const mockAdoptionPets = [
  ...['말티즈', '푸들', '포메라니안', '비숑 프리제', '골든 리트리버', '진돗개'].map(
    (name, index) => ({ id: `dog-${index + 1}`, name, category: 'dog' as PetCategory }),
  ),
  ...['코리안 쇼트헤어', '러시안 블루', '랙돌', '페르시안', '브리티시 쇼트헤어', '샴'].map(
    (name, index) => ({ id: `cat-${index + 1}`, name, category: 'cat' as PetCategory }),
  ),
];
