import { Fragment, useEffect, useId, useMemo, useRef, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, Text, View } from 'react-native';
import Svg, {
  Circle,
  ClipPath,
  Defs,
  G,
  Line,
  LinearGradient,
  Path,
  Polygon,
  RadialGradient,
  Rect,
  Stop,
  Text as SvgText,
} from 'react-native-svg';

import { colors, typography } from '../../theme';
import FilterArrowIcon from '../../assets/icons/common/filter-arrow.svg';
import {
  dailyAxisStep,
  expenseGroups,
  reportDailyAmounts,
  REPORT_STAT_ITEMS,
  statTone,
  won,
} from './reportLabels';

const STAT_COLOR = { low: '#A63828', medium: '#89500E', high: '#036666' };
const RADAR = { cx: 140, cy: 126, radius: 72, labelRadius: 108 };
const TRACK = 168;
const BAR_WIDTH = 18;
const BAR_FOCUS_MS = 200;
const easeOut = Easing.out(Easing.cubic);
const AnimatedG = Animated.createAnimatedComponent(G);

function polar(angle: number, radius: number) {
  return {
    x: (100 + radius * Math.cos(angle)).toFixed(2),
    y: (100 + radius * Math.sin(angle)).toFixed(2),
  };
}

function donutPath(start: number, end: number) {
  if (end - start <= 0) {
    return '';
  }
  const middle = (start + end) / 2;
  const outerStart = polar(start, 84);
  const outerMiddle = polar(middle, 84);
  const outerEnd = polar(end, 84);
  const innerEnd = polar(end, 56);
  const innerMiddle = polar(middle, 56);
  const innerStart = polar(start, 56);
  return [
    `M${outerStart.x} ${outerStart.y}`,
    `A84 84 0 0 1 ${outerMiddle.x} ${outerMiddle.y}`,
    `A84 84 0 0 1 ${outerEnd.x} ${outerEnd.y}`,
    `L${innerEnd.x} ${innerEnd.y}`,
    `A56 56 0 0 0 ${innerMiddle.x} ${innerMiddle.y}`,
    `A56 56 0 0 0 ${innerStart.x} ${innerStart.y}`,
    'Z',
  ].join(' ');
}

function move(value: Animated.Value, to: number, duration: number) {
  Animated.timing(value, {
    toValue: to,
    duration,
    easing: easeOut,
    useNativeDriver: false,
  }).start();
}

export function GradientFill({
  id,
  from,
  to,
  horizontal = false,
}: {
  id: string;
  from: string;
  to: string;
  horizontal?: boolean;
}) {
  return (
    <Svg
      pointerEvents="none"
      style={StyleSheet.absoluteFill}
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
    >
      <Defs>
        <LinearGradient
          id={id}
          x1="0"
          y1="0"
          x2={horizontal ? '1' : '0.45'}
          y2={horizontal ? '0' : '1'}
        >
          <Stop offset="0%" stopColor={from} />
          <Stop offset="100%" stopColor={to} />
        </LinearGradient>
      </Defs>
      <Rect x="0" y="0" width="100" height="100" fill={`url(#${id})`} />
    </Svg>
  );
}

function ExpenseSlice({
  active,
  color,
  d,
  dx,
  dy,
  gradientId,
  onPress,
}: {
  active: boolean;
  color: string;
  d: string;
  dx: number;
  dy: number;
  gradientId: string;
  onPress: () => void;
}) {
  const progress = useRef(new Animated.Value(active ? 1 : 0)).current;
  useEffect(() => {
    move(progress, active ? 1 : 0, 240);
  }, [active, progress]);
  const translateX = progress.interpolate({ inputRange: [0, 1], outputRange: [0, dx] });
  const translateY = progress.interpolate({ inputRange: [0, 1], outputRange: [0, dy] });
  const opacity = progress.interpolate({ inputRange: [0, 1], outputRange: [0.87, 1] });

  return (
    <AnimatedG opacity={opacity} translateX={translateX} translateY={translateY}>
      {active ? <Path d={d} fill={color} fillOpacity={0.18} transform="translate(0, 3)" /> : null}
      <Path
        d={d}
        fill={`url(#${gradientId})`}
        stroke="#FFFFFF"
        strokeLinejoin="round"
        strokeWidth={1.4}
        onPress={onPress}
      />
    </AnimatedG>
  );
}

