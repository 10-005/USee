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
      consentAdaptation: true,
      consentVisit: true,
      consentReturn: true,
      consentPrivacy: true,
      consentShelter: true,
      consentPublic: false,
    } as Parameters<typeof isAdoptionStepValid>[0];

    expect(isAdoptionStepValid(consent, 4)).toBe(true);
  });
});
