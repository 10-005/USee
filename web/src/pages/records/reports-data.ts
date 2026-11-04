import dog from '@/shared/assets/figma/animal-example.png';
import cat from '@/shared/assets/figma/demo-cat.png';
import poodle from '@/shared/assets/figma/demo-poodle.png';
export const initialReports = Array.from({ length: 23 }, (_, i) => ({
  id: `F-${123 + i}`,
  name: i % 2 ? '[개] 푸들' : '[고양이] 코리안숏헤어',
  species: i % 2 ? 'dog' : 'cat',
  location:
    i < 2 || i % 3 === 0
      ? '구미시 인동동 123-123 구미아파트'
      : i % 3 === 1
        ? '김천시 율곡동 공원 입구'
        : '칠곡군 석적읍 산책로',
  reportStatus: i % 4 === 3 ? '신고 종료' : '신고 진행 중',
  foundAt: `2026-10-${String(Math.min(19, 29 - i)).padStart(2, '0')}T18:30`,
  createdAt: `2026-10-${String(Math.min(20, 30 - i)).padStart(2, '0')}T09:${String(i).padStart(2, '0')}`,
  traits: i % 2 ? '2살 · 수컷 · 갈색 · 3kg' : '0살 · 암컷 · 갈색 · 체중 미상',
  reporter: '해피맘',
  contact: '010-1234-1234',
  animalStatus: i < 2 ? '입양 완료' : '보호 중',
  image: i % 2 ? (i % 3 ? poodle : dog) : cat,
}));