export function CategorySpending({
  spent,
  total,
}: {
  spent: Record<string, number>;
  total: number;
}) {
  const groups = useMemo(() => expenseGroups(spent), [spent]);
  const [selected, setSelected] = useState<string>();
  const [expanded, setExpanded] = useState<string>();
  const active = groups.find(item => item.key === selected) ?? groups.find(item => item.amount > 0);
  let angle = -Math.PI / 2;
  const slices = groups
    .filter(item => item.amount > 0)
    .map(item => {
      const start = angle;
      const share = total > 0 ? item.amount / total : 0;
      angle += share * Math.PI * 2;
      const middle = (start + angle) / 2;
      return {
        ...item,
        path: donutPath(start, angle),
        dx: Math.cos(middle) * 7,
        dy: Math.sin(middle) * 7,
      };
    });

  return (
    <View style={styles.breakdown}>
      <View style={styles.figure}>
        <Svg viewBox="0 0 200 200" width="100%" height="100%">
          <Defs>
            {slices.map(item => (
              <RadialGradient
                key={item.key}
                id={`expense-${item.key}`}
                cx="100"
                cy="100"
                r="84"
                gradientUnits="userSpaceOnUse"
              >
                <Stop offset="0%" stopColor={item.color} />
                <Stop offset="75%" stopColor={item.color} />
                <Stop offset="91%" stopColor={item.color} stopOpacity={0.85} />
                <Stop offset="100%" stopColor={item.color} stopOpacity={0.72} />
              </RadialGradient>
            ))}
          </Defs>
          {total === 0 ? (
            <Circle cx="100" cy="100" r="70" fill="none" stroke="#EBEAE5" strokeWidth={28} />
          ) : null}
          {slices.map(item => (
            <ExpenseSlice
              key={item.key}
              active={item.key === active?.key}
              color={item.color}
              d={item.path}
              dx={item.dx}
              dy={item.dy}
              gradientId={`expense-${item.key}`}
              onPress={() => {
                setSelected(item.key);
                setExpanded(item.key);
              }}
            />
          ))}
        </Svg>
        {active ? (
          <View
            pointerEvents="none"
            style={styles.figureCaption}
            accessibilityLabel={`선택한 지출 ${active.label} ${won(active.amount)}`}
          >
            <Text style={styles.figureLabel}>{active.label}</Text>
            <Text style={styles.figureAmount}>{won(active.amount)}</Text>
            <Text style={styles.figureShare}>
              {total > 0 ? Math.round((active.amount / total) * 100) : 0}%
            </Text>
          </View>
        ) : (
          <View pointerEvents="none" style={styles.figureCaption}>
            <Text style={styles.figureLabel}>지출한 항목이 없어요.</Text>
          </View>
        )}
      </View>
      <View>
        {groups.map(item => {
          const pressed = item.key === active?.key;
          const open = expanded === item.key;
          return (
            <View key={item.key}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`${item.label} ${won(item.amount)}`}
                accessibilityState={{ selected: pressed, expanded: open }}
                onPress={() => {
                  setSelected(item.key);
                  setExpanded(current => (current === item.key ? undefined : item.key));
                }}
                style={[
                  styles.legendItem,
                  pressed && { backgroundColor: `${item.color}33`, borderColor: item.color },
                ]}
              >
                <View style={[styles.swatch, { backgroundColor: item.color }]} />
                <Text style={[styles.legendLabel, pressed && styles.legendLabelActive]}>
                  {item.label}
                </Text>
                <Text style={[styles.legendAmount, pressed && styles.legendAmountActive]}>
                  {won(item.amount)}
                </Text>
                <FilterArrowIcon
                  width={12}
                  height={12}
                  style={open ? styles.expandedArrow : undefined}
                />
              </Pressable>
              {open ? (
                <View
                  accessibilityLabel={`${item.label} 구매 내역`}
                  style={[styles.purchaseList, { borderLeftColor: item.color }]}
                >
                  {item.purchases.length > 0 ? (
                    item.purchases.map(purchase => (
                      <View key={purchase.key} style={styles.purchaseRow}>
                        <Text style={styles.purchaseLabel}>{purchase.label}</Text>
                        <Text style={styles.purchaseAmount}>{won(purchase.amount)}</Text>
                      </View>
                    ))
                  ) : (
                    <Text style={styles.emptyPurchases}>구매 내역이 없어요.</Text>
                  )}
                </View>
              ) : null}
            </View>
          );
        })}
      </View>
    </View>
  );
}

