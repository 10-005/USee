import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { ActivityIndicator, Alert, Image, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import KakaoLoginButton from '../../assets/icons/common/kakao-login-btn.svg';
import Logo from '../../assets/images/usee_logo.png';
import { getApiErrorDisplayMessage } from '../../services/api';
import { useAuthStore } from '../../stores/authStore';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

export function LoginScreen({ navigation }: Props) {
  const isLoading = useAuthStore(state => state.isLoading);
  const loginWithKakao = useAuthStore(state => state.loginWithKakao);

  const handleLogin = async () => {
    try {
      const result = await loginWithKakao();
      if (result === 'registrationRequired') {
        navigation.navigate('SignupOnboarding');
      }
    } catch (error) {
      Alert.alert(
        '로그인 실패',
        getApiErrorDisplayMessage(error, '잠시 후 다시 시도해 주세요.'),
      );
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.content}>
        <Image resizeMode="contain" source={Logo} style={styles.logo} />
        <Pressable
          accessibilityLabel="카카오 로그인"
          accessibilityRole="button"
          disabled={isLoading}
          onPress={handleLogin}
          style={({ pressed }) => [styles.kakaoButton, pressed && styles.pressed]}
        >
          {isLoading ? (
            <ActivityIndicator color="#191919" style={styles.loginSpinner} />
          ) : (
            <KakaoLoginButton height={45} width={300} />
          )}
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: '#FFFFFF', flex: 1 },
  content: { alignItems: 'center', flex: 1 },
  logo: { height: 117, marginTop: 269, width: 234 },
  kakaoButton: {
    height: 45,
    marginTop: 172,
    width: 300,
  },
  pressed: { opacity: 0.8 },
  loginSpinner: { height: 45 },
});
