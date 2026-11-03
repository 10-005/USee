import { useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View } from 'react-native';

import ReplyIcon from '../../assets/icons/common/arrow-curve-left-right.svg';
import ChatIcon from '../../assets/icons/common/chat.svg';
import DotsIcon from '../../assets/icons/common/dots-vertical.svg';
import PlusIcon from '../../assets/icons/common/plus.svg';
import { colors, typography } from '../../theme';
import {
  foundReportComments,
  getCommentContent,
  getCommentCount,
  type FoundReportComment,
  type FoundReportReply,
} from './comments';

const INITIAL_COMMENT_COUNT = 3;

function CommentItem({ comment, reply = false }: { comment: FoundReportReply; reply?: boolean }) {
  const deleted = Boolean(comment.deleted);

  return (
    <View style={[styles.comment, reply && styles.reply]}>
      {deleted ? (
        <View style={styles.deletedAvatar} />
      ) : (
        <Image
          source={require('../../assets/images/found-report/reporter-profile.png')}
          style={styles.avatar}
        />
      )}
      <View style={styles.commentBody}>
        {!deleted ? (
          <View style={styles.commentMeta}>
            <Text numberOfLines={1} style={styles.nickname}>
              {comment.nickname}
            </Text>
            <Text style={styles.createdAt}>{comment.createdAt}</Text>
          </View>
        ) : null}
        <Text style={[styles.content, deleted && styles.deletedContent]}>
          {getCommentContent(comment)}
        </Text>
        {!deleted ? <Text style={styles.replyAction}>답글달기</Text> : null}
      </View>
      {!deleted ? (
        <Pressable accessibilityLabel="댓글 메뉴" hitSlop={10} style={styles.commentMenu}>
          <DotsIcon height={12} width={12} />
        </Pressable>
      ) : null}
    </View>
  );
}

function CommentThread({ comment }: { comment: FoundReportComment }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <View>
      <CommentItem comment={comment} />
      {expanded ? comment.replies.map(reply => <CommentItem comment={reply} key={reply.id} reply />) : null}
      {comment.replies.length ? (
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ expanded }}
          onPress={() => setExpanded(current => !current)}
          style={styles.replyToggle}
        >
          <ReplyIcon height={16} width={16} />
          <Text style={styles.replyToggleText}>
            {expanded ? '답글 숨기기' : `답글 ${comment.replies.length}개 더 보기`}
          </Text>
        </Pressable>
      ) : null}
    </View>
  );
}

export function FoundReportCommentList() {
  const [showAll, setShowAll] = useState(false);
  const comments = showAll
    ? foundReportComments
    : foundReportComments.slice(0, INITIAL_COMMENT_COUNT);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>{getCommentCount(foundReportComments)}개의 댓글</Text>
      <View style={styles.commentInput}>
        <View style={styles.commentInputStart}>
          <PlusIcon height={20} width={20} />
          <Text style={styles.commentPlaceholder}>메시지를 입력하세요</Text>
        </View>
        <ChatIcon height={20} width={20} />
      </View>
      {comments.map(comment => (
        <CommentThread comment={comment} key={comment.id} />
      ))}
      {foundReportComments.length > INITIAL_COMMENT_COUNT ? (
        <Pressable
          accessibilityRole="button"
          onPress={() => setShowAll(current => !current)}
          style={styles.moreButton}
        >
          <Text style={styles.moreButtonText}>{showAll ? '댓글 접기' : '댓글 더보기'}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingHorizontal: 22, paddingTop: 20 },
  title: { ...typography.bodyRegular14, color: colors.black },
  commentInput: {
    alignItems: 'center',
    backgroundColor: '#F1F1F1',
    borderRadius: 8,
    flexDirection: 'row',
    height: 48,
    justifyContent: 'space-between',
    marginBottom: 18,
    marginTop: 10,
    paddingHorizontal: 16,
  },
  commentPlaceholder: { ...typography.bodyRegular16, color: colors.gray300 },
  commentInputStart: { alignItems: 'center', flexDirection: 'row', gap: 10 },
  comment: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    marginBottom: 16,
    minHeight: 62,
  },
  reply: { marginLeft: 40 },
  avatar: { borderRadius: 20, height: 40, width: 40 },
  deletedAvatar: { height: 40, width: 40 },
  commentBody: { flex: 1, marginLeft: 7, minWidth: 0 },
  commentMeta: { alignItems: 'center', flexDirection: 'row' },
  nickname: {
    color: '#636363',
    fontFamily: 'Pretendard-Medium',
    fontSize: 12,
    lineHeight: 15,
    maxWidth: 150,
  },
  createdAt: { ...typography.captionRegular10, color: '#C1C1C1', marginLeft: 6 },
  content: { ...typography.bodyRegular14, color: colors.black, marginTop: 5 },
  deletedContent: { color: colors.gray400, marginTop: 11 },
  replyAction: {
    ...typography.captionRegular10,
    color: '#C1C1C1',
    fontFamily: 'Pretendard-SemiBold',
    marginTop: 5,
  },
  commentMenu: { alignItems: 'center', height: 28, justifyContent: 'center', width: 20 },
  replyToggle: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    flexDirection: 'row',
    gap: 6,
    marginBottom: 18,
    marginLeft: 47,
    minHeight: 24,
  },
  replyToggleText: {
    color: colors.gray500,
    fontFamily: 'Pretendard-Medium',
    fontSize: 12,
    lineHeight: 15,
  },
  moreButton: { alignItems: 'center', paddingVertical: 12 },
  moreButtonText: { ...typography.captionRegular10, color: colors.gray500 },
});
