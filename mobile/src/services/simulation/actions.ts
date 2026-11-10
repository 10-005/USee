/** 18.6 돌봄 행동 */
export enum SimulationCareAction {
  Feed = 'FEED',
  Water = 'WATER',
  CleanLitter = 'CLEAN_LITTER',
  Bathe = 'BATHE',
  Play = 'PLAY',
  Snack = 'SNACK',
  Medicine = 'MEDICINE',
  VetVisit = 'VET_VISIT',
}

/** 18.16 산책 중 이벤트 해결 행동 */
export enum SimulationWalkAction {
  GiveWater = 'GIVE_WATER',
  GiveSnack = 'GIVE_SNACK',
}

export type SimulationCareRequest =
  | {
      actionCode:
        | SimulationCareAction.Feed
        | SimulationCareAction.CleanLitter
        | SimulationCareAction.Snack
        | SimulationCareAction.Medicine;
      itemSeq: number;
    }
  | {
      actionCode: SimulationCareAction.Play;
      // 생략하면 보유 장난감 중 첫 번째를 사용한다.
      itemSeq?: number;
    }
  | {
      actionCode:
        | SimulationCareAction.Water
        | SimulationCareAction.Bathe
        | SimulationCareAction.VetVisit;
      itemSeq?: never;
    };

// 기구 보유 여부, 소모품 차감, VET_VISIT 허용 여부와 비용은 서버에서 검증한다.
// VET_VISIT은 해당 PENDING 이벤트가 없으면 409 SIMULATION_VET_NOT_NEEDED.
