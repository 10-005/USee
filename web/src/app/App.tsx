import { hasSession, sessionEvent } from '@/shared/api/session';
import { useSyncExternalStore } from 'react';
import { demoMode } from '@/shared/config/demo';
import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router';
import { ShelterPageLayout } from '@/app/layouts/ShelterPageLayout';
import { AppLayout } from '@/app/layouts/AppLayout';
import { HomePage } from '@/pages/home/HomePage';
import { NotFoundPage } from '@/pages/not-found/NotFoundPage';
import { AnimalsPage } from '@/pages/animals/AnimalsPage';
import { AnimalDetailPage } from '@/pages/animals/AnimalDetailPage';
import { AnimalFormPage } from '@/pages/animals/AnimalFormPage';
import { RecordsPage } from '@/pages/records/RecordsPage';
import { AdoptionDetailPage } from '@/pages/records/AdoptionDetailPage';
import { SimulationReportsPage } from '@/pages/records/SimulationReportsPage';
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

function subscribeSession(update: () => void) {
  window.addEventListener(sessionEvent, update);
  return () => window.removeEventListener(sessionEvent, update);
}
export function App() {
  useLocation();
  const authenticated = useSyncExternalStore(subscribeSession, hasSession);
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          (demoMode ? sessionStorage.getItem('usee-demo-login') === 'true' : authenticated) ? (
            <Outlet />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      >
        <Route
          element={
            <ShelterPageLayout>
              <Outlet />
            </ShelterPageLayout>
          }
        >
          <Route index element={<HomePage />} />
          <Route path="/animals" element={<AnimalsPage />} />
          <Route path="/animals/new" element={<AnimalFormPage />} />
          <Route path="/animals/:id" element={<AnimalDetailPage />} />
          <Route path="/animals/:id/edit" element={<AnimalFormPage />} />
          <Route path="/adoptions" element={<RecordsPage kind="adoptions" />} />
          <Route path="/adoptions/:id" element={<AdoptionDetailPage />} />
          <Route
            path="/adoptions/:id/simulation-reports/:reportId?"
            element={<SimulationReportsPage />}
          />
          <Route path="/rescues" element={<RecordsPage kind="rescues" />} />
          <Route path="/rescues/new" element={<RescueFormPage />} />
          <Route path="/rescues/:id" element={<RescueDetailPage />} />
          <Route path="/rescues/:id/edit" element={<RescueFormPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
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
      </Route>
    </Routes>
  );
}
import { lazy, Suspense } from 'react';
