import { useEffect, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

const ACTIONS = [
  ['bark', '짖기'],
  ['sit', '앉기'],
  ['lie', '눕기'],
  ['bellyLie', '배 보이며 눕기'],
  ['jumpPlace', '제자리 점프'],
  ['jumpForward', '앞으로 점프'],
  ['jumpRun', '달리며 점프'],
  ['jumpHigh', '도약 · 공중 · 착지'],
  ['eat', '먹기'],
  ['drink', '물 마시기'],
  ['dig', '땅파기'],
  ['crouch', '웅크리기'],
  ['scratch', '귀 긁기'],
  ['pee', '소변'],
  ['poop', '배변'],
] as const;

type DogActionsProps = {
  available: boolean;
  moving: boolean;
  bottom: number;
  onAction: (action: string) => void;
};

export function DogActions({ available, moving, bottom, onAction }: DogActionsProps) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!available || moving) {
      setOpen(false);
    }
  }, [available, moving]);

  if (!available) {
    return null;
  }

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="강아지 동작 메뉴"
        accessibilityState={{ disabled: moving }}
        disabled={moving}
        onPress={() => setOpen(true)}
        style={[styles.openButton, { bottom: bottom + 24 }, moving && styles.disabled]}
      >
        <Text style={styles.buttonText}>{moving ? '멈추면 동작을 할 수 있어요' : '동작'}</Text>
      </Pressable>
      <Modal transparent visible={open} animationType="fade" onRequestClose={() => setOpen(false)}>
        <View style={styles.backdrop}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="동작 메뉴 닫기"
            onPress={() => setOpen(false)}
            style={StyleSheet.absoluteFill}
          />
          <View accessibilityViewIsModal style={[styles.sheet, { paddingBottom: bottom + 16 }]}>
            <Text style={styles.title}>강아지 동작</Text>
            <Text style={styles.hint}>다시 걸으면 산책을 이어갑니다.</Text>
            <ScrollView decelerationRate="fast">
              <View style={styles.grid}>
                {ACTIONS.map(([action, label]) => (
                  <Pressable
                    key={action}
                    accessibilityRole="button"
                    accessibilityLabel={label}
                    onPress={() => {
                      onAction(action);
                      setOpen(false);
                    }}
                    style={styles.action}
                  >
                    <Text style={styles.buttonText}>{label}</Text>
                  </Pressable>
                ))}
              </View>
            </ScrollView>
            <Pressable
              accessibilityRole="button"
              onPress={() => setOpen(false)}
              style={styles.close}
            >
              <Text style={styles.buttonText}>닫기</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  openButton: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 28,
    justifyContent: 'center',
    left: 32,
    minHeight: 56,
    position: 'absolute',
    right: 32,
  },
  disabled: { backgroundColor: '#AAAAAA' },
  buttonText: { color: '#111111', fontSize: 15, fontWeight: '600' },
  backdrop: { backgroundColor: 'rgba(0, 0, 0, 0.45)', flex: 1, justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    maxHeight: '75%',
    padding: 20,
  },
  title: { color: '#111111', fontSize: 20, fontWeight: '700' },
  hint: { color: '#666666', fontSize: 13, marginBottom: 16, marginTop: 6 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  action: {
    alignItems: 'center',
    backgroundColor: '#F1F2F4',
    borderRadius: 14,
    justifyContent: 'center',
    minHeight: 52,
    paddingHorizontal: 8,
    width: '48%',
  },
  close: { alignItems: 'center', minHeight: 48, justifyContent: 'center', marginTop: 12 },
});
