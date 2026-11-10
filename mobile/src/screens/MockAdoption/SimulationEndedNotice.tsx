import { useEffect, useState } from 'react';
import type { ImageSourcePropType } from 'react-native';

import { mockAdoptionPets } from './data';
import { SuddenEventDialog } from './SuddenEventDialog';
import {
  SIMULATION_ENDED_DESCRIPTION,
  SIMULATION_ENDED_TITLE,
  subscribeSimulationEnded,
  type SimulationEndedNotice as Notice,
} from '../../services/simulation/sessionEnd';

function noticeImage(notice: Notice): ImageSourcePropType {
  if (notice.imageUrl) {
    return { uri: notice.imageUrl };
  }
  return (
    mockAdoptionPets.find(pet => pet.id === notice.petId)?.image ??
    require('../../assets/images/mock-adoption/intro-dog.png')
  );
}

export function SimulationEndedNotice() {
  const [notice, setNotice] = useState<Notice | null>(null);
  useEffect(() => subscribeSimulationEnded(setNotice), []);
  if (!notice) {
    return null;
  }
  return (
    <SuddenEventDialog
      event={{
        scenario: {
          title: SIMULATION_ENDED_TITLE,
          description: SIMULATION_ENDED_DESCRIPTION,
        },
      }}
      image={noticeImage(notice)}
      petName={notice.petName}
      onClose={() => setNotice(null)}
    />
  );
}
