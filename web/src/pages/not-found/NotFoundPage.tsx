import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <section className="panel">
      <p className="eyebrow">404</p>
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>주소를 확인하거나 홈으로 이동해 주세요.</p>
      <Link to="/">홈으로 돌아가기</Link>
    </section>
  );
}
