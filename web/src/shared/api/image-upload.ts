import { shelterRequest } from './session';

type SignedImage = { uploadUrl: string; imageKey: string; expiresInSeconds: number };
export function createImageUploader() {
  const pending = new WeakMap<File, Promise<{ imageKey: string; expires: number }>>();
  const confirmed = new WeakMap<File, number>();
  function prepare(file: File) {
    let task = pending.get(file);
    if (task) return task;
    task = (async () => {
      const signed = await shelterRequest<SignedImage>('/images/upload-urls', {
        method: 'POST',
        json: { fileName: file.name, byteSize: file.size, contentType: file.type },
      });
      if (!signed?.uploadUrl || !signed.imageKey)
        throw new Error('사진 업로드 주소를 받지 못했습니다.');
      const expires = Date.now() + signed.expiresInSeconds * 1000;
      // The browser sets the signed Content-Length from the File body.
      const response = await fetch(signed.uploadUrl, {
        method: 'PUT',
        headers: {
          'Content-Type': file.type,
          'x-amz-tagging': 'upload=pending',
          'If-None-Match': '*',
        },
        body: file,
        signal: AbortSignal.timeout(60000),
      });
      if (!response.ok) throw new Error('사진을 업로드하지 못했습니다.');
      return { imageKey: signed.imageKey, expires };
    })().catch((error: unknown) => {
      pending.delete(file);
      throw error;
    });
    pending.set(file, task);
    return task;
  }
  async function confirm(file: File) {
    const saved = confirmed.get(file);
    if (saved) return saved;
    let upload = await prepare(file);
    if (upload.expires <= Date.now()) {
      pending.delete(file);
      upload = await prepare(file);
    }
    try {
      const data = await shelterRequest<{ imageSeq: number }>('/images', {
        method: 'POST',
        json: { imageKey: upload.imageKey },
      });
      if (!data?.imageSeq) throw new Error('사진을 확정하지 못했습니다.');
      confirmed.set(file, data.imageSeq);
      return data.imageSeq;
    } finally {
      pending.delete(file);
    }
  }
  return { prepare, confirm };
}
