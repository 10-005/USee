import type { ReactNode } from 'react';
import { LoadStateMessage } from '@/shared/ui/LoadState';
import { WorkPage } from '@/shared/ui/WorkPage';

type Query = { isPending: boolean; error: Error | null; refetch: () => Promise<unknown> };
export function RecordQueries({
  title,
  queries,
  children,
  back,
}: {
  title: string;
  queries: Query[];
  children: ReactNode;
  back?: string;
}) {
  const failed = queries.find((q) => q.error);
  if (failed || queries.some((q) => q.isPending))
    return (
      <WorkPage title={title} back={back}>
        <LoadStateMessage
          state={
            failed
              ? {
                  status: 'error',
                  message: failed.error?.message,
                  retry: () => void failed.refetch(),
                }
              : { status: 'loading' }
          }
        >
          {null}
        </LoadStateMessage>
      </WorkPage>
    );
  return <>{children}</>;
}
