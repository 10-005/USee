import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useRef, useState, type ComponentRef } from 'react';
import {
  Image,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput as NativeTextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { RootStackParamList } from '../../app/navigation/types';
import { AppHeader, Button, TextInput } from '../../components/common';
import { colors, radius, typography } from '../../theme';
import { mockAdoptionPets } from './data';
import { getPetCallName, getPetNameError } from './petName';

type Props = NativeStackScreenProps<RootStackParamList, 'MockAdoptionName'>;
export function MockAdoptionNameScreen({ navigation, route }: Props) {
  const pet = mockAdoptionPets.find(item => item.id === route.params.petId);
  const inputRef = useRef<ComponentRef<typeof TextInput>>(null);
  const [name, setName] = useState('');
  const [touched, setTouched] = useState(false);
  const error = getPetNameError(name);
  const dismissNameInput = () => {
    const focusedInput = NativeTextInput.State.currentlyFocusedInput();
    if (focusedInput) {
      NativeTextInput.State.blurTextInput(focusedInput);
    }
    inputRef.current?.blur();
    Keyboard.dismiss();
  };
  const next = () => {
    setTouched(true);
    if (pet && !error) {
      navigation.navigate('MockAdoptionBudget', { petId: pet.id, petName: name });
    }
  };
  return (
    <SafeAreaView style={styles.screen}>
      <Pressable accessible={false} onPress={dismissNameInput}>
        <AppHeader title="모의 입양" onBackPress={() => navigation.goBack()} />
      </Pressable>
      <KeyboardAvoidingView
        style={styles.screen}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <ScrollView
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={styles.contentGrow}
          onScrollBeginDrag={dismissNameInput}
        >
          <Pressable accessible={false} onPress={dismissNameInput} style={styles.content}>
            <Text style={styles.step}>이름 설정</Text>
            <Text style={styles.title}>함께할 친구의{'\n'}이름을 지어주세요!</Text>
            <Text style={styles.subtitle}>산책 중 이름을 부르면 나를 돌아봐요.</Text>
            {pet ? (
              <View style={styles.pet}>
                <Image
                  accessible={false}
                  source={pet.image}
                  resizeMode="contain"
                  style={styles.image}
                />
                <Text style={styles.breed}>{pet.name}</Text>
              </View>
            ) : (
              <Text style={styles.error}>
                선택한 동물을 찾을 수 없어요. 이전 화면에서 다시 선택해주세요.
              </Text>
            )}
            <TextInput
              ref={inputRef}
              label="반려동물 이름"
              placeholder="예: 초코"
              value={name}
              maxLength={6}
              autoCorrect={false}
              autoCapitalize="none"
              returnKeyType="done"
              onChangeText={setName}
              onBlur={() => setTouched(true)}
              onSubmitEditing={next}
              errorMessage={touched || name ? error : undefined}
            />
            <View style={styles.hintRow}>
              <Text style={styles.hint}>완성된 한글만 · 최대 6글자</Text>
              <Text style={styles.hint}>{name.length}/6</Text>
            </View>
            <View style={styles.guide}>
              <Text style={styles.guideTitle}>
                {!error
                  ? `“${getPetCallName(name)}” 하고 불러보세요`
                  : '이름으로 더 가까워지는 시간'}
              </Text>
              <Text style={styles.subtitle}>
                이름만 부르면 잠시 멈춰 나를 보고 꼬리를 흔들어요.{'\n'}“이름 + 앉아 / 멈춰 / 가자 /
                돌아”로 행동을 알려줄 수도 있어요.
              </Text>
            </View>
          </Pressable>
        </ScrollView>
        <Pressable accessible={false} onPress={dismissNameInput} style={styles.footer}>
          <Button disabled={!pet || Boolean(error)} onPress={next}>
            다음
          </Button>
        </Pressable>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.white },
  contentGrow: { flexGrow: 1 },
  content: { flexGrow: 1, padding: 20, gap: 12 },
  step: { ...typography.bodySemiBold16, color: colors.green400 },
  title: { ...typography.titleBold18, fontSize: 24, lineHeight: 34, color: colors.black },
  subtitle: { ...typography.bodyRegular14, color: colors.gray500, lineHeight: 22 },
  pet: {
    backgroundColor: '#ECFDF3',
    borderRadius: radius.lg,
    alignItems: 'center',
    padding: 16,
    marginVertical: 12,
  },
  image: { width: 160, height: 140 },
  breed: { ...typography.bodySemiBold16, color: colors.gray700 },
  hintRow: { flexDirection: 'row', justifyContent: 'space-between' },
  hint: { ...typography.bodyRegular14, color: colors.gray500 },
  guide: {
    backgroundColor: colors.gray10,
    borderRadius: radius.lg,
    padding: 16,
    gap: 8,
    marginTop: 12,
  },
  guideTitle: { ...typography.bodySemiBold16, color: colors.green400 },
  error: { ...typography.bodyRegular14, color: colors.danger },
  footer: { paddingHorizontal: 20, paddingVertical: 16 },
});
