import { useEffect, useState } from 'react';
import { session, setUnauthorizedHandler } from './api';
import AuthScreen from './components/AuthScreen';
import Dashboard from './components/Dashboard';

export default function App() {
  const [token, setToken] = useState<string | null>(() => session.token());
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      session.clear();
      setToken(null);
      setNotice('Your session has expired. Please log in again.');
    });
  }, []);

  if (!token) {
    return (
      <AuthScreen
        notice={notice}
        onLoggedIn={(accessToken) => {
          session.save(accessToken);
          setNotice(null);
          setToken(accessToken);
        }}
      />
    );
  }

  return (
    <Dashboard
      onLogout={() => {
        session.clear();
        setToken(null);
      }}
    />
  );
}
