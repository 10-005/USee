import { Route, Routes } from 'react-router';
import { AppLayout } from '@/app/layouts/AppLayout';
import { HomePage } from '@/pages/home/HomePage';
import { NotFoundPage } from '@/pages/not-found/NotFoundPage';
import { AnimalsPage } from '@/pages/animals/AnimalsPage';
import { AnimalDetailPage } from '@/pages/animals/AnimalDetailPage';
import { AnimalFormPage } from '@/pages/animals/AnimalFormPage';
import { RecordsPage } from '@/pages/records/RecordsPage';
import { AdoptionDetailPage } from '@/pages/records/AdoptionDetailPage';
import { RescueDetailPage, RescueFormPage } from '@/pages/records/RescuePages';
import { ReportsPage } from '@/pages/records/ReportsPage';
import { ProfilePage } from '@/pages/profile/ProfilePage';
import { LoginPage } from '@/pages/login/LoginPage';
const UiPreviewPage = import.meta.env.DEV
  ? lazy(() =>
      import('@/pages/ui-preview/UiPreviewPage').then((module) => ({
        default: module.UiPreviewPage,
      })),
    )
  : null;
const ShelterPreviewPage = import.meta.env.DEV
  ? lazy(() =>
      import('@/pages/ui-preview/ShelterPreviewPage').then((module) => ({
        default: module.ShelterPreviewPage,
      })),
    )
  : null;

export function App() {
  return (
    <Routes>
      <Route index element={<HomePage />} />
      <Route path="/animals" element={<AnimalsPage />} />
      <Route path="/animals/new" element={<AnimalFormPage />} />
      <Route path="/animals/:id" element={<AnimalDetailPage />} />
      <Route path="/animals/:id/edit" element={<AnimalFormPage />} />
      <Route path="/adoptions" element={<RecordsPage kind="adoptions" />} />
      <Route path="/adoptions/:id" element={<AdoptionDetailPage />} />
      <Route path="/rescues" element={<RecordsPage kind="rescues" />} />
      <Route path="/rescues/new" element={<RescueFormPage />} />
      <Route path="/rescues/:id" element={<RescueDetailPage />} />
      <Route path="/rescues/:id/edit" element={<RescueFormPage />} />
      <Route path="/reports" element={<ReportsPage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/login" element={<LoginPage />} />
      {UiPreviewPage && (
        <Route
          path="/dev/ui"
          element={
            <Suspense fallback={<p>불러오는 중...</p>}>
              <UiPreviewPage />
            </Suspense>
          }
        />
      )}
      {ShelterPreviewPage && (
        <Route
          path="/dev/shelter"
          element={
            <Suspense fallback={<p>불러오는 중...</p>}>
              <ShelterPreviewPage />
            </Suspense>
          }
        />
      )}
      <Route element={<AppLayout />}>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
import { lazy, Suspense } from 'react';
