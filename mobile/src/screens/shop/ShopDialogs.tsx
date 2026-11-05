import { useState, type ReactNode } from 'react';
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button } from '../../components/common';
import { shopStyles as s } from '../../components/shop/shopStyles';
import { useShopStore } from '../../stores/shopStore';
import { colors } from '../../theme';
import SelectedIcon from '../../assets/icons/common/check.svg';

function ShopDialog({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const insets = useSafeAreaInsets();
  return (
    <Modal transparent animationType="fade" onRequestClose={onClose}>
      <KeyboardAvoidingView
        style={styles.overlay}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="닫기"
          style={StyleSheet.absoluteFill}
          onPress={onClose}
        />
        <View
          accessibilityViewIsModal
          style={[styles.sheet, { paddingBottom: 20 + insets.bottom }]}
        >
          <View style={s.between}>
            <Text style={s.label}>{title}</Text>
            <Pressable accessibilityLabel="닫기" accessibilityRole="button" onPress={onClose}>
              <Text style={s.body}>닫기</Text>
            </Pressable>
          </View>
          <View style={s.line} />
          {children}
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

export function ShopOptionDialog({
  title,
  options,
  value,
  onSelect,
  onClose,
}: {
  title: string;
  options: string[];
  value: string;
  onSelect: (value: string) => void;
  onClose: () => void;
}) {
  return (
    <ShopDialog title={title} onClose={onClose}>
      <ScrollView>
        {options.map(option => (
          <Pressable
            key={option}
            accessibilityRole="radio"
            accessibilityState={{ checked: option === value }}
            accessibilityLabel={option}
            onPress={() => {
              onSelect(option);
              onClose();
            }}
            style={styles.option}
          >
            <Text style={[s.body, option === value && s.green]}>{option}</Text>
            {option === value && <SelectedIcon width={20} height={20} />}
          </Pressable>
        ))}
      </ScrollView>
    </ShopDialog>
  );
}

export function BudgetDialog({ onClose }: { onClose: () => void }) {
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  const budget = useShopStore(state => state.budget);
  const amount = Number(value);
  const valid =
    /^\d+$/.test(value) &&
    Number.isSafeInteger(amount) &&
    amount > 0 &&
    amount + budget <= 100_000_000;
  return (
    <ShopDialog title="예산 추가" onClose={onClose}>
      <TextInput
        accessibilityLabel="추가할 예산"
        placeholder="추가할 금액을 입력해주세요"
        placeholderTextColor={colors.gray400}
        keyboardType="number-pad"
        value={value}
        onChangeText={text => {
          setValue(text.replace(/[^0-9]/g, ''));
          setError('');
        }}
        style={styles.input}
      />
      <View style={s.row}>
        {[10000, 50000, 100000].map(increment => (
          <Pressable
            key={increment}
            accessibilityRole="button"
            onPress={() => setValue(String(amount + increment))}
            style={styles.increment}
          >
            <Text style={s.green}>{`+${increment / 10000}만`}</Text>
          </Pressable>
        ))}
      </View>
      <Text accessibilityLiveRegion="polite" style={s.body}>
        {error ||
          (amount + budget > 100_000_000
            ? '총 예산은 1억 원까지 추가할 수 있어요.'
            : '추가한 예산은 현재 모의 입양에 적용돼요.')}
      </Text>
      <Button
        disabled={!valid}
        onPress={() => {
          if (useShopStore.getState().addBudget(amount)) {
            onClose();
          } else {
            setError(
              useShopStore.getState().error ||
                '예산을 추가할 수 없어요. 모의 입양 기간과 금액을 확인해주세요.',
            );
          }
        }}
      >
        추가하기
      </Button>
    </ShopDialog>
  );
}
const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: '#00000033', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
    gap: 16,
    maxHeight: '80%',
  },
  option: {
    minHeight: 44,
    paddingHorizontal: 8,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  input: {
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: 10,
    padding: 14,
    color: colors.black,
    fontSize: 16,
  },
  increment: {
    flex: 1,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.gray200,
    borderRadius: 10,
    paddingVertical: 10,
  },
});
