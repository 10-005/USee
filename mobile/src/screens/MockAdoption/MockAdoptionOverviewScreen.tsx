import { useCallback, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { Alert, Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppHeader, Button, ListScreenSkeleton } from '../../components/common';
import {
  getActiveMockAdoption,
  type MockAdoptionSession,
} from '../../services/storage/mockAdoptionStorage';
import { colors, radius, typography } from '../../theme';
import { mockAdoptionPets } from './data';
import { mockAdoptionReports } from './reportData';

type Props = {
  onBack: () => void;
  onStart: () => void;
  onResume: (petId: string) => void;
};

export function MockAdoptionOverviewScreen({ onBack, onStart, onResume }: Props) {
  const insets = useSafeAreaInsets();
  const [session, setSession] = useState<MockAdoptionSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [initialLoading, setInitialLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useFocusEffect(
    useCallback(() => {
      let mounted = true;
      getActiveMockAdoption()
        .then(value => {
          if (mounted) {
            setSession(value);
          }
        })
        .catch(() => {
          if (mounted) {
            setFailed(true);
          }
        })
        .finally(() => {
          if (mounted) {
            setLoading(false);
            setInitialLoading(false);
          }
        });
      return () => {
        mounted = false;
      };
    }, []),
  );

  const openAdoption = async () => {
    setLoading(true);
    try {
      const active = await getActiveMockAdoption();
      setSession(active);
      setFailed(false);
      if (active) {
        onResume(active.petId);
      } else {
        onStart();
      }
    } catch {
      setFailed(true);
      Alert.alert('모의입양', '진행 상태를 불러오지 못했어요. 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  };
  const petName = session?.petName ?? mockAdoptionPets.find(pet => pet.id === session?.petId)?.name;

  return (
    <View style={styles.container}>
      <AppHeader title="모의입양" onBackPress={onBack} />
      {initialLoading ? (
        <ListScreenSkeleton accessibilityLabel="모의입양 정보 불러오는 중" />
      ) : (
        <ScrollView
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 120 }]}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.intro}>
            <View style={styles.introText}>
              <Text style={styles.title}>모의입양</Text>
              <Text style={styles.description}>
                {
                  '실제 입양 전,\n보호동물과 함께하는 가상 체험을 통해\n최적의 반려동물을 찾는 데 도움을 주는 서비스입니다.'
                }
              </Text>
            </View>
            <Image
              accessible={false}
              resizeMode="contain"
              source={require('../../assets/images/mock-adoption/intro-dog.png')}
              style={styles.introImage}
            />
          </View>
          <Button loading={loading} onPress={openAdoption}>
            {failed ? '다시 시도하기' : session ? `${petName} 보러가기` : '모의입양 시작하기'}
          </Button>
          <View style={styles.sectionHeading}>
            <Text accessibilityRole="header" style={styles.sectionTitle}>
              받은 레포트
            </Text>
            <Text style={styles.count}>{mockAdoptionReports.length}</Text>
          </View>
          {mockAdoptionReports.map(report => {
            const pet = mockAdoptionPets.find(item => item.id === report.petId);
            return (
              <View key={report.id} style={styles.card}>
                <View style={styles.cardHeader}>
                  {pet ? (
                    <Image
                      accessibilityLabel={`${report.petName} (${
                        pet.category === 'cat' ? '고양이' : '개'
                      }) 이미지`}
                      source={pet.image}
                      resizeMode="contain"
                      style={styles.petImage}
                    />
                  ) : null}
                  <View style={styles.petInfo}>
                    <View style={styles.titleRow}>
                      <Text style={styles.petName}>{report.petName} 모의 입양 결과분석</Text>
                      {report.isNew ? (
                        <View style={styles.badge}>
                          <Text style={styles.badgeText}>new</Text>
                        </View>
                      ) : null}
                    </View>
                    <Text style={styles.date}>{report.createdAt}</Text>
                  </View>
                </View>
                <Text numberOfLines={1} style={styles.summary}>
                  {report.summary}
                </Text>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={`${report.petName} 리포트 상세보기`}
                  accessibilityState={{ disabled: true }}
                  disabled
                  style={styles.detailButton}
                >
                  <Text style={styles.detailLabel}>상세보기</Text>
                  <Text accessible={false} style={styles.detailArrow}>
                    →
                  </Text>
                </Pressable>
              </View>
            );
          })}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.white },
  content: { paddingHorizontal: 24, paddingTop: 28, gap: 12 },
  intro: { minHeight: 130, marginBottom: 8 },
  introText: { gap: 18 },
  title: { ...typography.titleBold18, fontSize: 26, lineHeight: 34, color: colors.black },
  description: {
    ...typography.bodyRegular14,
    color: colors.gray500,
    lineHeight: 20,
    paddingRight: 64,
  },
  introImage: { position: 'absolute', right: 0, top: 0, width: 90, height: 100 },
  sectionHeading: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 8,
    marginBottom: 4,
  },
  sectionTitle: { ...typography.titleBold18, color: colors.black },
  count: { ...typography.bodySemiBold16, color: colors.green400 },
  card: {
    backgroundColor: '#FCFDFE',
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.gray200,
    padding: 16,
    gap: 16,
  },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  petInfo: { flex: 1, gap: 6 },
  titleRow: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 8 },
  petName: {
    ...typography.bodySemiBold16,
    fontFamily: 'Pretendard-Bold',
    color: colors.black,
    flexShrink: 1,
  },
  date: { ...typography.bodyRegular14, color: colors.gray500 },
  badge: {
    backgroundColor: '#789AF5',
    borderRadius: radius.pill,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  badgeText: { ...typography.captionSemiBold12, color: colors.white },
  petImage: { width: 64, height: 64 },
  summary: { ...typography.bodyRegular14, color: colors.gray500, lineHeight: 22 },
  detailButton: {
    alignSelf: 'flex-end',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: 32,
  },
  detailLabel: { ...typography.bodyRegular14, color: colors.gray300 },
  detailArrow: { ...typography.bodyRegular16, color: colors.gray300 },
});
