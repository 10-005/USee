import { beforeEach, describe, expect, it } from 'vitest';
import { shelterMockRepository } from './repository';
import { useShelterDemo } from './store';
import { chatMockRepository } from '@/mocks/chat/repository';
import { useDemoChat } from '@/mocks/chat/store';

beforeEach(() => {
  useShelterDemo.setState(useShelterDemo.getInitialState(), true);
  useDemoChat.setState(useDemoChat.getInitialState(), true);
});

describe('메모리 저장소 계약', () => {
  it('조회·저장과 없는 데이터 조회를 지원한다', async () => {
    const original = (await shelterMockRepository.listAnimals())[0]!;
    const changed = { ...original, name: '변경된 품종' };
    await expect(shelterMockRepository.saveAnimal(changed)).resolves.toEqual(changed);
    await expect(shelterMockRepository.getAnimal(changed.id)).resolves.toEqual(changed);
    await expect(shelterMockRepository.getAnimal('missing')).resolves.toBeNull();
  });
  it('이미 취소된 조회는 데이터를 반환하지 않는다', async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(
      shelterMockRepository.listReports({ signal: controller.signal }),
    ).rejects.toMatchObject({ name: 'AbortError' });
  });
  it('채팅 메시지 전송과 나가기는 기존 저장소에 반영된다', async () => {
    const conversation = (await chatMockRepository.listConversations())[0]!;
    const message = {
      id: 'test-message',
      type: 'message' as const,
      sender: 'me' as const,
      text: '안녕하세요',
      time: '12:00',
    };
    await chatMockRepository.sendMessage(conversation.id, message);
    expect(await chatMockRepository.listMessages(conversation.id)).toContainEqual(message);
    await chatMockRepository.leaveConversation(conversation.id);
    expect(await chatMockRepository.listMessages(conversation.id)).toEqual([]);
  });
});
