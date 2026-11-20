import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, expect, it, vi } from 'vitest';
import { AppProviders } from '@/app/providers/AppProviders';
import { ProfilePage } from './ProfilePage';

vi.mock('@/shared/config/demo', () => ({ demoMode: false }));
afterEach(() => {
  sessionStorage.clear();
  vi.unstubAllGlobals();
});

it('내 보호소를 조회하고 수정한 사진과 운영정보를 프로필과 헤더에 반영한다', async () => {
  sessionStorage.setItem(
    'usee-shelter-session',
    JSON.stringify({ accessToken: 'test', refreshToken: 'test' }),
  );
  const profile = {
    shelterName: '구미 보호소',
    address: '구미',
    phone: '054',
    operatingHours: '9~18시',
    closedDays: null,
    description: null,
    profileImage: null,
    backgroundImage: null,
  };
  const uploaded = 'https://example.org/profile.png';
  const fetchMock = vi.fn(async (url: string, options: RequestInit) => {
    let data;
    if (url.endsWith('/shelters/me') && options.method === 'PATCH') {
      expect(JSON.parse(String(options.body))).toEqual({
        operatingHours: null,
        profileImageSeq: 7,
      });
      data = {
        ...profile,
        operatingHours: null,
        profileImage: { imageSeq: 7, imageUrl: uploaded },
      };
    } else if (url.endsWith('/shelters/me')) data = profile;
    else if (url.endsWith('/images/upload-urls'))
      data = {
        uploadUrl: 'https://example.org/upload',
        imageKey: 'profile',
        expiresInSeconds: 300,
      };
    else if (url === 'https://example.org/upload') return new Response(null);
    else if (url.endsWith('/images')) data = { imageSeq: 7 };
    else throw new Error(url);
    return Response.json({
      status: 200,
      code: 'SUCCESS',
      message: '성공',
      data,
      errors: [],
      traceId: null,
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  render(
    <AppProviders>
      <MemoryRouter>
        <ShelterPageLayout>
          <ProfilePage />
        </ShelterPageLayout>
      </MemoryRouter>
    </AppProviders>,
  );
  const edit = await screen.findByRole('button', { name: '정보 수정' });
  const header = within(screen.getByRole('link', { name: '마이페이지' }));
  expect(header.getByText('구미 보호소')).toBeVisible();
  fireEvent.click(edit);
  fireEvent.change(screen.getByLabelText('운영시간'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('프로필이미지 선택'), {
    target: { files: [new File(['photo'], 'photo.png', { type: 'image/png' })] },
  });
  await waitFor(() =>
    expect(screen.getByAltText('프로필이미지').getAttribute('src')).toMatch(/^data:/),
  );
  fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
  await waitFor(() => expect(header.getByAltText('')).toHaveAttribute('src', uploaded));
  expect(screen.getByAltText('프로필이미지')).toHaveAttribute('src', uploaded);
});
import { ShelterPageLayout } from '@/app/layouts/ShelterPageLayout';
