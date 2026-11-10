import { Client } from '@stomp/stompjs';
import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { chatAccessToken, refreshChatToken } from '@/shared/api/session';
import { env } from '@/shared/config/env';

export function useChatSocket() {
  const cache = useQueryClient();
  const [connection, setConnection] = useState<{ client: Client }>();
  useEffect(() => {
    let active = true;
    let renewal: Promise<void> | undefined;
    const address = new URL(env.apiBaseUrl + '/chat/v1/ws', window.location.origin);
    address.protocol = address.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new Client({
      brokerURL: import.meta.env.VITE_CHAT_WS_URL || address.href,
      reconnectDelay: 3000,
      connectionTimeout: 10000,
      beforeConnect: async () => {
        await renewal;
        const token = chatAccessToken();
        if (!active || !token) {
          void client.deactivate();
          return;
        }
        client.connectHeaders = { Authorization: 'Bearer ' + token };
      },
      onConnect: () => {
        if (!active) return;
        const refreshLists = () => {
          void cache.invalidateQueries({ queryKey: ['chat', 'rooms'] });
          void cache.invalidateQueries({ queryKey: ['chat', 'unread'] });
        };
        client.subscribe('/user/queue/room-updates', refreshLists);
        refreshLists();
        setConnection({ client });
      },
      onStompError: (frame) => {
        if (frame.headers.message === 'AUTH_TOKEN_EXPIRED') {
          renewal = refreshChatToken(client.connectHeaders.Authorization?.slice(7) ?? '').catch(
            () => {},
          );
        }
      },
      onWebSocketClose: () => {
        if (active) setConnection(undefined);
      },
    });
    client.activate();
    return () => {
      active = false;
      void client.deactivate();
    };
  }, [cache]);
  return connection;
}
