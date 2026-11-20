jest.mock('../../api/client', () => ({ apiClient: { post: jest.fn() } }));

import { apiClient } from '../../api/client';
import { createReport } from '../reportApi';

describe('reportApi', () => {
  it('대상과 사유 코드로 신고를 접수한다', async () => {
    const request = {
      reasonCode: 'FALSE_INFO' as const,
      targetSeq: 101,
      targetType: 'MISSING' as const,
    };
    (apiClient.post as jest.Mock).mockResolvedValue({
      data: { data: { reportSeq: 601, status: 'PENDING' } },
    });

    await expect(createReport(request)).resolves.toEqual({ reportSeq: 601, status: 'PENDING' });
    expect(apiClient.post).toHaveBeenCalledWith('/reports', request);
  });
});
