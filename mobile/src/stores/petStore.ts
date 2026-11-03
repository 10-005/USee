import type { ImageSourcePropType } from 'react-native';
import { create } from 'zustand';

export type Pet = {
  age: number | null;
  breed: string;
  color: string;
  description: string;
  gender: '암컷' | '수컷' | '모름';
  id: string;
  image: ImageSourcePropType;
  images: ImageSourcePropType[];
  microchipNumber: string;
  name: string;
  neutered: boolean | null;
  species: string;
  weight: number | null;
};

const initialPets: Pet[] = [
  {
    age: 8,
    breed: '말티즈',
    color: '흰색',
    description: '꼬리 끝이 뭉툭함',
    gender: '수컷',
    id: 'mongsil',
    image: require('../assets/images/mypage/pets/mongsil.png'),
    images: [
      require('../assets/images/mypage/pets/mongsil.png'),
      require('../assets/images/mypage/pets/mongsil.png'),
      require('../assets/images/mypage/pets/mongsil.png'),
    ],
    microchipNumber: '1234567890000',
    name: '몽실이',
    neutered: true,
    species: '강아지',
    weight: 5,
  },
  {
    age: 13,
    breed: '푸들',
    color: '갈색',
    description: '사람을 좋아하고 온순함',
    gender: '암컷',
    id: 'bori',
    image: require('../assets/images/mypage/pets/bori.png'),
    images: [
      require('../assets/images/mypage/pets/bori.png'),
      require('../assets/images/mypage/pets/bori.png'),
      require('../assets/images/mypage/pets/bori.png'),
    ],
    microchipNumber: '9876543210000',
    name: '보리',
    neutered: false,
    species: '강아지',
    weight: 4,
  },
];

type PetState = {
  addPet: (pet: Pet) => void;
  deletePet: (petId: string) => void;
  pets: Pet[];
  resetPets: () => void;
  updatePet: (pet: Pet) => void;
};

export const usePetStore = create<PetState>(set => ({
  addPet: pet => set(state => ({ pets: [...state.pets, pet] })),
  deletePet: petId => set(state => ({ pets: state.pets.filter(pet => pet.id !== petId) })),
  pets: initialPets,
  resetPets: () => set({ pets: initialPets }),
  updatePet: pet =>
    set(state => ({ pets: state.pets.map(item => (item.id === pet.id ? pet : item)) })),
}));
