import { isAdoptionStepValid } from '../AdoptionApplicationScreen';

describe('adoption application validation', () => {
  it('requires the address and visit date on the applicant step', () => {
    const empty = { address: '', visitDate: '' } as Parameters<typeof isAdoptionStepValid>[0];
    expect(isAdoptionStepValid(empty, 1)).toBe(false);

    const complete = { address: '서울시 강남구', visitDate: '2026.10.10' } as Parameters<
      typeof isAdoptionStepValid
    >[0];
    expect(isAdoptionStepValid(complete, 1)).toBe(true);
  });

  it('allows the optional public consent to remain unchecked', () => {
    const consent = {
      consentHealth: true,
      consentResponsibility: true,
      consentCost: true,
      consentLostPrevention: true,
      consentNoCommercialUse: true,
      consentInformationCheck: true,
      consentBudget: true,
      consentAdaptation: true,
      consentVisit: true,
      consentReturn: true,
      consentPrivacy: true,
      consentShelter: true,
      consentPublic: false,
    } as Parameters<typeof isAdoptionStepValid>[0];

    expect(isAdoptionStepValid(consent, 4)).toBe(true);
  });

  it('requires additional answers for conditional household questions', () => {
    const household = {
      housing: '아파트',
      petAllowed: '가능',
      cohabitant: '있음',
      cohabitantConsent: '',
      primaryCaretaker: '본인',
      livingSpace: '실내',
      aloneTime: '2시간 미만',
      currentPets: '없음',
      pastExperience: '없음',
      emergencyPlan: '있음',
      alternativeCare: '가족에게 맡깁니다.',
    } as Parameters<typeof isAdoptionStepValid>[0];

    expect(isAdoptionStepValid(household, 2)).toBe(false);
    household.cohabitantConsent = '전원 동의';
    expect(isAdoptionStepValid(household, 2)).toBe(true);
  });

  it('requires education details when education is completed', () => {
    const carePlan = {
      motivation: '입양 동기',
      dailyPlan: '일상 돌봄 계획',
      medicalPlan: '치료 계획',
      changePlan: '환경 변화 계획',
      education: '이수',
      educationInstitution: '',
      educationCourse: '',
      educationDate: '',
    } as Parameters<typeof isAdoptionStepValid>[0];

    expect(isAdoptionStepValid(carePlan, 3)).toBe(false);
    carePlan.educationInstitution = '반려동물 교육기관';
    carePlan.educationCourse = '입양 교육';
    carePlan.educationDate = '2026.10.10';
    expect(isAdoptionStepValid(carePlan, 3)).toBe(true);
  });
});
