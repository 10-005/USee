export type FoundReportReply = {
  content: string;
  createdAt: string;
  deleted?: boolean;
  id: string;
  nickname: string;
};

export type FoundReportComment = FoundReportReply & {
  replies: FoundReportReply[];
};

export const foundReportComments: FoundReportComment[] = [
  {
    content: '해리 눈여겨봐주세요.',
    createdAt: '26.10.18 23:27',
    id: 'comment-1',
    nickname: '보리보리',
    replies: [],
  },
  {
    content: '근처 산책로에서 비슷한 아이를 본 것 같아요. 다시 확인해볼게요.',
    createdAt: '26.10.18 23:31',
    id: 'comment-2',
    nickname: '강아지사랑',
    replies: [],
  },
  {
    content:
      '오늘 저녁에도 같은 장소를 지나갈 예정이에요. 주변 골목과 공원까지 꼼꼼히 살펴보고 제보드리겠습니다.',
    createdAt: '26.10.18 23:35',
    id: 'comment-3',
    nickname: '산책친구',
    replies: [
      {
        content: '도와주셔서 정말 감사합니다.',
        createdAt: '26.10.18 23:40',
        id: 'reply-3-1',
        nickname: '시샤',
      },
      {
        content: '확인되는 내용이 있으면 바로 알려드릴게요.',
        createdAt: '26.10.18 23:42',
        id: 'reply-3-2',
        nickname: '산책친구',
      },
    ],
  },
  {
    content: '',
    createdAt: '26.10.18 23:48',
    deleted: true,
    id: 'comment-4',
    nickname: '알 수 없음',
    replies: [],
  },
  {
    content: '사진 속 목줄 색상도 함께 확인해볼게요.',
    createdAt: '26.10.18 23:55',
    id: 'comment-5',
    nickname: '구미주민',
    replies: [],
  },
];

export function getCommentCount(comments: FoundReportComment[]) {
  return comments.length;
}

export function getCommentContent(comment: FoundReportReply) {
  return comment.deleted ? '삭제된 댓글입니다.' : comment.content;
}
