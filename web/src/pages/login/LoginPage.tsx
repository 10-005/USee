import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { TextField } from '@/shared/ui/TextField';
import { Button } from '@/shared/ui/Button';
import logo from '@/shared/assets/figma/logo.png';
import info from '@/shared/assets/figma/account-info.svg';
import '@/features/shelter-demo/pages.css';

export function LoginPage() {
  const navigate = useNavigate();
  // [TODO] 실제 DB와 연결한 뒤 id, password에 할당한 'demo' 기본값 제거할 것
  const [id, setId] = useState('demo');
  const [password, setPassword] = useState('demo');
  const [error, setError] = useState('');

  // [TODO] 실제 DB와 연결할 것
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (id === 'demo' && password === 'demo') {
      sessionStorage.setItem('usee-demo-login', 'true');
      setPassword('');
      navigate('/', { replace: true });
    } else setError('예시 계정 demo / demo로 입력해 주세요. 실제 계정 인증은 연결 전입니다.');
  };

  return (
    <main className="login-page">
      <div className="login-panel">
        <div className="login-content">
          <h1 className="shelter-sr-only">보호소 로그인</h1>
          <img className="login-logo" src={logo} alt="USEE" />
          <p className="login-caption">USEE에 등록된 보호소 담당자 전용 서비스입니다</p>
          <form onSubmit={submit}>
            <TextField
              label="아이디"
              placeholder="ID"
              autoComplete="off"
              value={id}
              onChange={(e) => {
                setId(e.target.value);
                setError('');
              }}
              required
            />
            <TextField
              label="비밀번호"
              placeholder="PASSWORD"
              type="password"
              autoComplete="off"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setError('');
              }}
              required
              error={error}
            />
            <Button type="submit" size="large" fullWidth disabled={!id || !password}>
              로그인
            </Button>
          </form>
          <aside className="login-help">
            <h2>
              <img src={info} alt="" />
              보호소 계정 안내
            </h2>
            <p>
              보호소 계정은 USEE 운영팀에서 별도로 발급합니다.
              <br />
              계정 발급 및 로그인 관련 문의는 운영팀에 연락해 주세요.
            </p>
          </aside>
        </div>
      </div>
    </main>
  );
}
