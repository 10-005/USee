/** 현재 화면이 사용하는 발견 신고 데이터. 서버의 DTO 형식은 아닙니다. */
export type Report = {
  id: string;
  name: string;
  species: string;
  location: string;
  reportStatus: string;
  foundAt: string;
  createdAt: string;
  traits: string;
  reporter: string;
  contact: string;
  animalStatus: string;
  image: string;
};