function DailyBar({
  amount,
  index,
  last,
  onPress,
  scale,
  selected,
}: {
  amount: number;
  index: number;
  last: boolean;
  onPress: () => void;
  scale: number;
  selected: boolean;
}) {
  const barHeight =
    scale > 0 && amount > 0 ? Math.max(10, Math.min(TRACK, (amount / scale) * TRACK)) : 0;
  const focus = useRef(new Animated.Value(selected ? 1 : 0)).current;
  useEffect(() => {
    move(focus, selected ? 1 : 0, BAR_FOCUS_MS);
  }, [focus, selected]);
  const tipShift = focus.interpolate({ inputRange: [0, 1], outputRange: [4, 0] });

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${index + 1}일차 지출 ${won(amount)}`}
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[styles.barColumn, selected && styles.barColumnSelected]}
    >
      <View style={styles.barTrack}>
        <View style={styles.barSlot}>
          <View collapsable={false} style={[styles.bar, { height: barHeight }]}>
            <View style={[styles.barFill, { height: barHeight }]}>
              {barHeight > 0 ? (
                <Svg width={BAR_WIDTH} height={barHeight}>
                  <Defs>
                    <LinearGradient id={`day-${index}-base`} x1="0" y1="0" x2="0" y2="1">
                      <Stop offset="0%" stopColor="#7DCFB0" />
                      <Stop offset="100%" stopColor="#CDE8DC" />
                    </LinearGradient>
                  </Defs>
                  <Rect width={BAR_WIDTH} height={barHeight} fill={`url(#day-${index}-base)`} />
                </Svg>
              ) : null}
              {barHeight > 0 ? (
                <Animated.View pointerEvents="none" style={[styles.barShade, { opacity: focus }]}>
                  <Svg width={BAR_WIDTH} height={barHeight}>
                    <Defs>
                      <LinearGradient id={`day-${index}-on`} x1="0" y1="0" x2="0" y2="1">
                        <Stop offset="0%" stopColor="#248277" />
                        <Stop offset="78%" stopColor="#99E2B4" />
                        <Stop offset="100%" stopColor="#F5FAF7" />
                      </LinearGradient>
                    </Defs>
                    <Rect width={BAR_WIDTH} height={barHeight} fill={`url(#day-${index}-on)`} />
                  </Svg>
                </Animated.View>
              ) : null}
            </View>
            <Animated.View style={[styles.barDot, { opacity: focus }]} />
            {selected ? (
              <Animated.View
                style={[
                  styles.tip,
                  last ? styles.tipEnd : styles.tipCenter,
                  { opacity: focus, transform: [{ translateY: tipShift }] },
                ]}
              >
                <Text style={styles.tipLabel}>{`${index + 1}일차 지출`}</Text>
                <Text style={styles.tipAmount}>{won(amount)}</Text>
              </Animated.View>
            ) : null}
          </View>
        </View>
      </View>
      <Text style={[styles.dayLabel, selected && styles.dayLabelSelected]}>{`${
        index + 1
      }일차`}</Text>
    </Pressable>
  );
}

