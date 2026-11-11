import {
  NaverMapMarkerOverlay,
  NaverMapView,
  type NaverMapViewRef,
} from '@mj-studio/react-native-naver-map';
import Geolocation from '@react-native-community/geolocation';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMemo, useRef, useState } from 'react';
import {
  Keyboard,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import type { RootStackParamList } from '../../app/navigation/types';
import LocationIcon from '../../assets/icons/common/location.svg';
import MapLocationIcon from '../../assets/icons/common/map-location.svg';
import SearchIcon from '../../assets/icons/common/search.svg';
import { AppHeader } from '../../components/common';
import { getApiErrorDisplayMessage } from '../../services/api';
import {
  getCandidateAddress,
  searchGeocodeCandidates,
  type GeocodeCandidate,
} from '../../services/geocode/geocode';
import { reverseGeocode } from '../../services/geocode/reverseGeocode';
import { useAdoptionLocationStore } from '../../stores/adoptionLocationStore';
import { useFoundReportDraftStore } from '../../stores/foundReportDraftStore';
import { useLostReportDraftStore } from '../../stores/lostReportDraftStore';
import { colors, radius, typography } from '../../theme';

type Props = NativeStackScreenProps<RootStackParamList, 'LostReportLocationPicker'>;

const DEFAULT_LOCATION = { latitude: 36.1195, longitude: 128.3446 };

export function LostReportLocationPickerScreen({ navigation, route }: Props) {
  const insets = useSafeAreaInsets();
  const isAdoptionLocation = route.params?.target === 'adoption';
  const isFoundReportLocation = route.params?.target === 'found-report';
  const mapRef = useRef<NaverMapViewRef>(null);
  const details = useLostReportDraftStore(state => state.details);
  const updateDetails = useLostReportDraftStore(state => state.updateDetails);
  const selectAdoptionLocation = useAdoptionLocationStore(state => state.selectLocation);
  const foundDraft = useFoundReportDraftStore(state => state.draft);
  const updateFoundDraft = useFoundReportDraftStore(state => state.updateDraft);
  const initialCandidate = useMemo<GeocodeCandidate | null>(() => {
    if (isAdoptionLocation) {
      return null;
    }
    if (isFoundReportLocation) {
      if (foundDraft.latitude === null || foundDraft.longitude === null || !foundDraft.location) {
        return null;
      }
      return {
        jibunAddress: '',
        latitude: foundDraft.latitude,
        longitude: foundDraft.longitude,
        roadAddress: foundDraft.location,
      };
    }
    if (details.latitude === null || details.longitude === null || !details.location) {
      return null;
    }
    return {
      jibunAddress: '',
      latitude: details.latitude,
      longitude: details.longitude,
      roadAddress: details.location,
    };
  }, [
    details.latitude,
    details.location,
    details.longitude,
    foundDraft.latitude,
    foundDraft.location,
    foundDraft.longitude,
    isAdoptionLocation,
    isFoundReportLocation,
  ]);
  const [query, setQuery] = useState('');
  const [candidates, setCandidates] = useState<GeocodeCandidate[] | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState(initialCandidate);
  const [isSearching, setIsSearching] = useState(false);
  const [isFindingLocation, setIsFindingLocation] = useState(false);
  const [isResolvingAddress, setIsResolvingAddress] = useState(false);
  const [locationError, setLocationError] = useState('');

  const initialCoordinate = selectedCandidate ?? DEFAULT_LOCATION;
  const bottomInsetStyle = useMemo(
    () => ({ paddingBottom: Math.max(insets.bottom, 12) + 12 }),
    [insets.bottom],
  );

  const searchAddress = async () => {
    const normalized = query.trim();
    Keyboard.dismiss();
    if (!normalized || normalized.length > 100) {
      setCandidates([]);
      return;
    }
    setIsSearching(true);
    setLocationError('');
    try {
      setCandidates(await searchGeocodeCandidates(normalized));
    } catch (error) {
      setCandidates(null);
      setLocationError(
        getApiErrorDisplayMessage(error, '주소를 검색하지 못했어요. 잠시 후 다시 시도해 주세요.'),
      );
    } finally {
      setIsSearching(false);
    }
  };

  const chooseCandidate = (candidate: GeocodeCandidate) => {
    setSelectedCandidate(candidate);
    setQuery(getCandidateAddress(candidate));
    setCandidates(null);
    Keyboard.dismiss();
    mapRef.current?.animateCameraTo({
      duration: 500,
      latitude: candidate.latitude,
      longitude: candidate.longitude,
      zoom: 16,
    });
  };

  const chooseMapCoordinate = async (latitude: number, longitude: number) => {
    setCandidates(null);
    Keyboard.dismiss();
    setLocationError('');
    setIsResolvingAddress(true);
    try {
      const address = await reverseGeocode(latitude, longitude);
      setSelectedCandidate({ jibunAddress: null, latitude, longitude, roadAddress: address });
    } catch {
      setSelectedCandidate(null);
      setLocationError('선택한 위치의 주소를 확인하지 못했어요. 다시 눌러 주세요.');
    } finally {
      setIsResolvingAddress(false);
    }
  };

  const selectCurrentLocation = async () => {
    if (isFindingLocation) {
      return;
    }
    if (Platform.OS === 'android') {
      const permission = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        {
          buttonNegative: '나중에',
          buttonPositive: '허용',
          message: '지도에서 현재 위치를 확인하기 위해 위치 권한이 필요합니다.',
          title: '현재 위치 사용',
        },
      );
      if (permission !== PermissionsAndroid.RESULTS.GRANTED) {
        return;
      }
    }
    setLocationError('');
    setIsFindingLocation(true);
    mapRef.current?.setLocationTrackingMode('Follow');
    Geolocation.getCurrentPosition(
      async ({ coords }) => {
        await chooseMapCoordinate(coords.latitude, coords.longitude);
        mapRef.current?.animateCameraTo({
          duration: 500,
          latitude: coords.latitude,
          longitude: coords.longitude,
          zoom: 16,
        });
        setIsFindingLocation(false);
      },
      error => {
        setIsFindingLocation(false);
        setLocationError(
          error.code === 3
            ? '현재 위치 확인이 지연되고 있어요. GPS를 켜고 다시 눌러 주세요.'
            : '현재 위치를 확인하지 못했어요. 위치 권한과 GPS를 확인해 주세요.',
        );
      },
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 15000 },
    );
  };

  const selectLocation = () => {
    if (!selectedCandidate) {
      return;
    }
    const address = getCandidateAddress(selectedCandidate);
    if (isAdoptionLocation) {
      selectAdoptionLocation({
        address,
        latitude: selectedCandidate.latitude,
        longitude: selectedCandidate.longitude,
      });
      navigation.goBack();
      return;
    }
    if (isFoundReportLocation) {
      updateFoundDraft({
        latitude: selectedCandidate.latitude,
        location: address,
        longitude: selectedCandidate.longitude,
      });
      navigation.goBack();
      return;
    }
    updateDetails({
      detailAddress: '',
      latitude: selectedCandidate.latitude,
      location: address,
      longitude: selectedCandidate.longitude,
    });
    navigation.goBack();
  };

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <AppHeader
        onBackPress={navigation.goBack}
        title={
          isAdoptionLocation
            ? '양육 예정 장소 선택'
            : isFoundReportLocation
            ? '발견 장소 선택'
            : '실종 장소 선택'
        }
      />
      <View style={[styles.searchSection, candidates !== null && styles.searchSectionWithResults]}>
        <View style={styles.searchField}>
          <SearchIcon height={20} width={20} />
          <TextInput
            maxLength={100}
            onChangeText={value => {
              setQuery(value);
              setSelectedCandidate(null);
              setCandidates(null);
            }}
            onSubmitEditing={searchAddress}
            placeholder="장소명, 도로명, 지번으로 검색"
            placeholderTextColor={colors.gray400}
            returnKeyType="search"
            style={styles.searchInput}
            value={query}
          />
        </View>
        <Pressable
          accessibilityRole="button"
          onPress={searchAddress}
          style={({ pressed }) => [styles.searchButton, pressed && styles.pressed]}
        >
          <Text style={styles.searchButtonLabel}>{isSearching ? '검색 중' : '검색'}</Text>
        </Pressable>
      </View>
      <View style={styles.mapContainer}>
        <NaverMapView
          initialCamera={{ ...initialCoordinate, zoom: 15 }}
          isShowCompass={false}
          isShowIndoorLevelPicker={false}
          isShowLocationButton
          isShowScaleBar={false}
          isShowZoomControls={false}
          onCameraChanged={({ latitude, longitude, reason }) => {
            if (reason !== 'Location') {
              return;
            }
            chooseMapCoordinate(latitude, longitude).catch(() => undefined);
            setIsFindingLocation(false);
          }}
          onTapMap={({ latitude, longitude }) =>
            chooseMapCoordinate(latitude, longitude).catch(() => undefined)
          }
          ref={mapRef}
          style={styles.map}
        >
          {selectedCandidate ? (
            <NaverMapMarkerOverlay
              height={36}
              latitude={selectedCandidate.latitude}
              longitude={selectedCandidate.longitude}
              width={30}
            >
              <MapLocationIcon height={36} width={30} />
            </NaverMapMarkerOverlay>
          ) : null}
        </NaverMapView>
        <Pressable
          accessibilityLabel="현재 위치 선택"
          accessibilityRole="button"
          disabled={isFindingLocation}
          onPress={selectCurrentLocation}
          style={({ pressed }) => [
            styles.currentLocationButton,
            pressed && styles.currentLocationButtonPressed,
          ]}
        />
        {candidates ? (
          <View style={styles.resultsPanel}>
            {candidates.length ? (
              <ScrollView
                decelerationRate="fast"
                keyboardShouldPersistTaps="handled"
                style={styles.resultsScroll}
              >
                {candidates.map(candidate => (
                  <Pressable
                    key={`${candidate.latitude}-${candidate.longitude}`}
                    onPress={() => chooseCandidate(candidate)}
                    style={({ pressed }) => [styles.resultItem, pressed && styles.resultPressed]}
                  >
                    <MapLocationIcon height={20} width={20} />
                    <View style={styles.resultTextWrap}>
                      <Text numberOfLines={1} style={styles.roadAddress}>
                        {candidate.placeName || getCandidateAddress(candidate)}
                      </Text>
                      {candidate.placeName || candidate.jibunAddress ? (
                        <Text numberOfLines={1} style={styles.jibunAddress}>
                          {candidate.placeName ? getCandidateAddress(candidate) : candidate.jibunAddress}
                        </Text>
                      ) : null}
                    </View>
                  </Pressable>
                ))}
              </ScrollView>
            ) : (
              <View style={styles.emptyResult}>
                <Text style={styles.emptyTitle}>검색 결과가 없습니다.</Text>
                <Text style={styles.emptyDescription}>다른 주소 표현으로 다시 검색해 주세요.</Text>
              </View>
            )}
          </View>
        ) : null}
      </View>
      <View style={[styles.bottomPanel, bottomInsetStyle]}>
        <Text style={[styles.guide, locationError && styles.locationError]}>
          {locationError ||
            (isFindingLocation
              ? '현재 위치를 확인하고 있어요.'
              : isResolvingAddress
              ? '선택한 위치의 주소를 확인하고 있어요.'
              : '주소를 검색하거나 지도를 눌러 실종 장소를 선택해 주세요.')}
        </Text>
        <View style={[styles.selectedBox, !selectedCandidate && styles.selectedBoxEmpty]}>
          <LocationIcon height={20} width={20} />
          <Text
            numberOfLines={2}
            style={[styles.selectedText, !selectedCandidate && styles.placeholderText]}
          >
            {selectedCandidate
              ? getCandidateAddress(selectedCandidate)
              : '선택한 주소가 여기에 표시됩니다.'}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ disabled: !selectedCandidate || isResolvingAddress }}
          disabled={!selectedCandidate || isResolvingAddress}
          onPress={selectLocation}
          style={({ pressed }) => [
            styles.selectButton,
            (!selectedCandidate || isResolvingAddress) && styles.selectButtonDisabled,
            pressed && selectedCandidate && !isResolvingAddress && styles.pressed,
          ]}
        >
          <Text style={styles.selectLabel}>이 위치로 선택</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { backgroundColor: colors.white, flex: 1 },
  searchSection: {
    alignItems: 'center',
    backgroundColor: colors.white,
    flexDirection: 'row',
    gap: 8,
    paddingBottom: 12,
    paddingHorizontal: 20,
  },
  searchSectionWithResults: { paddingBottom: 4 },
  searchField: {
    alignItems: 'center',
    borderColor: colors.gray200,
    borderRadius: radius.md,
    borderWidth: 1,
    flex: 1,
    flexDirection: 'row',
    gap: 8,
    height: 46,
    paddingHorizontal: 12,
  },
  searchInput: { ...typography.bodyRegular14, color: colors.gray700, flex: 1, padding: 0 },
  searchButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    height: 46,
    justifyContent: 'center',
    width: 62,
  },
  searchButtonLabel: { ...typography.bodySemiBold16, color: colors.white },
  mapContainer: { flex: 1 },
  map: { flex: 1 },
  currentLocationButton: {
    backgroundColor: 'transparent',
    bottom: 12,
    height: 48,
    position: 'absolute',
    right: 12,
    width: 48,
    zIndex: 2,
  },
  currentLocationButtonPressed: { backgroundColor: 'rgba(0, 0, 0, 0.04)', borderRadius: 24 },
  resultsPanel: {
    backgroundColor: colors.white,
    borderBottomLeftRadius: radius.md,
    borderBottomRightRadius: radius.md,
    elevation: 5,
    left: 20,
    maxHeight: 210,
    position: 'absolute',
    right: 20,
    top: 0,
  },
  resultsScroll: { maxHeight: 210 },
  resultItem: {
    alignItems: 'center',
    borderBottomColor: colors.gray200,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: 10,
    minHeight: 68,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  resultPressed: { backgroundColor: colors.gray10 },
  resultTextWrap: { flex: 1, gap: 3 },
  roadAddress: { ...typography.bodyRegular14, color: colors.gray800 },
  jibunAddress: { ...typography.captionSemiBold12, color: colors.gray400 },
  emptyResult: { alignItems: 'center', gap: 4, paddingHorizontal: 16, paddingVertical: 24 },
  emptyTitle: { ...typography.bodySemiBold16, color: colors.gray700 },
  emptyDescription: { ...typography.bodyRegular14, color: colors.gray400 },
  bottomPanel: { backgroundColor: colors.white, paddingHorizontal: 24, paddingTop: 16 },
  guide: { ...typography.bodyRegular14, color: colors.gray500, marginBottom: 10 },
  locationError: { color: colors.danger },
  selectedBox: {
    alignItems: 'center',
    borderColor: colors.green400,
    borderRadius: radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 8,
    minHeight: 50,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  selectedBoxEmpty: { borderColor: colors.gray200 },
  selectedText: { ...typography.bodyRegular14, color: colors.gray700, flex: 1 },
  placeholderText: { color: colors.gray400 },
  selectButton: {
    alignItems: 'center',
    backgroundColor: colors.green400,
    borderRadius: radius.md,
    height: 48,
    justifyContent: 'center',
    marginTop: 12,
  },
  selectButtonDisabled: { backgroundColor: colors.gray200 },
  selectLabel: { ...typography.bodySemiBold16, color: colors.white },
  pressed: { opacity: 0.72 },
});
