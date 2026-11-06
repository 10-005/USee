import { useCallback, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import { Alert, Image, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppHeader, Button } from '../../components/common';
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
                <View style={styles.petInfo}>
                  <Text style={styles.petName}>
                    {report.petName} ({pet?.category === 'cat' ? '고양이' : '개'})
                  </Text>
                  <Text style={styles.date}>{report.createdAt}</Text>
                </View>
                {report.isNew ? (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText}>new</Text>
                  </View>
                ) : null}
                {pet ? (
                  <Image
                    accessibilityLabel={`${report.petName} 이미지`}
                    source={pet.image}
                    resizeMode="contain"
                    style={styles.petImage}
                  />
                ) : null}
              </View>
              <Text style={styles.summary}>{report.summary}</Text>
              <Button disabled variant="secondary" style={styles.detailButton}>
                상세보기
              </Button>
            </View>
          );
        })}
      </ScrollView>
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
    backgroundColor: colors.gray10,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.gray200,
    padding: 16,
    gap: 16,
  },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  petInfo: { flex: 1, gap: 12 },
  petName: { ...typography.titleBold18, color: colors.black },
  date: { ...typography.bodyRegular14, color: colors.gray500 },
  badge: {
    backgroundColor: '#494DFF',
    borderRadius: radius.pill,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  badgeText: { ...typography.captionSemiBold12, color: colors.white },
  petImage: { width: 58, height: 58, backgroundColor: colors.white, borderRadius: radius.pill },
  summary: { ...typography.bodyRegular14, color: colors.gray800, lineHeight: 22 },
  detailButton: { width: '55%' },
});
