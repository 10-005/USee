import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { createPersistStorage } from '../services/storage/persistStorage';

export type NotificationCategory = 'report' | 'reply' | 'adoption' | 'aiMatching';

export type AppNotification = {
  body: string;
  category: NotificationCategory;
  createdAtLabel: string;
  id: string;
  isRead: boolean;
  targetCommentId?: string;
  targetId?: string;
  title: string;
};

export const initialNotifications: AppNotification[] = [
  {
    body: '서울시 강남구에서 새로운 목격 제보가 등록되었어요.',
    category: 'report',
    createdAtLabel: '1시간 전',
    id: 'notification-1',
    isRead: false,
    targetId: 'found-1',
    title: '내 아이 관련 제보가 올라왔어요!',
  },
  {
    body: '저 얼마 전에 서울에서 본 것 같아요. 여기 더 길어지면 이렇게 표시돼요.',
    category: 'reply',
    createdAtLabel: '2시간 전',
    id: 'notification-2',
    isRead: true,
    targetCommentId: 'reply-3-1',
    targetId: 'found-1',
    title: '댓글에 답글이 달렸어요!',
  },
  {
    body: '초코의 입양 신청 상태를 확인해 주세요.',
    category: 'adoption',
    createdAtLabel: '2026.10.30',
    id: 'notification-3',
    isRead: false,
    targetId: 'animal-1',
    title: '입양 신청 상태가 변경되었어요!',
  },
  {
    body: '부산시 해운대구에서 새로운 목격 제보가 등록되었어요.',
    category: 'aiMatching',
    createdAtLabel: '2026.10.30',
    id: 'notification-4',
    isRead: true,
    targetId: 'found-2',
    title: '내 아이 관련 제보가 올라왔어요!',
  },
  {
    body: '등록한 동물과 유사한 보호동물이 새로 등록되었어요.',
    category: 'aiMatching',
    createdAtLabel: '2026.10.30',
    id: 'notification-5',
    isRead: false,
    targetId: 'animal-2',
    title: '내 아이 관련 제보가 올라왔어요!',
  },
  {
    body: '저 얼마 전에 공원에서 본 것 같아요.',
    category: 'reply',
    createdAtLabel: '2026.10.30',
    id: 'notification-6',
    isRead: true,
    targetCommentId: 'reply-3-2',
    targetId: 'found-3',
    title: '댓글에 답글이 달렸어요!',
  },
  {
    body: '근처 산책로에서 비슷한 아이를 본 것 같아요.',
    category: 'reply',
    createdAtLabel: '2026.10.30',
    id: 'notification-7',
    isRead: false,
    targetCommentId: 'comment-2',
    targetId: 'found-1',
    title: '내 게시물에 댓글이 달렸어요!',
  },
];

type NotificationState = {
  deleteAll: () => void;
  deleteNotification: (id: string) => void;
  markAsRead: (id: string) => void;
  notifications: AppNotification[];
  reset: () => void;
};

export const useNotificationStore = create<NotificationState>()(
  persist(
    set => ({
      deleteAll: () => set({ notifications: [] }),
      deleteNotification: id =>
        set(state => ({
          notifications: state.notifications.filter(notification => notification.id !== id),
        })),
      markAsRead: id =>
        set(state => ({
          notifications: state.notifications.map(notification =>
            notification.id === id ? { ...notification, isRead: true } : notification,
          ),
        })),
      notifications: initialNotifications,
      reset: () => set({ notifications: initialNotifications }),
    }),
    {
      migrate: persistedState => {
        const state = persistedState as NotificationState;
        const commentTargets: Record<string, string> = {
          'notification-2': 'reply-3-1',
          'notification-6': 'reply-3-2',
        };
        const persistedNotifications = state.notifications.map(notification => ({
          ...notification,
          targetCommentId: commentTargets[notification.id] ?? notification.targetCommentId,
        }));
        const newCommentNotification = initialNotifications.find(
          notification => notification.id === 'notification-7',
        )!;
        return {
          ...state,
          notifications: persistedNotifications.some(
            notification => notification.id === newCommentNotification.id,
          )
            ? persistedNotifications
            : [...persistedNotifications, newCommentNotification],
        };
      },
      name: 'usee-notifications',
      storage: createPersistStorage<NotificationState>(),
      version: 1,
    },
  ),
);
