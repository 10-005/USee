import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Line, Polygon, Path, Text as SvgText } from 'react-native-svg';

import { colors, typography } from '../../theme';
import {
  dailyAxisStep,
  expenseItems,
  reportDailyAmounts,
  REPORT_STAT_ITEMS,
  statTone,
  won,
} from './reportLabels';

const EXPENSE_PALETTE = [
  '#99E2B4',
  '#88D4AB',
  '#78C6A3',
  '#67B99A',
  '#56AB91',
  '#469D89',
  '#358F80',
  '#248277',
  '#14746F',
  '#036666',
];
const STAT_COLOR = { low: '#A63828', medium: '#89500E', high: '#036666' };
const RADAR = { cx: 140, cy: 126, radius: 72, labelRadius: 108 };

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

export function CategorySpending({
  spent,
  total,
}: {
  spent: Record<string, number>;
  total: number;
}) {
  const items = expenseItems(spent);
  const [selected, setSelected] = useState<string>();
  const active = items.find(item => item.key === selected) ?? items[0];
  let angle = -Math.PI / 2;
  const slices = items.map((item, index) => {
    const start = angle;
    const share = total > 0 ? item.amount / total : 0;
    angle += share * Math.PI * 2;
    const middle = (start + angle) / 2;
    return {
      ...item,
      color: EXPENSE_PALETTE[index % EXPENSE_PALETTE.length],
      path: donutPath(start, angle),
      offset: `translate(${(Math.cos(middle) * 7).toFixed(2)}, ${(Math.sin(middle) * 7).toFixed(
        2,
      )})`,
    };
  });

  return (
    <View style={styles.breakdown}>
      <View style={styles.figure}>
        <Svg viewBox="0 0 200 200" width="100%" height="100%">
          {total === 0 ? (
            <Circle cx="100" cy="100" r="70" fill="none" stroke="#EBEAE5" strokeWidth={28} />
          ) : null}
          {slices.map(item => (
            <Path
              key={item.key}
              d={item.path}
              fill={item.color}
              stroke="#FFFFFF"
              strokeWidth={1.4}
              opacity={item.key === active?.key ? 1 : 0.87}
              transform={item.key === active?.key ? item.offset : undefined}
              onPress={() => setSelected(item.key)}
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
        {slices.map(item => {
          const pressed = item.key === active?.key;
          return (
            <Pressable
              key={item.key}
              accessibilityRole="button"
              accessibilityLabel={`${item.label} ${won(item.amount)}`}
              accessibilityState={{ selected: pressed }}
              onPress={() => setSelected(item.key)}
              style={[styles.legendItem, pressed && styles.legendItemActive]}
            >
              <View style={[styles.swatch, { backgroundColor: item.color }]} />
              <Text style={styles.legendLabel}>{item.label}</Text>
              <Text style={styles.legendAmount}>{won(item.amount)}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export function DailySpending({ daily }: { daily: Record<string, number> }) {
  const amounts = reportDailyAmounts(daily);
  const step = dailyAxisStep(amounts);
  const peak = Math.max(0, ...amounts);
  const [selected, setSelected] = useState(amounts.indexOf(peak));
  const active = selected >= 0 && selected < amounts.length ? selected : 0;
  const scale = step * 4;

  return (
    <View style={styles.daily}>
      <Text style={styles.dayCaption}>{`${active + 1}일차 지출 ${won(amounts[active])}`}</Text>
      <View style={styles.dailyRow}>
        <View style={styles.axis}>
          {[4, 3, 2, 1, 0].map(tick => (
            <Text key={tick} style={styles.axisLabel}>
              {won(tick * step)}
            </Text>
          ))}
        </View>
        <View style={styles.bars}>
          {amounts.map((amount, index) => {
            const pressed = active === index;
            const height = scale > 0 ? Math.min(100, (amount / scale) * 100) : 0;
            return (
              <Pressable
                key={String(index)}
                accessibilityRole="button"
                accessibilityLabel={`${index + 1}일차 지출 ${won(amount)}`}
                accessibilityState={{ selected: pressed }}
                onPress={() => setSelected(index)}
                style={styles.barColumn}
              >
                <View style={styles.barTrack}>
                  <View
                    style={[
                      styles.bar,
                      { height: `${height}%` },
                      pressed ? styles.barSelected : null,
                    ]}
                  />
                </View>
                <Text style={styles.dayLabel}>{`${index + 1}일차`}</Text>
              </Pressable>
            );
          })}
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
  const points = REPORT_STAT_ITEMS.map((item, index) => {
    const value = stats[item.key] ?? 0;
    return { ...item, value, ...radarPoint(index, REPORT_STAT_ITEMS.length, value) };
  });
  const outline = points.map(item => `${item.x},${item.y}`).join(' ');
  const baselinePoints = REPORT_STAT_ITEMS.map((_, index) =>
    radarPoint(index, REPORT_STAT_ITEMS.length, 100),
  );
  const baseline = baselinePoints.map(point => `${point.x},${point.y}`).join(' ');
  const summary = points.map(item => `${item.label} ${item.value}%`).join(', ');

  return (
    <View style={styles.radarPanel}>
      <Svg
        accessibilityLabel={summary}
        accessibilityRole="image"
        viewBox="-28 -12 336 276"
        width="100%"
        height={230}
      >
        {[RADAR.radius + 10, RADAR.radius * 0.77, RADAR.radius * 0.42].map(radius => (
          <Circle
            key={radius}
            cx={RADAR.cx}
            cy={RADAR.cy}
            r={radius}
            fill="#FFFFFF"
            stroke="#E7F0EB"
          />
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
        <Polygon
          points={outline}
          fill="#469D89"
          fillOpacity={0.16}
          stroke="#036666"
          strokeWidth={1.4}
          strokeLinejoin="round"
        />
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
            fill={STAT_COLOR[statTone(item.value)]}
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
  breakdown: { gap: 12 },
  figure: { alignSelf: 'center', height: 220, width: 220 },
  figureCaption: {
    alignItems: 'center',
    height: 220,
    justifyContent: 'center',
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  figureLabel: { ...typography.captionSemiBold12, color: '#245C48' },
  figureAmount: {
    color: '#245C48',
    fontFamily: 'Pretendard-Bold',
    fontSize: 16,
    lineHeight: 22,
  },
  figureShare: { ...typography.captionSemiBold12, color: '#245C48' },
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
  legendItemActive: { backgroundColor: '#E8F5EE', borderColor: '#B7DDCA' },
  swatch: { borderRadius: 3, height: 10, width: 10 },
  legendLabel: { ...typography.bodyRegular14, color: colors.gray700, flex: 1 },
  legendAmount: { ...typography.bodyRegular14, color: colors.gray700 },
  daily: { gap: 12 },
  dayCaption: { ...typography.bodySemiBold16, color: colors.gray800 },
  dailyRow: { flexDirection: 'row', gap: 8 },
  axis: { height: 148, justifyContent: 'space-between' },
  axisLabel: { ...typography.captionRegular10, color: colors.gray500, textAlign: 'right' },
  bars: {
    borderBottomColor: '#EDF1EF',
    borderBottomWidth: 1,
    flex: 1,
    flexDirection: 'row',
  },
  barColumn: { flex: 1, minWidth: 0 },
  barTrack: { height: 148, justifyContent: 'flex-end', alignItems: 'center' },
  bar: {
    backgroundColor: '#CDE8DC',
    borderTopLeftRadius: 8,
    borderTopRightRadius: 8,
    maxWidth: 28,
    width: '70%',
  },
  barSelected: { backgroundColor: '#248277' },
  dayLabel: {
    ...typography.captionSemiBold12,
    color: colors.gray500,
    marginTop: 8,
    textAlign: 'center',
  },
  radarPanel: {
    backgroundColor: '#F4FAF7',
    borderRadius: 14,
    paddingBottom: 12,
    paddingTop: 8,
  },
  radarLegend: { flexDirection: 'row', gap: 16, justifyContent: 'center' },
  radarKey: { alignItems: 'center', flexDirection: 'row', gap: 6 },
  radarLine: { borderRadius: 2, height: 2, width: 16 },
  radarLineActual: { backgroundColor: '#14746F' },
  radarLineBase: { backgroundColor: '#88BFA7' },
  radarKeyLabel: { ...typography.captionSemiBold12, color: '#435E54' },
});
