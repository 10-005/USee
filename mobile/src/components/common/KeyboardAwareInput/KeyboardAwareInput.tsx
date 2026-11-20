import {
  createContext,
  forwardRef,
  useCallback,
  useContext,
  useEffect,
  useRef,
  type ComponentRef,
  type PropsWithChildren,
  type RefObject,
} from 'react';
import { TextInput, type ScrollView, type TextInputProps } from 'react-native';

type ScrollViewHandle = ComponentRef<typeof ScrollView>;
type RevealInput = (inputHandle: number) => void;

const RevealInputContext = createContext<RevealInput | null>(null);

export function KeyboardAwareInputProvider({
  additionalOffset = 80,
  children,
  scrollRef,
}: PropsWithChildren<{
  additionalOffset?: number;
  scrollRef: RefObject<ScrollViewHandle | null>;
}>) {
  const timers = useRef<Array<ReturnType<typeof setTimeout>>>([]);

  useEffect(
    () => () => {
      timers.current.forEach(clearTimeout);
    },
    [],
  );

  const revealInput = useCallback<RevealInput>(
    inputHandle => {
      const reveal = () => {
        scrollRef.current
          ?.getScrollResponder()
          ?.scrollResponderScrollNativeHandleToKeyboard(inputHandle, additionalOffset, true);
      };
      requestAnimationFrame(reveal);
      timers.current.push(setTimeout(reveal, 300));
    },
    [additionalOffset, scrollRef],
  );

  return (
    <RevealInputContext.Provider value={revealInput}>{children}</RevealInputContext.Provider>
  );
}

export const KeyboardAwareTextInput = forwardRef<ComponentRef<typeof TextInput>, TextInputProps>(
  ({ onFocus, ...props }, ref) => {
    const revealInput = useContext(RevealInputContext);
    return (
      <TextInput
        {...props}
        onFocus={event => {
          revealInput?.(event.nativeEvent.target);
          onFocus?.(event);
        }}
        ref={ref}
      />
    );
  },
);

KeyboardAwareTextInput.displayName = 'KeyboardAwareTextInput';
