-- 06. shelter_district_assignments: 시드 보호소 5곳이 자기 시군구의 지정 보호소(Q406)
-- V2 보호소의 district_code가 곧 담당 구역이다(구미 47190·김천 47150·칠곡 47850·상주 47250·성주 47840)
INSERT INTO shelter_district_assignments (district_code, shelter_seq)
SELECT district_code, shelter_seq
FROM shelters
WHERE district_code IN ('47190', '47150', '47850', '47250', '47840')
  AND is_deleted = FALSE;
