import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { Image, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import KakaoLoginButton from '../../assets/icons/common/kakao-login-btn.svg';
import Logo from '../../assets/images/logo.png';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

export function LoginScreen({ navigation }: Props) {
  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.content}>
        <Image resizeMode="contain" source={Logo} style={styles.logo} />
        <Pressable
          accessibilityLabel="카카오 로그인"
          accessibilityRole="button"
          onPress={() => navigation.reset({ index: 0, routes: [{ name: 'Home' }] })}
          style={({ pressed }) => [styles.kakaoButton, pressed && styles.pressed]}
        >
          <KakaoLoginButton height={45} width={300} />
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
});