export function DailySpending({ daily }: { daily: Record<string, number> }) {
  const amounts = reportDailyAmounts(daily);
  const step = dailyAxisStep(amounts);
  const peak = Math.max(0, ...amounts);
  const [selected, setSelected] = useState(Math.max(0, amounts.indexOf(peak)));
  const active = selected >= 0 && selected < amounts.length ? selected : 0;
  const scale = step * 4;

  return (
    <View style={styles.daily}>
      <View style={styles.dailyRow}>
        <View style={styles.axis}>
          {[4, 3, 2, 1, 0].map(tick => (
            <Text key={tick} style={styles.axisLabel}>
              {won(tick * step)}
            </Text>
          ))}
        </View>
        <View style={styles.bars}>
          <View pointerEvents="none" style={styles.grid}>
            {[0, 1, 2, 3].map(line => (
              <View key={line} style={[styles.gridLine, { top: (line * TRACK) / 4 }]} />
            ))}
          </View>
          {amounts.map((amount, index) => (
            <DailyBar
              key={String(index)}
              amount={amount}
              index={index}
              last={index === amounts.length - 1}
              onPress={() => setSelected(index)}
              scale={scale}
              selected={active === index}
            />
          ))}
        </View>
      </View>
    </View>
  );
}

function radarPoint(index: number, count: number, level: number) {
  const angle = -Math.PI / 2 + (index * 2 * Math.PI) / count;
  const distance = (RADAR.radius * level) / 100;
  return {
    x: RADAR.cx + Math.cos(angle) * distance,
    y: RADAR.cy + Math.sin(angle) * distance,
    labelX: RADAR.cx + Math.cos(angle) * RADAR.labelRadius,
    labelY: RADAR.cy + Math.sin(angle) * RADAR.labelRadius,
  };
}

