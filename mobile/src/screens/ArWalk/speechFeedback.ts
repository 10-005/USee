export function getWalkSpeechErrorMessage(reason?: string, retrying = false): string {
  if (retrying) {
    switch (reason) {
      case 'network':
        return '인터넷 연결 확인 · 재연결 중';
      case 'audio':
      case 'busy':
        return '마이크 연결을 다시 확인하고 있어요';
      case 'language':
        return '한국어 음성 인식을 다시 연결하고 있어요';
      case 'rateLimit':
        return '잠시 기다린 뒤 다시 연결하고 있어요';
      default:
        return '음성 인식에 다시 연결하고 있어요';
    }
  }
  switch (reason) {
    case 'network':
      return '인터넷 확인 후 음성을 껐다 켜주세요';
    case 'audio':
      return '마이크 오류 · 음성을 껐다 켜주세요';
    case 'busy':
      return '마이크 사용 중 · 잠시 후 다시 켜주세요';
    case 'language':
      return '한국어 음성 인식 설정을 확인해주세요';
    case 'rateLimit':
      return '요청이 많아요 · 잠시 후 다시 켜주세요';
    case 'service':
      return '음성 서비스 오류 · 음성 껐다 켜기';
    default:
      return '인식 중단 · 음성을 껐다 켜주세요';
  }
}
