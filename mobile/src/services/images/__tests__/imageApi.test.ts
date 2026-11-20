jest.mock('../../api/client', () => ({
  apiClient: { post: jest.fn() },
}));

import { apiClient } from '../../api/client';
import { confirmImage, uploadImageToPendingStorage } from '../imageApi';

const mockPost = apiClient.post as jest.Mock;
const mockFetch = jest.fn();

describe('imageApi', () => {
  beforeEach(() => {
    mockPost.mockReset();
    mockFetch.mockReset();
    globalThis.fetch = mockFetch as typeof fetch;
  });

  it('발급받은 URL로 S3 PUT 헤더를 전송한다', async () => {
    const issued = {
      expiresInSeconds: 600,
      imageKey: 'images/2026/11/profile.jpg',
      uploadUrl: 'https://s3.example.test/upload',
    };
    mockPost.mockResolvedValue({ data: { data: issued } });
    mockFetch
      .mockResolvedValueOnce({ blob: jest.fn().mockResolvedValue('image-blob') })
      .mockResolvedValueOnce({ ok: true });

    await expect(
      uploadImageToPendingStorage({
        byteSize: 1234,
        contentType: 'image/jpeg',
        fileName: 'profile.jpg',
        uri: 'file:///profile.jpg',
      }),
    ).resolves.toEqual(issued);

    expect(mockPost).toHaveBeenCalledWith('/images/upload-urls', {
      byteSize: 1234,
      contentType: 'image/jpeg',
      fileName: 'profile.jpg',
    });
    expect(mockFetch).toHaveBeenLastCalledWith(issued.uploadUrl, {
      body: 'image-blob',
      headers: {
        'Content-Length': '1234',
        'Content-Type': 'image/jpeg',
        'If-None-Match': '*',
        'x-amz-tagging': 'upload=pending',
      },
      method: 'PUT',
    });
  });

  it('업로드된 imageKey를 확정한다', async () => {
    const data = {
      byteSize: 1234,
      imageSeq: 1001,
      imageUrl: 'https://cdn.example.test/profile.jpg',
      mimeType: 'image/jpeg',
    };
    mockPost.mockResolvedValue({ data: { data } });

    await expect(confirmImage('images/2026/11/profile.jpg')).resolves.toEqual(data);
    expect(mockPost).toHaveBeenCalledWith('/images', {
      imageKey: 'images/2026/11/profile.jpg',
    });
  });
});
