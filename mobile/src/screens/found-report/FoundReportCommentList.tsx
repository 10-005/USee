import { useCallback, useEffect, useRef, useReducer, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Modal,
  PermissionsAndroid,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type LayoutChangeEvent,
} from 'react-native';
import { launchCamera, launchImageLibrary, type Asset } from 'react-native-image-picker';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import CancelIcon from '../../assets/icons/common/cancel.svg';
import ChatIcon from '../../assets/icons/common/chat.svg';
import SendIcon from '../../assets/icons/common/chat-send.svg';
import DotsIcon from '../../assets/icons/common/dots-vertical.svg';
import PlusIcon from '../../assets/icons/common/plus.svg';
import ReportIcon from '../../assets/icons/common/report.svg';
import XCircleIcon from '../../assets/icons/common/x-circle.svg';
import PetEditIcon from '../../assets/icons/mypage/pet-edit.svg';
import PetTrashIcon from '../../assets/icons/mypage/pet-trash.svg';
import CameraIcon from '../../assets/icons/common/photo-camera.svg';
import LibraryIcon from '../../assets/icons/common/photo-library.svg';
import {
  ContentReportModal,
  type ContentReportTarget,
} from '../../components/report/ContentReportModal';
import { useProfileStore } from '../../stores/profileStore';
import {
  createComment,
  deleteComment,
  getCommentType,
  getCommentThreads,
  updateComment,
  type CommentDetail,
  type CommentTargetType,
} from '../../services/comments';
import { getApiErrorDisplayMessage } from '../../services/api';
import { confirmImage, uploadImageToPendingStorage } from '../../services/images';
import { colors, typography } from '../../theme';
import { validatePetImage } from '../MyPage/petRegistration';
import {
  commentReducer,
  getCommentContent,
  getCommentCount,
  type FoundReportComment,
  type FoundReportReply,
} from './comments';

type ComposerProps = {
  compact?: boolean;
  isSubmitting?: boolean;
  onCancel?: () => void;
  onDismissMenus: () => void;
  onFocus?: (inputHandle: number) => void;
  onPhotoMenuChange: (open: boolean) => void;
  onStartWriting?: () => void;
  onSubmit: (content: string, image?: Asset) => Promise<boolean>;
  photoMenuOpen: boolean;
};

