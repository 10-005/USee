import { login } from '@/shared/api/session';
import { useQueryClient } from '@tanstack/react-query';
import { demoMode } from '@/shared/config/demo';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { TextField } from '@/shared/ui/TextField';
import { Button } from '@/shared/ui/Button';
import logo from '@/shared/assets/figma/logo.png';
import info from '@/shared/assets/icons/account-info.svg';
import '@/shared/styles/work-pages.css';
import './login.css';

export function LoginPage() {
  const navigate = useNavigate();
  const cache = useQueryClient();
  const [pending, setPending] = useState(false);
  const [id, setId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!demoMode) {
      if (pending) return;
      setPending(true);
      setError('');
      try {
        await login(id.trim(), password);
        cache.clear();
        navigate('/', { replace: true });
      } catch (error) {
        setError(error instanceof Error ? error.message : '로그인하지 못했습니다.');
      } finally {
        setPending(false);
      }
      return;
    }
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
            <Button
              type="submit"
              size="large"
              fullWidth
              disabled={pending || !id.trim() || !password}
            >
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
