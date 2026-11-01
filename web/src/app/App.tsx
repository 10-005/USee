import { Route, Routes } from 'react-router';
import { AppLayout } from '@/app/layouts/AppLayout';
import { HomePage } from '@/pages/home/HomePage';
import { NotFoundPage } from '@/pages/not-found/NotFoundPage';
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
        <Route index element={<HomePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
import { lazy, Suspense } from 'react';