function CommentComposer({
  compact = false,
  isSubmitting = false,
  onCancel,
  onDismissMenus,
  onFocus,
  onPhotoMenuChange,
  onStartWriting,
  onSubmit,
  photoMenuOpen,
}: ComposerProps) {
  const [content, setContent] = useState('');
  const [focused, setFocused] = useState(false);
  const [image, setImage] = useState<Asset | null>(null);
  const canSubmit = !isSubmitting && Boolean(content.trim() || image?.uri);

  const acceptImage = (asset?: Asset) => {
    if (!asset) {
      return;
    }
    const error = validatePetImage(asset);
    if (error) {
      Alert.alert('사진을 첨부할 수 없어요', error);
      return;
    }
    setImage(asset);
  };

  const chooseFromLibrary = async () => {
    onPhotoMenuChange(false);
    try {
      const result = await launchImageLibrary({
        includeExtra: true,
        mediaType: 'photo',
        selectionLimit: 1,
      });
      if (result.errorCode) {
        Alert.alert(
          '사진 보관함을 열 수 없어요',
          result.errorMessage ?? '잠시 후 다시 시도해주세요.',
        );
        return;
      }
      if (!result.didCancel) {
        acceptImage(result.assets?.[0]);
      }
    } catch {
      Alert.alert('사진을 첨부할 수 없어요', '앱을 새로 빌드한 뒤 다시 시도해주세요.');
    }
  };

  const takePhoto = async () => {
    onPhotoMenuChange(false);
    if (Platform.OS === 'android') {
      const permission = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.CAMERA, {
        buttonNegative: '취소',
        buttonPositive: '허용',
        message: '댓글 사진을 촬영하려면 카메라 권한이 필요합니다.',
        title: '카메라 권한',
      });
      if (permission !== PermissionsAndroid.RESULTS.GRANTED) {
        Alert.alert('카메라 권한이 필요해요', '설정에서 카메라 권한을 허용해주세요.');
        return;
      }
    }
    try {
      const result = await launchCamera({ mediaType: 'photo', saveToPhotos: false });
      if (result.errorCode) {
        Alert.alert('카메라를 열 수 없어요', result.errorMessage ?? '잠시 후 다시 시도해주세요.');
        return;
      }
      if (!result.didCancel) {
        acceptImage(result.assets?.[0]);
      }
    } catch {
      Alert.alert('사진을 첨부할 수 없어요', '앱을 새로 빌드한 뒤 다시 시도해주세요.');
    }
  };

  const submit = async () => {
    if (!canSubmit) {
      return;
    }
    const submitted = await onSubmit(content.trim(), image ?? undefined);
    if (!submitted) {
      return;
    }
    setContent('');
    setImage(null);
    setFocused(false);
  };

  return (
    <View style={[styles.composerWrap, compact && styles.compactComposerWrap]}>
      <View style={[styles.commentInput, focused && styles.commentInputFocused]}>
        <Pressable
          accessibilityLabel="댓글 사진 첨부"
          accessibilityState={{ disabled: isSubmitting }}
          disabled={isSubmitting}
          hitSlop={8}
          onPress={event => {
            event.stopPropagation();
            onStartWriting?.();
            onDismissMenus();
            onPhotoMenuChange(!photoMenuOpen);
          }}
        >
          <PlusIcon height={20} width={20} />
        </Pressable>
        <TextInput
          editable={!isSubmitting}
          multiline
          onBlur={() => setFocused(false)}
          onChangeText={setContent}
          onFocus={event => {
            setFocused(true);
            onStartWriting?.();
            onDismissMenus();
            onFocus?.(event.nativeEvent.target);
          }}
          placeholder="메시지를 입력하세요"
          placeholderTextColor={colors.gray300}
          style={styles.commentTextInput}
          value={content}
        />
        <Pressable
          accessibilityLabel="댓글 전송"
          accessibilityRole="button"
          accessibilityState={{ busy: isSubmitting, disabled: !canSubmit }}
          disabled={!canSubmit}
          onPress={submit}
        >
          {isSubmitting ? (
            <ActivityIndicator color={colors.green400} size="small" />
          ) : (
            <SendIcon color={canSubmit ? colors.green400 : '#797770'} height={20} width={20} />
          )}
        </Pressable>
      </View>
      {image?.uri ? (
        <View style={styles.imagePreviewWrap}>
          <View style={styles.imageThumbnailWrap}>
            <Image source={{ uri: image.uri }} style={styles.imagePreview} />
            <Pressable
              accessibilityLabel="첨부 사진 삭제"
              accessibilityState={{ disabled: isSubmitting }}
              disabled={isSubmitting}
              hitSlop={8}
              onPress={() => setImage(null)}
              style={styles.removeImage}
            >
              <XCircleIcon height={20} width={20} />
            </Pressable>
          </View>
          <Text style={styles.imageGuide}>사진은 1장만 첨부 가능해요.</Text>
        </View>
      ) : null}
      {photoMenuOpen ? (
        <View style={styles.photoMenu}>
          <Pressable
            onPress={event => {
              event.stopPropagation();
              chooseFromLibrary();
            }}
            style={styles.photoMenuItem}
          >
            <LibraryIcon height={22} width={22} />
            <Text style={styles.photoMenuText}>사진 보관함</Text>
          </Pressable>
          <Pressable
            onPress={event => {
              event.stopPropagation();
              takePhoto();
            }}
            style={styles.photoMenuItem}
          >
            <CameraIcon height={22} width={22} />
            <Text style={styles.photoMenuText}>사진 찍기</Text>
          </Pressable>
        </View>
      ) : null}
      {compact && onCancel ? (
        <Pressable onPress={onCancel} style={styles.replyCancel}>
          <Text style={styles.replyCancelText}>답글 취소</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

type CommentItemProps = {
  onAuthor: () => void;
  authorMenuOpen: boolean;
  onChat: () => void;
  comment: FoundReportReply;
  editing: boolean;
  highlighted: boolean;
  menuOpen: boolean;
  onDelete: () => void;
  onEdit: (content: string) => void;
  onEditFocus?: (inputHandle: number) => void;
  onImagePress: (imageUri: string) => void;
  onMenu: () => void;
  onReport: () => void;
  onReply: () => void;
  onTargetLayout?: (event: LayoutChangeEvent) => void;
  reply?: boolean;
};

function CommentItem({
  onAuthor,
  authorMenuOpen,
  onChat,
  comment,
  editing,
  highlighted,
  menuOpen,
  onDelete,
  onEdit,
  onEditFocus,
  onImagePress,
  onMenu,
  onReport,
  onReply,
  onTargetLayout,
  reply = false,
}: CommentItemProps) {
  const deleted = Boolean(comment.deleted);
  const hasContent = Boolean(comment.content.trim());
  const [draft, setDraft] = useState(comment.content);

  return (
    <View
      onLayout={onTargetLayout}
      style={[styles.comment, reply && styles.reply, deleted && styles.deletedComment]}
    >
      {deleted ? (
        <View style={styles.deletedAvatar} />
      ) : (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${comment.nickname} 프로필`}
          disabled={!comment.authorId || comment.isMine}
          onPress={event => {
            event.stopPropagation();
            onAuthor();
          }}
          accessibilityState={{ expanded: authorMenuOpen }}
        >
          <Image
            source={
              comment.profileUri
                ? { uri: comment.profileUri }
                : require('../../assets/images/mypage/profile-default.png')
            }
            style={styles.avatar}
          />
        </Pressable>
      )}
      <View style={[
        styles.commentBody,
        deleted && styles.deletedCommentBody,
        reply && !deleted && !editing && styles.replyBody,
      ]}>
        {!deleted ? (
          <View style={styles.commentMeta}>
            <Text
              accessibilityRole={comment.authorId && !comment.isMine ? 'button' : undefined}
              onPress={
                comment.authorId && !comment.isMine
                  ? event => {
                      event.stopPropagation();
                      onAuthor();
                    }
                  : undefined
              }
              numberOfLines={1}
              style={styles.nickname}
            >
              {comment.nickname}
            </Text>
            {comment.isMine && <Text style={[styles.nickname, styles.selfLabel]}>(본인)</Text>}
            <Text style={styles.createdAt}>{comment.createdAt}</Text>
          </View>
        ) : null}
        {editing ? (
          <View style={styles.editArea}>
            <View style={styles.editInputRow}>
              <TextInput
                autoFocus
                multiline
                onChangeText={setDraft}
                onFocus={event => onEditFocus?.(event.nativeEvent.target)}
                placeholder="텍스트를 입력하세요"
                placeholderTextColor={colors.gray300}
                style={styles.editInput}
                value={draft}
              />
              <Pressable
                accessibilityLabel="댓글 수정 전송"
                accessibilityRole="button"
                accessibilityState={{ disabled: !draft.trim() }}
                disabled={!draft.trim()}
                onPress={() => onEdit(draft.trim())}
              >
                <SendIcon
                  color={draft.trim() ? colors.green400 : '#797770'}
                  height={20}
                  width={20}
                />
              </Pressable>
            </View>
          </View>
        ) : deleted || hasContent ? (
          <Text style={[styles.content, deleted && styles.deletedContent]}>
            <Text style={highlighted && styles.highlightedContent}>{getCommentContent(comment)}</Text>
          </Text>
        ) : null}
        {comment.imageUri ? (
          <Pressable
            accessibilityLabel="댓글 이미지 크게 보기"
            onPress={() => onImagePress(comment.imageUri!)}
            style={[styles.commentImageWrap, !hasContent && styles.imageOnlyComment]}
          >
            <Image source={{ uri: comment.imageUri }} style={styles.commentImage} />
          </Pressable>
        ) : null}
        {!deleted && !editing && !reply ? (
          <Pressable hitSlop={6} onPress={onReply}>
            <Text style={styles.replyAction}>답글달기</Text>
          </Pressable>
        ) : null}
      </View>
      {!deleted && !editing ? (
        <Pressable
          accessibilityLabel="댓글 메뉴"
          hitSlop={10}
          onPress={event => {
            event.stopPropagation();
            onMenu();
          }}
          style={styles.commentMenu}
        >
          <DotsIcon height={12} width={12} />
          {menuOpen && comment.isMine ? (
            <View style={styles.myCommentMenu}>
              <Pressable
                onPress={event => {
                  event.stopPropagation();
                  onEdit(comment.content);
                }}
                style={styles.myCommentMenuItem}
              >
                <PetEditIcon height={20} width={20} />
                <Text style={styles.myCommentMenuText}>수정하기</Text>
              </Pressable>
              <Pressable
                onPress={event => {
                  event.stopPropagation();
                  onDelete();
                }}
                style={styles.myCommentMenuItem}
              >
                <PetTrashIcon height={20} width={20} />
                <Text style={styles.deleteMenuText}>삭제하기</Text>
              </Pressable>
            </View>
          ) : null}
          {menuOpen && !comment.isMine ? (
            <View style={styles.viewerCommentMenu}>
              <Pressable
                onPress={event => {
                  event.stopPropagation();
                  onReport();
                }}
                style={styles.myCommentMenuItem}
              >
                <ReportIcon height={20} width={20} />
                <Text style={styles.reportMenuText}>신고하기</Text>
              </Pressable>
            </View>
          ) : null}
        </Pressable>
      ) : null}
      {authorMenuOpen && !comment.isMine ? (
        <View style={[styles.viewerCommentMenu, styles.authorMenu]}>
          <Pressable
            onPress={event => {
              event.stopPropagation();
              onChat();
            }}
            style={styles.myCommentMenuItem}
          >
            <ChatIcon height={20} width={20} />
            <Text style={styles.myCommentMenuText}>채팅하기</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

type CommentThreadProps = {
  onAuthor: (comment: FoundReportReply) => void;
  onChat: (comment: FoundReportReply) => void;
  comment: FoundReportComment;
  editingId: string | null;
  menuId: string | null;
  submittingReplyId: string | null;
  onAddReply: (commentId: string, content: string, image?: Asset) => Promise<boolean>;
  onDelete: (commentId: string) => void;
  onEdit: (commentId: string, content: string) => void;
  onEditFocus?: (inputHandle: number) => void;
  onImagePress: (imageUri: string) => void;
  onMenu: (comment: FoundReportReply, isReply: boolean) => void;
  onPhotoMenuChange: (composerId: string | null) => void;
  onReport: (comment: FoundReportReply, isReply: boolean) => void;
  onReply: (commentId: string | null) => void;
  photoMenuId: string | null;
  replyTargetId: string | null;
  targetCommentId?: string;
  onTargetLayout?: (y: number) => void;
};

function CommentThread({
  onAuthor,
  onChat,
  comment,
  editingId,
  menuId,
  submittingReplyId,
  onAddReply,
  onDelete,
  onEdit,
  onEditFocus,
  onImagePress,
  onMenu,
  onPhotoMenuChange,
  onReport,
  onReply,
  replyTargetId,
  photoMenuId,
  targetCommentId,
  onTargetLayout,
}: CommentThreadProps) {
  const threadYRef = useRef<number | null>(null);
  const targetYRef = useRef<number | null>(null);

  const reportTargetLayout = () => {
    if (threadYRef.current === null || targetYRef.current === null) {
      return;
    }
    onTargetLayout?.(threadYRef.current + targetYRef.current);
  };

  const item = (reply: FoundReportReply, isReply = false) => (
    <CommentItem
      onAuthor={() => onAuthor(reply)}
      authorMenuOpen={menuId === `author:${reply.id}`}
      onChat={() => onChat(reply)}
      comment={reply}
      editing={editingId === reply.id}
      highlighted={reply.id === targetCommentId}
      key={reply.id}
      menuOpen={menuId === reply.id}
      onDelete={() => onDelete(reply.id)}
      onEdit={content => onEdit(reply.id, content)}
      onEditFocus={onEditFocus}
      onImagePress={onImagePress}
      onMenu={() => onMenu(reply, isReply)}
      onReport={() => onReport(reply, isReply)}
      onReply={() => onReply(comment.id)}
      onTargetLayout={
        reply.id === targetCommentId
          ? event => {
              targetYRef.current = event.nativeEvent.layout.y;
              reportTargetLayout();
            }
          : undefined
      }
      reply={isReply}
    />
  );

  return (
    <View
      onLayout={event => {
        threadYRef.current = event.nativeEvent.layout.y;
        reportTargetLayout();
      }}
    >
      {item(comment)}
      {replyTargetId === comment.id ? (
        <CommentComposer
          compact
          isSubmitting={submittingReplyId === comment.id}
          onCancel={() => onReply(null)}
          onDismissMenus={() => onPhotoMenuChange(null)}
          onFocus={onEditFocus}
          onPhotoMenuChange={open => onPhotoMenuChange(open ? `reply-${comment.id}` : null)}
          onStartWriting={() => onReply(comment.id)}
          onSubmit={(content, image) => onAddReply(comment.id, content, image)}
          photoMenuOpen={photoMenuId === `reply-${comment.id}`}
        />
      ) : null}
      {comment.replies.map(reply => item(reply, true))}
    </View>
  );
}

type FoundReportCommentListProps = {
  onTargetUnavailable?: () => void;
  onChat: (name: string, authorId: string) => void;
  onEditFocus?: (inputHandle: number) => void;
  onPhotoMenuVisibilityChange?: (visible: boolean) => void;
  onTargetLayout?: (y: number) => void;
  reportId: string;
  targetType: CommentTargetType;
  targetCommentId?: string;
};

function formatCommentDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const year = String(date.getFullYear()).slice(2);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${year}.${month}.${day} ${hour}:${minute}`;
}

function mapComment(detail: CommentDetail, currentUserSeq: number): FoundReportReply {
  return {
    authorId: String(detail.author.userSeq),
    commentSeq: detail.commentSeq,
    commentType: detail.commentType,
    content: detail.body ?? '',
    createdAt: formatCommentDate(detail.regDttm),
    deleted: detail.isDeleted,
    id: String(detail.commentSeq),
    imageSeq: detail.image?.imageSeq,
    imageUri: detail.image?.imageUrl,
    isMine: detail.author.userSeq === currentUserSeq,
    profileUri: detail.author.profileImage?.imageUrl,
    nickname: detail.author.displayName,
  };
}

async function uploadCommentImage(asset?: Asset) {
  if (!asset?.uri) {
    return null;
  }
  const contentType = asset.type === 'image/png' ? 'image/png' : 'image/jpeg';
  const issued = await uploadImageToPendingStorage({
    byteSize: asset.fileSize ?? 0,
    contentType,
    fileName:
      asset.fileName ?? `comment-${Date.now()}.${contentType === 'image/png' ? 'png' : 'jpg'}`,
    uri: asset.uri,
  });
  return confirmImage(issued.imageKey);
}

export function FoundReportCommentList({
  onTargetUnavailable,
  onChat,
  onEditFocus,
  onPhotoMenuVisibilityChange,
  onTargetLayout,
  reportId,
  targetType,
  targetCommentId,
}: FoundReportCommentListProps) {
  const listYRef = useRef<number | null>(null);
  const targetRelativeYRef = useRef<number | null>(null);
  const insets = useSafeAreaInsets();
  const [comments, dispatch] = useReducer(commentReducer, []);
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [photoMenuId, setPhotoMenuId] = useState<string | null>(null);
  const [replyTargetId, setReplyTargetId] = useState<string | null>(null);
  const [reportTarget, setReportTarget] = useState<ContentReportTarget | null>(null);
  const [viewerImageUri, setViewerImageUri] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [mutating, setMutating] = useState(false);
  const [submittingTarget, setSubmittingTarget] = useState<string | null>(null);
  const currentUserSeq = useProfileStore(state => state.profile.userSeq);
  const loadProfile = useProfileStore(state => state.loadProfile);

  useEffect(() => {
    onPhotoMenuVisibilityChange?.(photoMenuId !== null);
    return () => onPhotoMenuVisibilityChange?.(false);
  }, [onPhotoMenuVisibilityChange, photoMenuId]);

  const targetSeq = Number(reportId);
  const refreshComments = useCallback(async () => {
    if (!Number.isFinite(targetSeq)) {
      throw new Error('댓글 대상 번호가 올바르지 않습니다.');
    }
    const threads = await getCommentThreads(targetType, targetSeq);
    dispatch({
      comments: threads.map(thread => ({
        ...mapComment(thread, currentUserSeq),
        replies: thread.replies.map(reply => mapComment(reply, currentUserSeq)),
      })),
      type: 'replace',
    });
  }, [currentUserSeq, targetSeq, targetType]);

  useEffect(() => {
    loadProfile().catch(() => undefined);
  }, [loadProfile]);

  useEffect(() => {
    setLoading(true);
    setLoadError('');
    refreshComments()
      .catch(error => setLoadError(getApiErrorDisplayMessage(error, '다시 시도해 주세요.')))
      .finally(() => setLoading(false));
  }, [refreshComments]);

  useEffect(() => {
    if (
      !loading &&
      targetCommentId &&
      (loadError ||
        !comments.some(
          comment =>
            comment.id === targetCommentId ||
            comment.replies.some(reply => reply.id === targetCommentId),
        ))
    ) {
      onTargetUnavailable?.();
    }
  }, [comments, loadError, loading, onTargetUnavailable, targetCommentId]);

  const reportTargetPosition = () => {
    if (listYRef.current === null || targetRelativeYRef.current === null) {
      return;
    }
    onTargetLayout?.(listYRef.current + targetRelativeYRef.current);
  };

  const addComment = async (content: string, image?: Asset) => {
    if (mutating) {
      return false;
    }
    try {
      setMutating(true);
      setSubmittingTarget('root');
      const uploaded = await uploadCommentImage(image);
      await createComment({
        body: content || null,
        imageSeq: uploaded?.imageSeq ?? null,
        targetSeq,
        targetType,
      });
      await refreshComments();
      return true;
    } catch (error) {
      Alert.alert(
        '댓글을 등록하지 못했어요',
        getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'),
      );
      return false;
    } finally {
      setMutating(false);
      setSubmittingTarget(null);
    }
  };

  const addReply = async (commentId: string, content: string, image?: Asset) => {
    if (mutating) {
      return false;
    }
    try {
      setMutating(true);
      setSubmittingTarget(commentId);
      const uploaded = await uploadCommentImage(image);
      await createComment({
        body: content || null,
        imageSeq: uploaded?.imageSeq ?? null,
        parentCommentSeq: Number(commentId),
        targetSeq,
        targetType,
      });
      setReplyTargetId(null);
      await refreshComments();
      return true;
    } catch (error) {
      Alert.alert(
        '답글을 등록하지 못했어요',
        getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'),
      );
      return false;
    } finally {
      setMutating(false);
      setSubmittingTarget(null);
    }
  };

  const findComment = (commentId: string) => {
    for (const root of comments) {
      if (root.id === commentId) {
        return root;
      }
      const reply = root.replies.find(item => item.id === commentId);
      if (reply) {
        return reply;
      }
    }
    return null;
  };

  const editComment = async (commentId: string, content: string) => {
    const comment = findComment(commentId);
    if (!comment?.commentSeq || !comment.commentType || mutating) {
      return;
    }
    try {
      setMutating(true);
      await updateComment(comment.commentType, comment.commentSeq, { body: content.trim() });
      setEditingId(null);
      await refreshComments();
    } catch (error) {
      Alert.alert(
        '댓글을 수정하지 못했어요',
        getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'),
      );
    } finally {
      setMutating(false);
    }
  };

  const removeComment = async (commentId: string) => {
    const comment = findComment(commentId);
    if (!comment?.commentSeq || !comment.commentType || mutating) {
      return;
    }
    try {
      setMutating(true);
      await deleteComment(comment.commentType, comment.commentSeq);
      setDeleteTargetId(null);
      setMenuId(null);
      await refreshComments();
    } catch (error) {
      Alert.alert(
        '댓글을 삭제하지 못했어요',
        getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'),
      );
    } finally {
      setMutating(false);
    }
  };

  const openMenu = (comment: FoundReportReply, _isReply: boolean) => {
    setPhotoMenuId(null);
    setMenuId(current => (current === comment.id ? null : comment.id));
  };

  const openReport = (comment: FoundReportReply, isReply: boolean) => {
    setMenuId(null);
    setReportTarget({
      excerpt: getCommentContent(comment),
      id: `${reportId}:${isReply ? 'reply' : 'comment'}:${comment.id}`,
      nickname: comment.nickname,
      targetSeq: comment.commentSeq ?? Number(comment.id),
      targetType: comment.commentType ?? getCommentType(targetType),
      type: isReply ? 'reply' : 'comment',
    });
  };

  const changePhotoMenu = (composerId: string | null) => {
    setMenuId(null);
    setPhotoMenuId(composerId);
  };

  const dismissMenus = () => {
    setMenuId(null);
    setPhotoMenuId(null);
  };

  return (
    <Pressable
      accessible={false}
      onLayout={event => {
        listYRef.current = event.nativeEvent.layout.y;
        reportTargetPosition();
      }}
      onPress={dismissMenus}
      style={styles.container}
    >
      <Text style={styles.title}>{getCommentCount(comments)}개의 댓글</Text>
      {loading ? <Text style={styles.commentState}>댓글을 불러오는 중이에요</Text> : null}
      {!loading && loadError ? (
        <Pressable
          accessibilityRole="button"
          onPress={() => {
            setLoading(true);
            setLoadError('');
            refreshComments()
              .catch(error => setLoadError(getApiErrorDisplayMessage(error, '다시 시도해 주세요.')))
              .finally(() => setLoading(false));
          }}
        >
          <Text style={styles.commentState}>
            {loadError}
            {'\n'}다시 시도
          </Text>
        </Pressable>
      ) : null}
      <CommentComposer
        isSubmitting={submittingTarget === 'root'}
        onDismissMenus={dismissMenus}
        onFocus={onEditFocus}
        onPhotoMenuChange={open => changePhotoMenu(open ? 'root' : null)}
        onStartWriting={() => {
          setEditingId(null);
          setReplyTargetId(null);
        }}
        onSubmit={addComment}
        photoMenuOpen={photoMenuId === 'root'}
      />
      {comments.map(comment => (
        <CommentThread
          onAuthor={author => {
            setPhotoMenuId(null);
            const id = `author:${author.id}`;
            setMenuId(current => (current === id ? null : id));
          }}
          onChat={author => {
            dismissMenus();
            if (author.authorId) {
              onChat(author.nickname, author.authorId);
            }
          }}
          comment={comment}
          editingId={editingId}
          key={comment.id}
          menuId={menuId}
          submittingReplyId={submittingTarget}
          onAddReply={addReply}
          onDelete={setDeleteTargetId}
          onEdit={(commentId, content) => {
            if (editingId === commentId) {
              editComment(commentId, content);
            } else {
              setEditingId(commentId);
              setMenuId(null);
              setPhotoMenuId(null);
              setReplyTargetId(null);
            }
          }}
          onEditFocus={onEditFocus}
          onImagePress={setViewerImageUri}
          onMenu={openMenu}
          onPhotoMenuChange={changePhotoMenu}
          onReport={openReport}
          onReply={commentId => {
            setEditingId(null);
            setPhotoMenuId(null);
            setReplyTargetId(commentId);
            setMenuId(null);
          }}
          photoMenuId={photoMenuId}
          replyTargetId={replyTargetId}
          targetCommentId={targetCommentId}
          onTargetLayout={y => {
            targetRelativeYRef.current = y;
            reportTargetPosition();
          }}
        />
      ))}
      <ContentReportModal onClose={() => setReportTarget(null)} target={reportTarget} />
      <Modal
        animationType="fade"
        onRequestClose={() => setDeleteTargetId(null)}
        transparent
        visible={Boolean(deleteTargetId)}
      >
        <View style={styles.modalBackdrop}>
          <View style={styles.deleteModal}>
            <Text style={styles.modalTitle}>댓글을 삭제하시겠습니까?</Text>
            <Text style={styles.modalDescription}>삭제 후에는 해당 정보를 복구할 수 없습니다.</Text>
            <View style={styles.modalActions}>
              <Pressable
                onPress={() => setDeleteTargetId(null)}
                style={[styles.modalButton, styles.cancelButton]}
              >
                <Text style={styles.modalButtonText}>취소하기</Text>
              </Pressable>
              <Pressable
                onPress={() => {
                  if (deleteTargetId) {
                    removeComment(deleteTargetId);
                  }
                }}
                style={[styles.modalButton, styles.deleteButton]}
              >
                <Text style={styles.modalButtonText}>삭제하기</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
      <Modal
        animationType="fade"
        onRequestClose={() => setViewerImageUri(null)}
        statusBarTranslucent
        transparent
        visible={Boolean(viewerImageUri)}
      >
        <Pressable
          accessibilityLabel="확대 이미지 닫기"
          onPress={() => setViewerImageUri(null)}
          style={styles.imageViewerBackdrop}
        >
          <Pressable onPress={event => event.stopPropagation()} style={styles.imageViewerContent}>
            {viewerImageUri ? (
              <Image
                resizeMode="contain"
                source={{ uri: viewerImageUri }}
                style={styles.viewerImage}
              />
            ) : null}
          </Pressable>
          <Pressable
            accessibilityLabel="확대 이미지 닫기"
            hitSlop={12}
            onPress={() => setViewerImageUri(null)}
            style={[styles.imageViewerClose, { top: insets.top + 16 }]}
          >
            <CancelIcon height={20} width={20} />
          </Pressable>
        </Pressable>
      </Modal>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { paddingHorizontal: 22, paddingTop: 20 },
  title: { ...typography.bodyRegular14, color: colors.black },
  commentState: { ...typography.bodyRegular14, color: colors.gray500, marginVertical: 12 },
  composerWrap: { marginBottom: 18, marginTop: 10, position: 'relative', zIndex: 5 },
  compactComposerWrap: { marginLeft: 44, marginTop: 0 },
  commentInput: {
    alignItems: 'center',
    backgroundColor: '#F1F1F1',
    borderColor: colors.transparent,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    minHeight: 48,
    paddingHorizontal: 16,
  },
  commentInputFocused: { borderColor: colors.green400 },
  commentTextInput: {
    ...typography.bodyRegular16,
    color: colors.gray800,
    flex: 1,
    maxHeight: 88,
    minHeight: 40,
    paddingHorizontal: 10,
    paddingVertical: 9,
  },
  imagePreviewWrap: { alignSelf: 'flex-start', marginTop: 8 },
  imageThumbnailWrap: { height: 76, position: 'relative', width: 76 },
  imagePreview: { borderRadius: 8, height: 76, width: 76 },
  removeImage: { position: 'absolute', right: -7, top: -7 },
  imageGuide: { ...typography.captionRegular10, color: colors.gray400, marginTop: 5 },
  photoMenu: {
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: 10,
    borderWidth: 1,
    left: 0,
    paddingVertical: 6,
    position: 'absolute',
    top: 52,
    width: 154,
    zIndex: 20,
  },
  photoMenuItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 12,
    height: 44,
    paddingHorizontal: 14,
  },
  photoMenuText: { ...typography.bodyRegular14, color: colors.gray700 },
  replyCancel: { alignSelf: 'flex-end', paddingHorizontal: 4, paddingTop: 6 },
  replyCancelText: { ...typography.captionRegular10, color: colors.gray400 },
  comment: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    marginBottom: 8,
    minHeight: 62,
    position: 'relative',
  },
  deletedComment: { minHeight: 0, paddingVertical: 10 },
  reply: { marginLeft: 40, minHeight: 0 },
  avatar: { borderRadius: 14, height: 28, width: 28 },
  deletedAvatar: { height: 0, width: 0 },
  commentBody: { flex: 1, marginLeft: 7, minWidth: 0 },
  replyBody: { paddingBottom: (typography.captionRegular10.lineHeight + 5) / 2 },
  deletedCommentBody: { marginLeft: 0 },
  commentMeta: { alignItems: 'flex-start', flexDirection: 'row', minHeight: 15 },
  nickname: {
    color: '#636363',
    fontFamily: 'Pretendard-Medium',
    fontSize: 11,
    includeFontPadding: false,
    lineHeight: 15,
    maxWidth: 150,
  },
  selfLabel: { marginLeft: 4 },
  createdAt: {
    ...typography.captionRegular10,
    color: '#C1C1C1',
    includeFontPadding: false,
    marginLeft: 6,
    marginTop: 1,
  },
  content: { ...typography.bodyRegular14, color: colors.black, marginTop: 4 },
  highlightedContent: { backgroundColor: `${colors.green400}1A`, color: colors.green400 },
  deletedContent: { color: colors.gray400, marginTop: 0 },
  commentImageWrap: { borderRadius: 8, height: 120, marginTop: 8, overflow: 'hidden', width: 120 },
  commentImage: { height: '100%', width: '100%' },
  imageOnlyComment: { marginTop: 4 },
  replyAction: {
    ...typography.captionRegular10,
    color: '#C1C1C1',
    fontFamily: 'Pretendard-SemiBold',
    marginTop: 5,
  },
  commentMenu: { alignItems: 'center', height: 28, justifyContent: 'center', width: 20 },
  myCommentMenu: {
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: 10,
    borderWidth: 1,
    paddingVertical: 6,
    position: 'absolute',
    right: 0,
    top: 24,
    width: 142,
    zIndex: 10,
  },
  viewerCommentMenu: {
    backgroundColor: colors.white,
    borderColor: colors.gray200,
    borderRadius: 10,
    borderWidth: 1,
    paddingVertical: 6,
    position: 'absolute',
    right: 0,
    top: 24,
    width: 142,
    zIndex: 10,
  },
  authorMenu: {
    elevation: 4,
    left: 0,
    right: undefined,
    top: 38,
  },
  myCommentMenuItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 12,
    height: 42,
    paddingHorizontal: 14,
  },
  myCommentMenuText: { ...typography.bodyRegular14, color: colors.gray700 },
  reportMenuText: { ...typography.bodyRegular14, color: '#FA705B' },
  deleteMenuText: { ...typography.bodyRegular14, color: '#FA705B' },
  editArea: { marginTop: 5 },
  editInputRow: {
    alignItems: 'center',
    borderColor: colors.green400,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    minHeight: 44,
    paddingHorizontal: 10,
  },
  editInput: {
    ...typography.bodyRegular14,
    color: colors.black,
    flex: 1,
    maxHeight: 88,
    minHeight: 40,
    paddingRight: 8,
    paddingVertical: 8,
  },
  modalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.32)',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  deleteModal: { backgroundColor: colors.white, borderRadius: 18, padding: 18, width: '100%' },
  modalTitle: { ...typography.titleSemiBold18, color: colors.gray800 },
  modalDescription: { ...typography.bodyRegular14, color: colors.gray500, marginTop: 14 },
  modalActions: { flexDirection: 'row', gap: 10, marginTop: 18 },
  modalButton: {
    alignItems: 'center',
    borderRadius: 8,
    flex: 1,
    height: 42,
    justifyContent: 'center',
  },
  cancelButton: { backgroundColor: colors.gray200 },
  deleteButton: { backgroundColor: '#FA705B' },
  modalButtonText: { ...typography.bodySemiBold16, color: colors.white },
  imageViewerBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.88)',
    flex: 1,
    justifyContent: 'center',
  },
  imageViewerContent: { height: '82%', width: '100%' },
  viewerImage: { height: '100%', width: '100%' },
  imageViewerClose: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: 20,
    height: 40,
    justifyContent: 'center',
    position: 'absolute',
    right: 20,
    width: 40,
  },
});