export function FinalStateChart({ stats }: { stats: Record<string, number> }) {
  const chartId = useId().replace(/:/g, '');
  const points = REPORT_STAT_ITEMS.map((item, index) => {
    const value = stats[item.key] ?? 0;
    return {
      ...item,
      value,
      color: STAT_COLOR[statTone(value)],
      ...radarPoint(index, REPORT_STAT_ITEMS.length, value),
    };
  });
  const outline = points.map(item => `${item.x},${item.y}`).join(' ');
  const baselinePoints = REPORT_STAT_ITEMS.map((_, index) =>
    radarPoint(index, REPORT_STAT_ITEMS.length, 100),
  );
  const baseline = baselinePoints.map(point => `${point.x},${point.y}`).join(' ');
  const summary = points.map(item => `${item.label} ${item.value}%`).join(', ');

  return (
    <View style={styles.radarPanel}>
      <Svg pointerEvents="none" style={StyleSheet.absoluteFill} preserveAspectRatio="none">
        <Defs>
          <RadialGradient id={`radar-panel-${chartId}`} cx="50%" cy="40%" r="75%">
            <Stop offset="0%" stopColor="#FAFCFB" />
            <Stop offset="100%" stopColor="#EDF4F0" />
          </RadialGradient>
        </Defs>
        <Rect width="100%" height="100%" fill={`url(#radar-panel-${chartId})`} />
      </Svg>
      <Svg
        accessibilityLabel={summary}
        accessibilityRole="image"
        viewBox="-28 -12 336 276"
        width="100%"
        height={230}
      >
        <Defs>
          <ClipPath id={`radar-area-${chartId}`}>
            <Polygon points={outline} />
          </ClipPath>
          {points.map((item, index) => {
            const next = points[(index + 1) % points.length];
            return (
              <Fragment key={item.key}>
                <RadialGradient
                  id={`radar-fill-${chartId}-${item.key}`}
                  cx={item.x}
                  cy={item.y}
                  r={RADAR.radius * 1.5}
                  gradientUnits="userSpaceOnUse"
                >
                  <Stop offset="0%" stopColor={item.color} stopOpacity={0.24} />
                  <Stop offset="55%" stopColor={item.color} stopOpacity={0.08} />
                  <Stop offset="100%" stopColor={item.color} stopOpacity={0} />
                </RadialGradient>
                <LinearGradient
                  id={`radar-edge-${chartId}-${item.key}`}
                  x1={item.x}
                  y1={item.y}
                  x2={next.x}
                  y2={next.y}
                  gradientUnits="userSpaceOnUse"
                >
                  <Stop offset="0%" stopColor={item.color} />
                  <Stop offset="100%" stopColor={next.color} />
                </LinearGradient>
              </Fragment>
            );
          })}
        </Defs>
        {[RADAR.radius + 10, RADAR.radius * 0.77, RADAR.radius * 0.42].map(radius => (
          <Circle key={radius} cx={RADAR.cx} cy={RADAR.cy} r={radius} fill="#FFFFFF" />
        ))}
        {baselinePoints.map((point, index) => (
          <Line
            key={REPORT_STAT_ITEMS[index].key}
            x1={RADAR.cx}
            y1={RADAR.cy}
            x2={point.x}
            y2={point.y}
            stroke="#DFEAE5"
            strokeWidth={0.8}
          />
        ))}
        <Polygon
          points={baseline}
          fill="none"
          stroke="#88BFA7"
          strokeWidth={1}
          strokeLinejoin="round"
        />
        <G clipPath={`url(#radar-area-${chartId})`}>
          {points.map(item => (
            <Rect
              key={item.key}
              x={RADAR.cx - RADAR.radius}
              y={RADAR.cy - RADAR.radius}
              width={RADAR.radius * 2}
              height={RADAR.radius * 2}
              fill={`url(#radar-fill-${chartId}-${item.key})`}
            />
          ))}
        </G>
        {points.map((item, index) => {
          const next = points[(index + 1) % points.length];
          return (
            <Line
              key={item.key}
              x1={item.x}
              y1={item.y}
              x2={next.x}
              y2={next.y}
              stroke={`url(#radar-edge-${chartId}-${item.key})`}
              strokeWidth={1.6}
              strokeLinecap="round"
            />
          );
        })}
        {points.map(item => (
          <Circle
            key={item.key}
            cx={item.x}
            cy={item.y}
            r={3.2}
            fill={item.color}
            stroke="#FFFFFF"
            strokeWidth={1}
          />
        ))}
        {points.map(item => (
          <SvgText
            key={item.key}
            x={item.labelX}
            y={item.labelY}
            fill="#344A40"
            fontSize="11"
            fontFamily="Pretendard-Regular"
            textAnchor="middle"
          >
            {item.label}
          </SvgText>
        ))}
        {points.map(item => (
          <SvgText
            key={`${item.key}-value`}
            x={item.labelX}
            y={item.labelY + 14}
            fill={item.color}
            fontSize="12"
            fontFamily="Pretendard-Bold"
            textAnchor="middle"
          >
            {`${item.value}%`}
          </SvgText>
        ))}
      </Svg>
      <View style={styles.radarLegend}>
        <View style={styles.radarKey}>
          <View style={[styles.radarLine, styles.radarLineActual]} />
          <Text style={styles.radarKeyLabel}>최종 상태</Text>
        </View>
        <View style={styles.radarKey}>
          <View style={[styles.radarLine, styles.radarLineBase]} />
          <Text style={styles.radarKeyLabel}>100% 기준선</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  breakdown: { gap: 18 },
  figure: { alignSelf: 'center', height: 220, overflow: 'visible', width: 220 },
  figureCaption: {
    alignItems: 'center',
    height: 220,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  figureLabel: { ...typography.bodySemiBold16, color: '#245C48', fontSize: 14, lineHeight: 18 },
  figureAmount: {
    color: '#036666',
    fontFamily: 'Pretendard-Bold',
    fontSize: 20,
    lineHeight: 26,
  },
  figureShare: {
    color: '#14746F',
    fontFamily: 'Pretendard-Bold',
    fontSize: 14,
    lineHeight: 18,
  },
  legendItem: {
    alignItems: 'center',
    borderColor: colors.transparent,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 8,
    minHeight: 36,
    paddingHorizontal: 8,
  },
  expandedArrow: { transform: [{ rotate: '180deg' }] },
  purchaseList: {
    borderLeftWidth: 2,
    gap: 10,
    marginBottom: 12,
    marginLeft: 12,
    marginTop: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  purchaseRow: { alignItems: 'center', flexDirection: 'row', gap: 12 },
  purchaseLabel: { ...typography.bodyRegular14, color: colors.gray600, flex: 1 },
  purchaseAmount: { ...typography.bodyRegular14, color: colors.gray700 },
  emptyPurchases: { ...typography.bodyRegular14, color: colors.gray500 },
  swatch: { borderRadius: 3, height: 10, width: 10 },
  legendLabel: { ...typography.bodyRegular14, color: colors.gray700, flex: 1 },
  legendLabelActive: { color: '#245C48', fontFamily: 'Pretendard-SemiBold' },
  legendAmount: { ...typography.bodyRegular14, color: colors.gray700 },
  legendAmountActive: { color: '#036666', fontFamily: 'Pretendard-Bold', fontSize: 15 },
  daily: { paddingTop: 56 },
  dailyRow: { flexDirection: 'row', gap: 8 },
  axis: { flexShrink: 0, height: TRACK, justifyContent: 'space-between', width: 58 },
  axisLabel: { ...typography.captionRegular10, color: colors.gray500, textAlign: 'right' },
  bars: { flex: 1, flexDirection: 'row', position: 'relative' },
  grid: { height: TRACK, left: 0, position: 'absolute', right: 0, top: 0 },
  gridLine: { backgroundColor: '#EDF1EF', height: 1, left: 0, position: 'absolute', right: 0 },
  barColumn: { alignItems: 'center', flex: 1, overflow: 'visible' },
  barColumnSelected: { zIndex: 1 },
  barTrack: { height: TRACK, overflow: 'visible', width: '100%' },
  barSlot: {
    backgroundColor: '#E7F2EC',
    borderTopLeftRadius: 8,
    borderTopRightRadius: 8,
    height: TRACK,
    justifyContent: 'flex-end',
    overflow: 'visible',
    width: BAR_WIDTH,
  },
  bar: { overflow: 'visible', width: BAR_WIDTH },
  barFill: {
    backgroundColor: '#8FCFB0',
    borderTopLeftRadius: 8,
    borderTopRightRadius: 8,
    overflow: 'hidden',
    width: BAR_WIDTH,
  },
  barShade: { ...StyleSheet.absoluteFill },
  barDot: {
    backgroundColor: '#248277',
    borderColor: colors.white,
    borderRadius: 5,
    borderWidth: 2,
    height: 9,
    left: (BAR_WIDTH - 9) / 2,
    position: 'absolute',
    top: -5,
    width: 9,
  },
  tip: {
    backgroundColor: colors.white,
    borderColor: '#E2EBE6',
    borderRadius: 8,
    borderWidth: 1,
    bottom: '100%',
    gap: 2,
    marginBottom: 9,
    minWidth: 88,
    paddingHorizontal: 8,
    paddingVertical: 6,
    position: 'absolute',
  },
  tipCenter: { left: '50%', marginLeft: -44 },
  tipEnd: { right: 0 },
  tipLabel: { ...typography.captionRegular10, color: colors.gray600 },
  tipAmount: {
    color: '#036666',
    fontFamily: 'Pretendard-Bold',
    fontSize: 14,
    lineHeight: 18,
  },
  dayLabel: {
    ...typography.captionSemiBold12,
    color: colors.gray500,
    marginTop: 10,
    textAlign: 'center',
  },
  dayLabelSelected: { color: '#248277' },
  radarPanel: {
    borderRadius: 14,
    overflow: 'hidden',
    paddingBottom: 12,
    paddingTop: 8,
    position: 'relative',
  },
  radarLegend: { flexDirection: 'row', gap: 16, justifyContent: 'center' },
  radarKey: { alignItems: 'center', flexDirection: 'row', gap: 6 },
  radarLine: { borderRadius: 2, height: 2, width: 16 },
  radarLineActual: { backgroundColor: '#14746F' },
  radarLineBase: { backgroundColor: '#88BFA7' },
  radarKeyLabel: { ...typography.captionSemiBold12, color: '#435E54' },
});
